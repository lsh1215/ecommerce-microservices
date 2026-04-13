import http from 'k6/http';
import { check, sleep } from 'k6';

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';

export const options = {
  scenarios: {
    constant_load: {
      executor: 'constant-vus',
      vus: 20,
      duration: '60s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    http_req_failed: ['rate<0.5'],
  },
};

export default function () {
  const orderPayload = JSON.stringify({
    customerId: 1,
    items: [
      {
        productVariantId: 1,
        productId: 1,
        productName: 'Failure Test',
        size: 'M',
        color: 'Black',
        unitPrice: 29900,
        quantity: 1,
      },
    ],
    shippingAddress: {
      recipientName: 'Test',
      phone: '010-0000-0000',
      zipCode: '06234',
      address1: 'Seoul',
      address2: 'Test',
    },
  });

  const res = http.post(`${ORDER_API}/api/orders`, orderPayload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '10s',
  });

  check(res, {
    'responded (any status)': (r) => r.status > 0,
    'not timeout': (r) => r.timings.duration < 10000,
  });

  sleep(0.3);
}
