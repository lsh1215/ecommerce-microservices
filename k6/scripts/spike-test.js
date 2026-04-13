import http from 'k6/http';
import { check, sleep } from 'k6';

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
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
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  sleep(0.5);
}
