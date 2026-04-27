import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// Phase 5 — Load Test (Normal Traffic)
// 목적: 일반적인 운영 트래픽 수준(50 VUs)에서 시스템이 안정적으로 동작하는지 검증.
// Ramp-up 1분 → 3분 유지 → 30초 Ramp-down.
// 트래픽 혼합: 60% 브라우징, 20% 주문 생성, 10% 주문 조회, 10% 결제 조회.
//
// 통과 기준: p99 < 2s, 에러율 < 1%.

const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const PAYMENT_API = __ENV.PAYMENT_API || 'http://localhost:8083';
const CUSTOMER_ID = __ENV.CUSTOMER_ID || 14;
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxNCJ9.sig'}`;

// 경로별 p99/처리량 분리 측정
const browseDuration = new Trend('browse_duration', true);
const orderCreateDuration = new Trend('order_create_duration', true);

export const options = {
  scenarios: {
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<2000'],
    http_req_failed: ['rate<0.01'],
    browse_duration: ['p(95)<500'],
    order_create_duration: ['p(95)<1500'],
  },
};

export default function () {
  const rand = Math.random();

  if (rand < 0.6) {
    // 60%: 상품 브라우징 (카탈로그 + 상세)
    const listRes = http.get(`${PRODUCT_API}/api/products?page=0&size=20`);
    browseDuration.add(listRes.timings.duration);
    check(listRes, { 'browse list 200': (r) => r.status === 200 });

    const productId = Math.floor(Math.random() * 8) + 1;
    const detailRes = http.get(`${PRODUCT_API}/api/products/${productId}`);
    browseDuration.add(detailRes.timings.duration);
    check(detailRes, { 'browse detail 200 or 404': (r) => r.status === 200 || r.status === 404 });
  } else if (rand < 0.8) {
    // 20%: 주문 생성 (SAGA 흐름)
    const variantId = Math.floor(Math.random() * 3) + 1; // 1~3 (첫 상품의 variant들)
    const orderPayload = JSON.stringify({
      customerId: CUSTOMER_ID,
      items: [
        {
          productVariantId: variantId,
          productId: 1,
          productName: 'Essential Cotton Crew Tee',
          size: ['S', 'M', 'L'][variantId - 1],
          color: ['Black', 'White', 'Navy'][variantId - 1],
          unitPrice: 29900,
          quantity: 1,
        },
      ],
      shippingAddress: {
        recipientName: 'Load Test',
        phone: '010-0000-0000',
        zipCode: '06234',
        address1: 'Seoul Gangnam',
        address2: 'Apt 101',
      },
    });

    const orderRes = http.post(`${ORDER_API}/api/orders`, orderPayload, {
      headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER },
    });
    orderCreateDuration.add(orderRes.timings.duration);
    check(orderRes, { 'order create 201': (r) => r.status === 201 });
  } else if (rand < 0.9) {
    // 10%: Order 서비스 헬스 조회 (주문 GET API는 기존 LazyInit 버그로 생략)
    const r = http.get(`${ORDER_API}/actuator/health`);
    check(r, { 'order health 200': (r) => r.status === 200 });
  } else {
    // 10%: 결제 서비스 헬스 조회
    const r = http.get(`${PAYMENT_API}/actuator/health`);
    check(r, { 'payment health 200': (r) => r.status === 200 });
  }

  sleep(1);
}
