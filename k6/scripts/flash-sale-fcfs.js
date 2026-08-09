import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ---------------------------------------------------------------------------
// 선착순(FCFS) 예약 — DB SKIP LOCKED 큐 + bounded-wait 부하 시나리오.
//
// 검증 설계: blog-draft/flash-sale-booking-system/ (DB를 권위로, 예약은 SKIP LOCKED
// 큐로, 읽기는 read replica, Redis는 예약 경로에서 제외). 계획서는 같은 폴더의
// load-test-plan.md.
//
// 이 스크립트가 치는 계약(코드와 일치해야 함):
//   POST {PRODUCT_API}/api/internal/products/variants/{id}/reserve-queue
//        body {orderId, quantity}
//        -> 202 PENDING(큐 적재) / 429 admission-shed / 4xx 매진(SOLD_OUT)
//   GET  {PRODUCT_API}/api/internal/products/variants/{id}/reserve-result?orderId=..
//        -> {status: PENDING|GRANTED|REJECTED}
//   => bounded-wait: 적재 후 결과를 짧게 폴링해 성공/매진/타임아웃 한 응답으로 수렴.
//
// MODE 하나로 다섯 기법을 전환한다(load-test-plan.md의 FS1~FS5):
//   rampup | spike | soak | stress | mixed
//
// 측정 규율(docs/loadtest/README.md 상속): open model, throttle knee 위, 계측 주체
// 라벨링, dropped_iterations 확인, oversell 프로브 vacuous-pass 방지.
// ---------------------------------------------------------------------------

const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
const PROBE_BASE = __ENV.PROBE_URL || `${PRODUCT_API}/api/internal/products/variants`;
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const MODE = (__ENV.MODE || 'spike').toLowerCase();
const QUANTITY = Number(__ENV.QUANTITY || 1);

// bounded-wait: 적재 뒤 결과를 최대 WAIT_MAX_MS 동안 WAIT_POLL_MS 간격으로 폴링.
// 넘어가면 "주문 실패(timeout)"로 분류 — 대기표를 노출하지 않는 UX 그대로.
const WAIT_MAX_MS = Number(__ENV.WAIT_MAX_MS || 3000);
const WAIT_POLL_MS = Number(__ENV.WAIT_POLL_MS || 150);

// 프로파일(open model). 절대 rps로 대용량을 주장하지 않고 knee/격리/불변식을 본다.
const PROFILES = {
  // FS1: knee 탐색 — 어디서 무엇이 먼저 무너지나(큐 INSERT CPU / 드레인 배치 / 커넥션).
  rampup: [
    { duration: '30s', target: 20 },
    { duration: '30s', target: 100 },
    { duration: '30s', target: 250 },
    { duration: '30s', target: 500 },
    { duration: '30s', target: 750 },
    { duration: '30s', target: 1000 },
    { duration: '20s', target: 0 },
  ],
  // FS2: 세일 오픈 재현 — 계단식 급증. bounded-wait 분포·admission shed·오버셀 0.
  spike: [
    { duration: '30s', target: 50 }, // 평시 baseline (warm-up 포함)
    { duration: '5s', target: 1000 }, // 계단 급증
    { duration: '60s', target: 1000 }, // 스파이크 지속
    { duration: '20s', target: 50 },
    { duration: '15s', target: 0 },
  ],
  // FS3: soak — 큐 누적/정리·replica lag·backlog 수렴·메모리 누수.
  soak: [
    { duration: '1m', target: 300 },
    { duration: '30m', target: 300 },
    { duration: '30s', target: 0 },
  ],
  // FS4: stress — 붕괴 지점과 형태(429로 흘리나, 5xx/hang인가).
  stress: [
    { duration: '30s', target: 100 },
    { duration: '1m', target: 500 },
    { duration: '1m', target: 1000 },
    { duration: '1m', target: 1500 },
    { duration: '1m', target: 2000 },
    { duration: '20s', target: 0 },
  ],
  // FS5: mixed — 선착순 스파이크와 동시에 일반 카탈로그 조회. 격리(blast radius).
  mixed: [
    { duration: '30s', target: 50 },
    { duration: '5s', target: 800 },
    { duration: '60s', target: 800 },
    { duration: '20s', target: 0 },
  ],
};

