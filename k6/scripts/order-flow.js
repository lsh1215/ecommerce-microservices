import http from 'k6/http';
import { check, sleep } from 'k6';

const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 1,
      duration: '10s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.1'],
  },
};

export default function () {
  const productsRes = http.get(`${PRODUCT_API}/api/products?page=0&size=10`);
  check(productsRes, { 'products 200': (r) => r.status === 200 });

  const detailRes = http.get(`${PRODUCT_API}/api/products/1`);
  check(detailRes, { 'product detail 200': (r) => r.status === 200 });

  const orderPayload = JSON.stringify({
    customerId: 1,
    items: [
      {
        productVariantId: 1,
        productId: 1,
        productName: 'Test Product',
        size: 'M',
        color: 'Black',
        unitPrice: 29900,
        quantity: 1,
      },
    ],
    shippingAddress: {
      recipientName: 'Test User',
      phone: '010-1234-5678',
      zipCode: '06234',
      address1: 'Seoul Gangnam',
      address2: 'Apt 101',
    },
  });

  const orderRes = http.post(`${ORDER_API}/api/orders`, orderPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(orderRes, {
    'order created': (r) => r.status === 200 || r.status === 201,
  });

  sleep(1);
}
