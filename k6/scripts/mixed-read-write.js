import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

// 혼합 부하: 카탈로그 조회 + 재고 예약 쓰기.
//
// 왜 이 형태여야 하나
//
//   읽기 전용 부하로는 read replica의 효과가 정의상 0 이하다. 읽기 용량은
//   (읽기를 받는 DB의 CPU) / (req당 DB CPU)이고, replica를 켜면 그 일을 통째로
//   더 작은 박스로 옮길 뿐이다. 실제로 A1(replica 3코어)은 primary를 0.02코어로
//   비워놓고 자신이 100% 포화해 p95가 15ms -> 2,281ms가 됐다.
//
//   replica가 값어치를 갖는 건 읽기와 쓰기가 primary를 두고 경합할 때다. 읽기를
//   빼면 primary의 쓰기 용량이 보존된다. 그래서 이 스크립트는 조회를 일정하게
//   깔아두고 예약 쓰기를 램프해 **쓰기 천장**을 잰다. 주 지표는 조회 처리량이
//   아니라 예약 처리량이다.

const API = __ENV.PRODUCT_API || 'http://service-product:8081';
const PRODUCT_POOL = Number(__ENV.PRODUCT_POOL || 2000000);
const VARIANT_POOL = Number(__ENV.VARIANT_POOL || 6000000);
const READ_RATE = Number(__ENV.READ_RATE || 1600);
const WRITE_START = Number(__ENV.WRITE_START || 50);
const WRITE_PEAK = Number(__ENV.WRITE_PEAK || 600);
const WRITE_PATH = __ENV.WRITE_PATH || 'reserve-unit';
// 예약 대상 variant 풀.
//
// 이전 측정은 모든 예약을 variant_id=1 하나에 걸었다. 그건 평시 예약이 아니라
// 단일 인기상품 플래시세일 조건이고, SKIP LOCKED에는 최악이다 — 모든 트랜잭션이
// (variant_id=1, status=AVAILABLE) 같은 인덱스 구간을 같은 지점부터 훑으며 서로
// 잠근 row를 건너뛰어야 한다. 스킵 비용을 최대로 만들어 놓고 "평시에 느리다"고
// 결론내면 조건이 틀린 주장이 된다.
//
// 평시 주문은 카탈로그 전반에 흩어지되 인기 상품에 어느 정도 쏠린다. 여기서는
// RESERVE_POOL개 variant에 걸치고, 상위 RESERVE_HOT 비율이 RESERVE_HOT_SHARE를
// 가져가는 형태로 만든다.
// 예약 대상 variant 범위. 시드(seed-catalog.sql)가 등급을 id로 배정하므로 여기서
// 범위를 고르면 곧 등급을 고르는 것이 된다.
//
//   TIER=normal   id 1101~   단일 row 조건부 UPDATE
//   TIER=popular  id 1001~1100  16샤드
//   TIER=hot      id 1~10       유닛 row + SKIP LOCKED
const TIER = __ENV.TIER || 'normal';
const TIER_RANGE = {
  normal:  { from: 1101, to: 3100 },
  popular: { from: 1001, to: 1100 },
  hot:     { from: 1,    to: 10 },
}[TIER];

const RESERVE_POOL = Number(__ENV.RESERVE_POOL || 2000);
const RESERVE_HOT = Number(__ENV.RESERVE_HOT || 0.05);
const RESERVE_HOT_SHARE = Number(__ENV.RESERVE_HOT_SHARE || 0.50);

function pickReserveVariant() {
  const span = TIER_RANGE.to - TIER_RANGE.from + 1;
  // 등급 안에서도 인기 편중이 있다. 상위 RESERVE_HOT 비율이 RESERVE_HOT_SHARE를 가져간다.
  const hot = Math.max(1, Math.floor(span * RESERVE_HOT));
  const offset = Math.random() < RESERVE_HOT_SHARE
    ? Math.floor(Math.random() * hot)
    : hot + Math.floor(Math.random() * Math.max(1, span - hot));
  return TIER_RANGE.from + Math.min(offset, span - 1);
}
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 20);
const DURATION = __ENV.DURATION || '240s';

const CATEGORIES = ['T-Shirts', 'Hoodies', 'Outerwear', 'Pants', 'Shorts', 'Shirts', 'Coats'];

// 접근 분포는 browse-catalog.js와 동일해야 한다. 전역 균등 랜덤은 캐시뿐 아니라
// DB 버퍼풀에도 최악의 조건이라 실제 서비스를 재는 것이 아니다.
// 상위 0.1%(상품 2,000 / variant 6,000). 1%(상품 2만)로 두면 핫셋이 너무 넓어
// TTL 안에 같은 키가 다시 조회되지 않아 캐시 히트가 나지 않는다.
const HOT_RATIO = Number(__ENV.HOT_RATIO || 0.001);
const HOT_SHARE = Number(__ENV.HOT_SHARE || 0.80);

