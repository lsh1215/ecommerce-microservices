import http from 'k6/http';
import { check, sleep } from 'k6';

// 로컬 smoke check용 최소 end-to-end 주문 흐름.
//
// 무거운 부하 시나리오를 실행하기 전에 Product read API와 Order 생성이 함께 동작하는지 확인한다.

const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';

const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;

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
  // Product 목록 조회는 가장 가벼운 catalog 경로이며 서비스 가용성을 빠르게 확인한다.
  const productsRes = http.get(`${PRODUCT_API}/api/products?page=0&size=10`);
  check(productsRes, { 'products 200': (r) => r.status === 200 });

  // 주문 payload에서 사용할 상품 상세 데이터가 존재하는지 확인한다.
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

  // Order 생성은 동기 Order -> Product -> Payment 경로를 검증한다.
  const orderRes = http.post(`${ORDER_API}/api/orders`, orderPayload, {
    headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER },
  });
  check(orderRes, {
    'order created': (r) => r.status === 200 || r.status === 201,
  });

  sleep(1);
}
