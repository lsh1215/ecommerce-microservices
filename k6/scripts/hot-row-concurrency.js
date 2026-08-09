import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 단일 재고 hot row에 대한 **닫힌 모델(closed model)** 경합 시나리오.
//
// 무엇을 위한 것인가:
// - 열린 모델(hot-row-rampup.js)은 **도착률**을 통제한다. 이 스크립트는 **동시 writer 수**를 통제한다.
//   같은 row 를 동시에 쓰는 트랜잭션이 N개일 때 어떻게 되는지 보고 싶을 때 쓴다.
//   VU 수 = 동시 writer 수이므로 경합 강도를 직접 지정할 수 있다.
//
// ⚠ 이 스크립트는 아직 한 번도 실행되지 않았다. 작성 당시의 근거는 틀렸다:
//   *"열린 모델로는 동시성이 1 미만이라 락 경합이 안 생긴다"* 고 판단했으나,
//   그 계산은 **빠른 arm(Atomic)** 의 서비스 시간으로만 한 것이었다. 느린 arm 에서는
//   응답이 늦어지면서 동시 요청이 쌓이고, 그 동시성이 다시 경합을 키운다.
//   실제로 R6·R7 의 비관적 락 arm 은 열린 모델에서 p95 10.9s·15.8s 로 무너졌다
//   (3회 중 2회). 즉 **열린 모델로 충분히 재현된다.** runs/2026-08-01-r7/VERDICT.md 참고.
//
// 그래도 이 스크립트가 쓸모 있는 경우: "동시 writer 30일 때" 처럼 경합 강도를 고정해
// 비교하고 싶을 때. 포트폴리오의 InnoDB 근거(lock-hold.png)가 30 워커 기준이라 대조에 맞다.

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const CUSTOMER_ID = Number(__ENV.CUSTOMER_ID || 1);

// 동시 writer 수를 단계적으로 올린다. 각 단계에서 처리량이 어떻게 꺾이는지가 관측 대상이다.
const STAGES = __ENV.STAGES
  ? JSON.parse(__ENV.STAGES)
  : [
      { duration: '1m', target: 1 },
      { duration: '2m', target: 5 },
      { duration: '2m', target: 10 },
      { duration: '2m', target: 20 },
      { duration: '2m', target: 30 },
      { duration: '2m', target: 50 },
    ];

const orders2xx = new Counter('orders_created_2xx');
const orders5xx = new Counter('orders_failed_5xx');
const ordersTimeout = new Counter('orders_timeout');
// 서버가 실제로 응답한 시간만 따로 본다(연결 수립 시간 제외).
const serveTime = new Trend('order_serve_ms', true);

export const options = {
  tags: { testid: __ENV.TESTID || 'hotrow-concurrency' },
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    hot_row_concurrency: {
      // 닫힌 모델: VU 수가 곧 동시 writer 수다.
      executor: 'ramping-vus',
      startVUs: 1,
      stages: STAGES,
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    // 관측용이다. 첫 실패에서 멈추지 않고 곡선 전체를 남긴다.
    http_req_duration: ['p(95)<30000'],
    http_req_failed: ['rate<1'],
  },
};

export default function () {
  const payload = JSON.stringify({
    customerId: CUSTOMER_ID,
    items: [
      {
        productVariantId: VARIANT_ID,
        productId: 1,
        productName: 'HotRow Concurrency',
        size: 'M',
        color: 'Black',
        unitPrice: 29900,
        quantity: 1,
      },
    ],
    shippingAddress: {
      recipientName: 'Test',
      phone: '010-0000-0000',
      zipCode: '06234',
      address1: 'Seoul',
      address2: 'Test',
    },
  });

  const res = http.post(`${ORDER_API}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER, 'X-Customer-Id': String(CUSTOMER_ID) },
    timeout: '30s',
  });

  serveTime.add(res.timings.waiting);

  if (res.status >= 200 && res.status < 300) orders2xx.add(1);
  else if (res.status >= 500) orders5xx.add(1);
  else if (res.status === 0) ordersTimeout.add(1);

  check(res, {
    'status 2xx': (r) => r.status >= 200 && r.status < 300,
    'not client-timeout': (r) => r.status !== 0,
  });
}