const stages = __ENV.STAGES ? JSON.parse(__ENV.STAGES) : PROFILES[MODE] || PROFILES.spike;

// 결과 분류 카운터. http_reqs는 실패 포함이라 성공 throughput으로 읽으면 안 된다.
const admittedGranted = new Counter('fcfs_granted'); // 선착순 당첨(RESERVED 확정)
const soldOut = new Counter('fcfs_sold_out'); // 매진(REJECTED) — 정상적인 선착순 탈락
const rejected429 = new Counter('fcfs_rejected_429'); // admission이 엣지에서 흘림
const boundedWaitTimeout = new Counter('fcfs_wait_timeout'); // 제한시간 내 미확정("주문 실패")
const enqueueFail5xx = new Counter('fcfs_enqueue_5xx'); // 적재 자체 서버오류 — 낮아야 함
const clientTimeouts = new Counter('fcfs_client_timeout'); // status 0
const timeToDecision = new Trend('fcfs_time_to_decision_ms', true); // 적재→확정까지(bounded-wait 체감)
const catalogRead = new Counter('catalog_read_2xx'); // mixed: 일반 조회 성공
const catalogReadBad = new Counter('catalog_read_bad'); // mixed: 일반 조회 실패(격리 깨짐 신호)

// 오버셀 조기 감지 프로브(실행 후 DB row-count로 최종 확정).
const oversellProbeObserved = new Counter('oversell_probe_observed');
const oversellProbeNegative = new Counter('oversell_probe_negative');
const stockObserved = new Trend('stock_observed', false);

const scenarios = {
  reserve: {
    executor: 'ramping-arrival-rate',
    startRate: 5,
    timeUnit: '1s',
    preAllocatedVUs: Number(__ENV.PRE_VUS || 800),
    maxVUs: Number(__ENV.MAX_VUS || 4000),
    stages,
    gracefulStop: '20s',
    exec: 'reserveFcfs',
  },
  oversell_probe: {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.PROBE_RATE || 5),
    timeUnit: '1s',
    duration: __ENV.PROBE_DURATION || totalDuration(stages),
    preAllocatedVUs: 5,
    maxVUs: 20,
    exec: 'probeStock',
  },
};

// mixed 모드에서만 일반 카탈로그 조회를 동시에 흘려 격리를 검증한다(read replica 대상).
if (MODE === 'mixed') {
  scenarios.catalog = {
    executor: 'constant-arrival-rate',
    rate: Number(__ENV.CATALOG_RATE || 200),
    timeUnit: '1s',
    duration: totalDuration(stages),
    preAllocatedVUs: 200,
    maxVUs: 1000,
    exec: 'browseCatalog',
  };
}

export const options = {
  tags: { testid: __ENV.TESTID || `flash-sale-fcfs-${MODE}` },
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios,
  thresholds: {
    // 불변식: 재고 음수를 한 번이라도 보면 FAIL.
    oversell_probe_negative: ['count<1'],
    // vacuous-pass 방지: 프로브가 실제 재고를 최소 한 번은 읽었어야 위 게이트가 의미를 가진다.
    oversell_probe_observed: ['count>0'],
    // degradation: 적재 5xx는 극소여야 한다(초과분은 429/매진으로 흘려야지 5xx면 실패).
    fcfs_enqueue_5xx: ['count<1'],
    http_req_failed: ['rate<1'],
  },
};

