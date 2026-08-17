import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

// 동기 예약 경로의 핫 로우 경합 측정.
//
// 왜 단일 옵션에 몰아넣나: 이 실험이 재려는 것이 "같은 재고를 여러 요청이 동시에 깎을 때
// 어디서 막히는가"이기 때문이다. 옵션을 흩뿌리면 경합이 사라져 두 등급의 차이도 사라진다.
// 평시 주문은 실제로 흩어지고, 그래서 평시에는 이 차이가 나타나지 않는다 — 이 부하는
// "단일 인기 상품에 주문이 몰리는 순간"을 재현한 것이다.
//
// 두 arm은 같은 엔드포인트를 탄다. 다른 것은 variant의 stock_contention 등급뿐이다.
//
//   VARIANT=1  NORMAL  product_variant.stock_quantity 조건부 UPDATE
//   VARIANT=2  HOT     stock_unit 유닛 row + SELECT ... FOR UPDATE SKIP LOCKED

const API = __ENV.PRODUCT_API || 'http://service-product:8081';
const VARIANT = __ENV.VARIANT || '1';
const RATE = Number(__ENV.RATE || 300);
const DURATION = __ENV.DURATION || '180s';

const ok = new Counter('reserve_2xx');
const insufficient = new Counter('reserve_insufficient');
const failed = new Counter('reserve_5xx');
const ms = new Trend('reserve_ms', true);

export const options = {
  discardResponseBodies: true,
  tags: { arm: __ENV.ARM || 'unset', tier: __ENV.TIER || 'unset' },
  scenarios: {
    reserve: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Number(__ENV.PRE_VUS || 1000),
      maxVUs: Number(__ENV.MAX_VUS || 6000),
      exec: 'go',
    },
  },
};

export function go() {
  // orderId는 요청마다 유일해야 한다. 같은 값이 재사용되면 중복 예약 가드에 걸려
  // 재고를 깎지 않고 통과하므로, 경합이 아니라 가드 성능을 재게 된다.
  const orderId = __VU * 100000000 + __ITER;
  const res = http.post(
    `${API}/api/internal/products/variants/${VARIANT}/reserve-stock`,
    JSON.stringify({ orderId, quantity: 1 }),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: '10s',
      tags: { name: 'POST /variants/:id/reserve-stock' },
    },
  );
  ms.add(res.timings.duration);
  if (res.status >= 200 && res.status < 300) ok.add(1);
  else if (res.status === 409 || res.status === 400) insufficient.add(1);
  else failed.add(1);
}