function pickId(pool) {
  const hotCount = Math.max(1, Math.floor(pool * HOT_RATIO));
  return Math.random() < HOT_SHARE
    ? 1 + Math.floor(Math.random() * hotCount)
    : 1 + hotCount + Math.floor(Math.random() * (pool - hotCount));
}

const readOk = new Counter('browse_2xx');
const readErr = new Counter('browse_err');
const writeOk = new Counter('reserve_2xx');
const write409 = new Counter('reserve_conflict');
const writeErr = new Counter('reserve_5xx');
const readMs = new Trend('browse_ms', true);
const writeMs = new Trend('reserve_ms', true);

export const options = {
  discardResponseBodies: true,
  tags: { replica: __ENV.REPLICA || 'unset', arm: __ENV.ARM || 'unset' },
  scenarios: {
    // 조회는 고정 부하로 깔아 primary(또는 replica)에 일정한 읽기 압력을 만든다.
    browse: {
      executor: 'constant-arrival-rate',
      rate: READ_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Number(__ENV.READ_VUS || 3000),
      maxVUs: Number(__ENV.READ_MAX_VUS || 6000),
      exec: 'browseFn',
    },
    // 쓰기는 램프. 어디서 무너지는지가 이 실험의 답이다.
    reserve: {
      executor: 'ramping-arrival-rate',
      startRate: WRITE_START,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.WRITE_VUS || 500),
      maxVUs: Number(__ENV.WRITE_MAX_VUS || 2000),
      stages: [
        { target: WRITE_START, duration: '30s' },
        { target: WRITE_PEAK, duration: '150s' },
        { target: WRITE_PEAK, duration: '60s' },
      ],
      exec: 'reserveFn',
    },
  },
};

export function browseFn() {
  const roll = Math.random() * 100;
  if (roll < 20) {
    const page = Math.random() < HOT_SHARE
      ? Math.floor(Math.random() * 3)
      : 3 + Math.floor(Math.random() * 47);
    const q = [`page=${page}`, `size=${PAGE_SIZE}`];
    const r2 = Math.random();
    if (r2 < 0.45) q.push(`category=${CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)]}`);
    else if (r2 < 0.75) q.push(`brandId=${1 + Math.floor(Math.random() * 100)}`);
    else {
      const lo = 10000 + Math.floor(Math.random() * 8) * 20000;
      q.push(`minPrice=${lo}`, `maxPrice=${lo + 40000}`);
    }
    const r = http.get(`${API}/api/products?${q.join('&')}`,
      { timeout: '10s', tags: { name: 'GET /api/products' } });
    readMs.add(r.timings.duration);
    if (r.status >= 200 && r.status < 300) readOk.add(1); else readErr.add(1);

  } else if (roll < 50) {
    const id = pickId(PRODUCT_POOL);
    const r = http.get(`${API}/api/products/${id}`,
      { timeout: '10s', tags: { name: 'GET /api/products/:id' } });
    readMs.add(r.timings.duration);
    if (r.status >= 200 && r.status < 300) readOk.add(1); else readErr.add(1);

  } else {
    const id = pickId(VARIANT_POOL);
    const r = http.get(`${API}/api/internal/products/variants/${id}`,
      { timeout: '10s', tags: { name: 'GET /api/internal/products/variants/:id' } });
    readMs.add(r.timings.duration);
    if (r.status >= 200 && r.status < 300) readOk.add(1); else readErr.add(1);
  }
}

export function reserveFn() {
  // orderId를 VU/반복으로 유일하게 만들어 중복 예약 가드에 걸리지 않게 한다.
  const orderId = __VU * 100000000 + __ITER;
  const variantId = pickReserveVariant();
  const res = http.post(
    `${API}/api/internal/products/variants/${variantId}/${WRITE_PATH}`,
    JSON.stringify({ orderId, quantity: 1 }),
    // 조회와 같은 10s. 이전엔 예약만 30s여서 p95 2.7초로 사실상 무너진
// 지점이 '실패 0%'로 집계됐다.
    { headers: { 'Content-Type': 'application/json' }, timeout: '10s',
      tags: { name: `POST /variants/:id/${WRITE_PATH}` } },
  );
  writeMs.add(res.timings.duration);
  if (res.status >= 200 && res.status < 300) writeOk.add(1);
  else if (res.status === 409) write409.add(1);
  else if (res.status >= 500) writeErr.add(1);
}
