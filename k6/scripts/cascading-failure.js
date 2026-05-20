import http from 'k6/http';
import { check, sleep } from 'k6';

// 의존 서비스 장애 상황을 보기 위한 지속적인 Order 트래픽.
//
// dependency가 비정상이거나 느린 상태에서 실행한다. 압박 상황에서 Order thread/connection이
// 얼마나 빨리 소모되는지 보고, 응답과 timeout을 구분하는 것이 핵심이다.

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;

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

  // 사용자가 기다리기엔 긴 시간을 피하되, client가 포기하기 전에 server-side failure를 관측할 수 있게 둔다.
  const res = http.post(`${ORDER_API}/api/orders`, orderPayload, {
    headers: { 'Content-Type': 'application/json', Authorization: AUTH_HEADER },
    timeout: '10s',
  });

  check(res, {
    // status가 0이 아니면 서버가 응답한 것이고, 0이면 client timeout이다.
    'responded (any status)': (r) => r.status > 0,
    'not timeout': (r) => r.timings.duration < 10000,
  });

  sleep(0.3);
}
