import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// 처음 눈에 보이는 degradation 지점을 찾기 위한 VU 기반 stress 시나리오.
//
// 정밀한 throughput capacity 테스트가 아니라 거친 pressure test다.
// regression 탐지와 soft limit 파악에는 유용하지만, 정확한 capacity 비교에는
// success-rate counter가 명시된 open-model 시나리오가 더 적합하다.

const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const CUSTOMER_ID = __ENV.CUSTOMER_ID || 14;
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxNCJ9.sig'}`;

const orderCreateDuration = new Trend('order_create_duration', true);
const orderCreateErrors = new Rate('order_create_errors');

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        // degradation 지점을 찾기 쉽도록 concurrency를 계단식으로 올린다.
        { duration: '1m', target: 100 },
        { duration: '1m', target: 200 },
        { duration: '1m', target: 300 },
        { duration: '2m', target: 300 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<5000'],
    http_req_failed: ['rate<0.1'],
    order_create_errors: ['rate<0.1'],
  },
};

export default function () {
  const rand = Math.random();

  if (rand < 0.7) {
    // 일반 browsing처럼 catalog traffic을 dominant하게 유지하는 read-heavy branch다.
    const r = http.get(`${PRODUCT_API}/api/products?page=0&size=20`);
    check(r, { 'browse 200': (r) => r.status === 200 });
  } else {
    // 높은 concurrency에서 동기 주문 흐름을 검증하는 write branch다.
    const variantId = Math.floor(Math.random() * 3) + 1;
    const orderPayload = JSON.stringify({
      customerId: CUSTOMER_ID,
      items: [
        {
          productVariantId: variantId,
          productId: 1,
          productName: 'Stress Test',
          size: 'S',
          color: 'Black',
          unitPrice: 29900,
          quantity: 1,
        },
      ],
      shippingAddress: {
        recipientName: 'Stress',
        phone: '010-0000-0000',
        zipCode: '06234',
        address1: 'Seoul',
        address2: 'Apt',
      },
    });

    const orderRes = http.post(`${ORDER_API}/api/orders`, orderPayload, {
      headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER },
      timeout: '10s',
    });
    orderCreateDuration.add(orderRes.timings.duration);
    orderCreateErrors.add(orderRes.status !== 201);
    check(orderRes, {
      'order create 201 or 5xx acceptable': (r) => r.status === 201 || r.status >= 500,
    });
  }

  sleep(0.3);
}
