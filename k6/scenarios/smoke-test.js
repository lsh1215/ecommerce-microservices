import http from 'k6/http';
import { check, sleep } from 'k6';

// Phase 5 — Smoke Test
// 목적: 4개 서비스의 핵심 엔드포인트가 정상 동작하는지 가장 작은 부하로 빠르게 검증.
// 5 VUs, 30초. 모든 경로가 2xx를 반환하고 p99 < 1s면 통과.

const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const PAYMENT_API = __ENV.PAYMENT_API || 'http://localhost:8083';
const CUSTOMER_API = __ENV.CUSTOMER_API || 'http://localhost:8084';
const CUSTOMER_ID = __ENV.CUSTOMER_ID || 14;
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxNCJ9.sig'}`;

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<1000'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  // 1. 브랜드/상품 목록 조회 (가장 흔한 경로)
  const productsRes = http.get(`${PRODUCT_API}/api/products?page=0&size=20`);
  check(productsRes, { 'products list 200': (r) => r.status === 200 });

  // 2. 상품 상세 조회
  const detailRes = http.get(`${PRODUCT_API}/api/products/1`);
  check(detailRes, { 'product detail 200': (r) => r.status === 200 });

  // 3. 고객 서비스 health (기본 actuator 경로)
  const customerHealth = http.get(`${CUSTOMER_API}/actuator/health`);
  check(customerHealth, { 'customer health 200': (r) => r.status === 200 });

  // 4. 결제 서비스 health
  const paymentHealth = http.get(`${PAYMENT_API}/actuator/health`);
  check(paymentHealth, { 'payment health 200': (r) => r.status === 200 });

  // 5. 주문 생성 (SAGA 전체 흐름 시작)
  const orderPayload = JSON.stringify({
    customerId: CUSTOMER_ID,
    items: [
      {
        productVariantId: 1,
        productId: 1,
        productName: 'Essential Cotton Crew Tee',
        size: 'S',
        color: 'Black',
        unitPrice: 29900,
        quantity: 1,
      },
    ],
    shippingAddress: {
      recipientName: 'Smoke Test',
      phone: '010-0000-0000',
      zipCode: '06234',
      address1: 'Seoul Gangnam',
      address2: 'Apt 101',
    },
  });

  const orderRes = http.post(`${ORDER_API}/api/orders`, orderPayload, {
    headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER },
  });
  check(orderRes, { 'order created 201': (r) => r.status === 201 });

  sleep(1);
}