export function reserveFcfs() {
  const orderId = uniqueOrderId();
  const body = JSON.stringify({ orderId, quantity: QUANTITY });
  const started = Date.now();

  const res = http.post(`${PROBE_BASE}/${VARIANT_ID}/reserve-queue`, body, {
    headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER },
    timeout: '10s',
  });

  if (res.status === 429) {
    rejected429.add(1);
    return;
  }
  if (res.status === 0) {
    clientTimeouts.add(1);
    return;
  }
  if (res.status >= 500) {
    enqueueFail5xx.add(1);
    return;
  }
  // 매진을 적재 시점(admission)에서 바로 거절할 수도 있다(4xx SOLD_OUT).
  if (res.status === 409) {
    soldOut.add(1);
    return;
  }
  if (res.status < 200 || res.status >= 300) {
    // 그 외 4xx(검증 오류 등)
    return;
  }

  // 202 PENDING: bounded-wait로 결과 폴링.
  const deadline = started + WAIT_MAX_MS;
  let decided = false;
  while (Date.now() < deadline) {
    const poll = http.get(
      `${PROBE_BASE}/${VARIANT_ID}/reserve-result?orderId=${orderId}`,
      { headers: { Authorization: AUTH_HEADER }, timeout: '5s' },
    );
    if (poll.status === 200) {
      let status = '';
      try {
        status = poll.json('data.status');
      } catch (e) {
        status = '';
      }
      if (status === 'GRANTED') {
        admittedGranted.add(1);
        timeToDecision.add(Date.now() - started);
        decided = true;
        break;
      }
      if (status === 'REJECTED') {
        soldOut.add(1);
        timeToDecision.add(Date.now() - started);
        decided = true;
        break;
      }
    }
    sleepMs(WAIT_POLL_MS);
  }
  if (!decided) boundedWaitTimeout.add(1); // 제한시간 내 미확정 = "주문 실패"(재시도 유도)
  check(res, { 'enqueue accepted (2xx/4xx, no 5xx)': (r) => r.status < 500 });
}

export function browseCatalog() {
  // read replica로 가야 하는 일반 조회. 선착순 버스트 중에도 안정적이어야 격리 성공.
  const res = http.get(`${PROBE_BASE}/${VARIANT_ID}`, {
    headers: { Authorization: AUTH_HEADER },
    timeout: '5s',
  });
  if (res.status === 200) catalogRead.add(1);
  else catalogReadBad.add(1);
  check(res, { 'catalog read ok during spike': (r) => r.status === 200 });
}

export function probeStock() {
  const res = http.get(`${PROBE_BASE}/${VARIANT_ID}`, {
    headers: { Authorization: AUTH_HEADER },
    timeout: '5s',
  });
  if (res.status === 200) {
    let stock = NaN;
    try {
      stock = res.json('data.stockQuantity');
    } catch (e) {
      stock = NaN;
    }
    if (typeof stock === 'number' && !Number.isNaN(stock)) {
      oversellProbeObserved.add(1);
      stockObserved.add(stock);
      if (stock < 0) oversellProbeNegative.add(1);
    }
  }
}

// k6에는 동기 sleep(ms)이 없어 busy-wait 없이 대기하려면 k6/execution의 sleep을 쓴다.
// 여기서는 짧은 폴링 간격이라 k6 sleep(초 단위)로 변환한다.

function sleepMs(ms) {
  sleep(ms / 1000);
}

// 매 반복마다 전역 고유 orderId. (__VU, __ITER)는 실행 전체에서 유일하므로 충돌이 없다.
function uniqueOrderId() {
  const vu = typeof __VU === 'number' ? __VU : 0;
  const iter = typeof __ITER === 'number' ? __ITER : 0;
  return vu * 10000000 + iter;
}

function totalDuration(st) {
  const secs = st.reduce((acc, s) => acc + parseDuration(s.duration), 0) + 25;
  return `${secs}s`;
}
function parseDuration(d) {
  const m = /^(\d+)m$/.exec(d);
  if (m) return Number(m[1]) * 60;
  const s = /^(\d+)s$/.exec(d);
  if (s) return Number(s[1]);
  const ms = /^(\d+)m(\d+)s$/.exec(d);
  if (ms) return Number(ms[1]) * 60 + Number(ms[2]);
  return 60;
}
