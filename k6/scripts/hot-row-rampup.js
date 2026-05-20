import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 단일 재고 hot row를 대상으로 한 breakpoint 시나리오.
//
// 목적:
// - 같은 hot-key 부하에서 재고 차감 구현별 차이를 비교한다.
// - 실패, timeout, queueing이 시작되는 offered request rate를 찾는다.
//
// 테스트 모델:
// - productVariantId는 의도적으로 고정한다. variant를 분산하면 row-lock contention이 가려진다.
// - VU 기반 부하 대신 ramping-arrival-rate를 사용한다. 응답이 느려져도 offered load가 계속 증가해야
//   시스템이 무너지는 지점을 볼 수 있기 때문이다.
//
// 결과 해석:
// - http_reqs에는 실패 응답도 포함된다. 성공 throughput으로 읽으면 안 된다.
// - capacity나 degradation을 판단할 때는 orders_created_2xx/s, timeout count,
//   DB commit delta, Hikari pending을 우선해서 본다.

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);

// 더 정확한 knee를 찾을 때는 CI나 로컬 실험에서 STAGES를 덮어쓴다.
const STAGES = __ENV.STAGES
  ? JSON.parse(__ENV.STAGES)
  : [
      // baseline과 warm-up 구간.
      { duration: '1m', target: 5 },
      // 안정 구간으로 예상되는 범위를 천천히 올린다.
      { duration: '2m', target: 20 },
      { duration: '2m', target: 45 },
      // 예상 knee를 넘겨 degradation 모양까지 기록한다.
      { duration: '2m', target: 80 },
      { duration: '2m', target: 130 },
      { duration: '1m', target: 180 },
    ];

// 성공한 주문 생성과 전체 HTTP request rate를 분리해서 본다.
const orders2xx = new Counter('orders_created_2xx');
const orders5xx = new Counter('orders_failed_5xx');
const ordersTimeout = new Counter('orders_timeout');

export const options = {
  tags: { testid: __ENV.TESTID || 'hotrow-rampup' },
  scenarios: {
    hot_row_breakpoint: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 300),
      maxVUs: Number(__ENV.MAX_VUS || 2000),
      stages: STAGES,
      gracefulStop: '15s',
    },
  },
  thresholds: {
    // 관측용 threshold다. 첫 실패에서 멈추지 않고 degradation curve 전체를 남기는 것이 목적이다.
    http_req_duration: ['p(95)<30000'],
    http_req_failed: ['rate<1'],
  },
};

export default function () {
  const payload = JSON.stringify({
    customerId: 1,
    items: [
      {
        productVariantId: VARIANT_ID,
        productId: 1,
        productName: 'HotRow Rampup',
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
    headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER },
    // 서버 connection timeout 구간과 맞춰 pool exhaustion과 client timeout을 분리해서 본다.
    timeout: '30s',
  });

  if (res.status >= 200 && res.status < 300) orders2xx.add(1);
  else if (res.status >= 500) orders5xx.add(1);
  else if (res.status === 0) ordersTimeout.add(1);

  check(res, {
    'status 2xx': (r) => r.status >= 200 && r.status < 300,
    'not client-timeout': (r) => r.status !== 0,
  });
}
