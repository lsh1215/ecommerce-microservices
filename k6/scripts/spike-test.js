import http from 'k6/http';
import { check, sleep } from 'k6';

// 갑작스러운 트래픽 변화에서 Order 경로의 반응을 보는 짧은 spike 테스트.
//
// 정확한 capacity 테스트가 아니라 VU spike 테스트다. burst traffic에서 thread pool,
// connection pool, timeout regression을 빠르게 잡는 용도로 사용한다.

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        // 짧게 warm-up한 뒤 급격히 올리고 다시 회복 구간을 둔다.
        { duration: '10s', target: 10 },
        { duration: '30s', target: 100 },
        { duration: '10s', target: 10 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    http_req_failed: ['rate<0.15'],
  },
};

export default function () {
  // hot-row contention 테스트가 아니므로 variant를 분산해 일반적인 catalog burst에 가깝게 만든다.
  const orderPayload = JSON.stringify({
    customerId: Math.floor(Math.random() * 100) + 1,
    items: [
      {
        productVariantId: Math.floor(Math.random() * 50) + 1,
        productId: Math.floor(Math.random() * 20) + 1,
        productName: 'Load Test Product',
        size: 'M',
        color: 'Black',
        unitPrice: 29900,
        quantity: 1,
      },
    ],
    shippingAddress: {
      recipientName: 'Load Test User',
      phone: '010-0000-0000',
      zipCode: '06234',
      address1: 'Seoul',
      address2: 'Test',
    },
  });

  const res = http.post(`${ORDER_API}/api/orders`, orderPayload, {
    headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER },
  });
  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  sleep(0.5);
}
