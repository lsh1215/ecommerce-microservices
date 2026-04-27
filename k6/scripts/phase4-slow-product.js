import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// Phase 4 — Slow Product Service 시나리오
// Product 서비스가 인위적 지연(2s)을 가진 상태에서 Order 서비스의 반응을 측정.
// Before (Circuit Breaker 없음): 스레드 풀이 고갈되어 Order 전체 응답 시간이 2s 이상으로 악화.
// After (Circuit Breaker 있음): 초반 실패 이후 CB가 OPEN되어 fast-fail, 주문 경로는 빠르게 503.
//                              재고 무관 경로인 GET /api/orders는 영향 없이 빠른 응답 유지.

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const CUSTOMER_ID = __ENV.CUSTOMER_ID || 14;
const VARIANT_ID = __ENV.VARIANT_ID || 1;
// JWT trust on main: ingress requires Authorization. Demo unsigned token
// with sub matching CUSTOMER_ID. customerId comes from header on main; the
// body's customerId is ignored.
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxNCJ9.sig'}`;

// 주문 생성 경로와 주문 조회 경로의 지표를 분리 측정 (thread pool saturation 영향 파악)
const orderCreateDuration = new Trend('order_create_duration', true);
const orderQueryDuration = new Trend('order_query_duration', true);
const orderCreateErrors = new Rate('order_create_errors');

export const options = {
  scenarios: {
    order_creation: {
      executor: 'constant-vus',
      vus: 30,
      duration: '30s',
      exec: 'createOrder',
    },
    order_query: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
      exec: 'queryOrders',
    },
  },
  thresholds: {
    // AFTER (CB) 기준:
    // - order_query_duration p95는 반드시 <1s (CB 덕에 스레드 풀 영향 없음)
    // - order_create_duration p95는 <3s (fast-fail)
    // BEFORE에서는 이 threshold들이 실패 → p99 >> 3s 가 증거가 된다.
    order_query_duration: ['p(95)<1000'],
    order_create_duration: ['p(95)<3000'],
  },
};

export function createOrder() {
  const payload = JSON.stringify({
    customerId: CUSTOMER_ID,
    items: [
      {
        productVariantId: VARIANT_ID,
        productId: 1,
        productName: 'Essential Cotton Crew Tee',
        size: 'S',
        color: 'Black',
        unitPrice: 29900,
        quantity: 1,
      },
    ],
    shippingAddress: {
      recipientName: 'Phase4 Test',
      phone: '010-0000-0000',
      zipCode: '06234',
      address1: 'Seoul Gangnam Teheran-ro 123',
      address2: 'Test Building 501',
    },
  });

  const res = http.post(`${ORDER_API}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER },
    timeout: '15s',
  });

  orderCreateDuration.add(res.timings.duration);
  orderCreateErrors.add(res.status !== 201);

  check(res, {
    'order create: 201 or 503 fast-fail': (r) => r.status === 201 || r.status === 503,
    'order create: not 5xx other': (r) => r.status !== 500 && r.status !== 502 && r.status !== 504,
  });

  sleep(0.1);
}

// GET /api/orders는 Phase 1에서 발견된 LazyInitializationException 버그로 500 반환.
// 본 테스트의 목적은 "Product가 느려도 Product를 안 거치는 경로는 영향 없는가" 검증이므로
// 독립적인 헬스체크 경로(/actuator/health)로 query latency를 측정.
export function queryOrders() {
  const res = http.get(`${ORDER_API}/actuator/health`, {
    timeout: '5s',
  });

  orderQueryDuration.add(res.timings.duration);

  check(res, {
    'health query: 200': (r) => r.status === 200,
    'health query: fast (<500ms)': (r) => r.timings.duration < 500,
  });

  sleep(0.5);
}
