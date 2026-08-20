import http from 'k6/http';
import { check } from 'k6';

// 의존 서비스 장애 상황을 보기 위한 지속적인 Order 트래픽.
//
// dependency가 비정상이거나 느린 상태에서 실행한다. 압박 상황에서 Order thread/connection이
// 얼마나 빨리 소모되는지 보고, 응답과 timeout을 구분하는 것이 핵심이다.

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;

export const options = {
  scenarios: {
    // 도착률 고정(open model)이어야 한다. constant-vus 로는 dependency 가 느려지는
    // 순간 VU 가 응답을 기다리며 막혀 제공 부하가 함께 줄어들고, 그러면 Order 의
    // 스레드와 커넥션은 끝내 소모되지 않는다. 소모되는 과정을 보려는 테스트가
    // 소모를 막는 셈이었다.
    //
    // RATE 는 정상 용량보다 높게 잡아야 한다. 낮게 잡으면 dependency 가 죽어도
    // 시스템이 따라가므로 아무 일도 일어나지 않는다.
    constant_load: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 50),
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 50,
      maxVUs: 500,
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

}
