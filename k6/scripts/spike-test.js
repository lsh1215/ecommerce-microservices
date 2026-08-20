import http from 'k6/http';
import { check } from 'k6';

// 갑작스러운 트래픽 변화에서 Order 경로의 반응을 보는 짧은 spike 테스트.
//
// 도착률을 짧은 시간에 끌어올려 burst traffic에서 thread pool, connection pool,
// timeout regression을 잡는다. 용량을 재는 테스트가 아니므로 여기서 나온 처리량을
// capacity로 인용하지 않는다. 보는 값은 부하가 걷힌 뒤의 회복 시간이다.

const ORDER_API = __ENV.ORDER_API || 'http://localhost:8082';
const AUTH_HEADER = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;

export const options = {
  scenarios: {
    // VU 를 올리는 것으로는 spike 가 만들어지지 않는다. VU 는 이전 응답을 받아야
    // 다음 요청을 보내므로, 시스템이 느려지는 즉시 도착률이 따라 내려간다.
    // 몰려드는 쪽을 재려면 도착률 자체를 올려야 한다.
    //
    // 마지막 두 구간은 회복 관측용이다. spike 테스트의 지표는 최대 처리량이 아니라
    // 부하가 걷힌 뒤 언제 정상으로 돌아오는지다.
    spike: {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.BASE_RATE || 20),
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 1000,
      stages: [
        { duration: '10s', target: Number(__ENV.BASE_RATE || 20) },
        { duration: '5s', target: Number(__ENV.PEAK_RATE || 400) },
        { duration: '25s', target: Number(__ENV.PEAK_RATE || 400) },
        { duration: '5s', target: Number(__ENV.BASE_RATE || 20) },
        { duration: '20s', target: Number(__ENV.BASE_RATE || 20) },
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

}
