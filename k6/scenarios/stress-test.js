import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// Phase 5 — Stress Test (Breaking Point Discovery)
// 목적: 시스템이 어떤 VU 수에서 저하되기 시작하는지 계단식 ramp로 탐색.
// 100 → 200 → 300 VU (각 1분) → 300 VU 유지 2분 → ramp down.
//
// 통과 기준 (완화):
// - p99 < 5s
// - 에러율 < 10%
// 이 기준을 초과하는 지점이 "soft limit".

const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const CUSTOMER_ID = __ENV.CUSTOMER_ID || 14;

const orderCreateDuration = new Trend('order_create_duration', true);
const orderCreateErrors = new Rate('order_create_errors');

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
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
    // 70%: 브라우징 (낮은 부하)
    const r = http.get(`${PRODUCT_API}/api/products?page=0&size=20`);
    check(r, { 'browse 200': (r) => r.status === 200 });
  } else {
    // 30%: 주문 생성 (높은 부하 — Product 호출 2회 + Payment 이벤트 비동기 발행)
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
      headers: { 'Content-Type': 'application/json' },
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
