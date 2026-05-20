import http from 'k6/http';
import { check, sleep } from 'k6';

// 핵심 서비스 경로를 확인하는 작은 end-to-end smoke check.
//
// load나 stress 시나리오 전에 실행한다. 짧고 낮은 부하로 routing 문제,
// 서비스 비가용 상태, 잘못된 seed data를 빠르게 잡는다.

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
  // Product 서비스 read path.
  const productsRes = http.get(`${PRODUCT_API}/api/products?page=0&size=20`);
  check(productsRes, { 'products list 200': (r) => r.status === 200 });

  // 아래 주문 payload에서 사용할 Product 상세 데이터.
  const detailRes = http.get(`${PRODUCT_API}/api/products/1`);
  check(detailRes, { 'product detail 200': (r) => r.status === 200 });

  // 서비스별 health check로 dependency/bootstrap 문제를 빠르게 확인한다.
  const customerHealth = http.get(`${CUSTOMER_API}/actuator/health`);
  check(customerHealth, { 'customer health 200': (r) => r.status === 200 });

  const paymentHealth = http.get(`${PAYMENT_API}/actuator/health`);
  check(paymentHealth, { 'payment health 200': (r) => r.status === 200 });

  // Order, Product 재고 예약, Payment까지 이어지는 end-to-end write path.
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
