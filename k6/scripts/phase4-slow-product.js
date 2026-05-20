import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// Product가 느릴 때 Order 서비스의 반응을 측정한다.
//
// circuit-breaker 적용 전후를 비교할 때 사용한다. 핵심 신호는 isolation이다.
// Product를 호출하는 주문 생성은 fast-fail할 수 있지만, Product를 호출하지 않는 Order 경로는
// 계속 빠르게 응답해야 한다.

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const CUSTOMER_ID = __ENV.CUSTOMER_ID || 14;
const VARIANT_ID = __ENV.VARIANT_ID || 1;
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxNCJ9.sig'}`;

// dependency saturation이 aggregate latency에 묻히지 않도록 endpoint별 metric을 분리한다.
const orderCreateDuration = new Trend('order_create_duration', true);
const orderQueryDuration = new Trend('order_query_duration', true);
const orderCreateErrors = new Rate('order_create_errors');

export const options = {
  scenarios: {
    // Product 의존 경로다. dependency failure mode가 드러나는 쪽이다.
    order_creation: {
      executor: 'constant-vus',
      vus: 30,
      duration: '30s',
      exec: 'createOrder',
    },
    // Product 비의존 control path다. isolation이 동작하면 빠르게 유지되어야 한다.
    order_query: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
      exec: 'queryOrders',
    },
  },
  thresholds: {
    // protected/fail-fast 동작 기준의 threshold다. resilience control 적용 전에는 실패할 수 있다.
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

export function queryOrders() {
  // health를 Product 비의존 control path로 사용해 Product latency와 Order 자체 응답성을 분리한다.
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
