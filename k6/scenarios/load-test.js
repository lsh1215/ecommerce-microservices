import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// 서비스 수준의 일반 트래픽 mix를 확인하는 load test.
//
// smoke test 통과 후, stress/breakpoint 시나리오 전에 실행한다.
// read 비중을 높게 두되 Order -> Product -> Payment 경로가 계속 실행될 만큼 주문 생성도 포함한다.
//
// Traffic mix:
// - 60% Product browsing
// - 20% Order creation
// - 10% Order health
// - 10% Payment health

const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const PAYMENT_API = __ENV.PAYMENT_API || 'http://localhost:8083';
const CUSTOMER_ID = __ENV.CUSTOMER_ID || 14;
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxNCJ9.sig'}`;

// catalog latency와 order creation latency를 분리해서 보기 위한 endpoint별 Trend다.
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
    // Browsing branch: 목록 + 상세 조회로 단순한 상품 페이지 조회를 흉내낸다.
    const listRes = http.get(`${PRODUCT_API}/api/products?page=0&size=20`);
    browseDuration.add(listRes.timings.duration);
    check(listRes, { 'browse list 200': (r) => r.status === 200 });

    const productId = Math.floor(Math.random() * 8) + 1;
    const detailRes = http.get(`${PRODUCT_API}/api/products/${productId}`);
    browseDuration.add(detailRes.timings.duration);
    check(detailRes, { 'browse detail 200 or 404': (r) => r.status === 200 || r.status === 404 });
  } else if (rand < 0.8) {
    // Order branch: seed data 요구사항을 안정적으로 유지하기 위해 작은 stock set에서 고른다.
    const variantId = Math.floor(Math.random() * 3) + 1;
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
    // Order 서비스 가용성을 보는 가벼운 control path다.
    const r = http.get(`${ORDER_API}/actuator/health`);
    check(r, { 'order health 200': (r) => r.status === 200 });
  } else {
    // Payment 서비스 가용성을 보는 가벼운 control path다.
    const r = http.get(`${PAYMENT_API}/actuator/health`);
    check(r, { 'payment health 200': (r) => r.status === 200 });
  }

  sleep(1);
}
