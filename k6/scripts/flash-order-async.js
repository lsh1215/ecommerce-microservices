import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// ---------------------------------------------------------------------------
// 선착순 예약 — 비동기 접수(Outbox → Kafka → granter).
//
// 계약: POST {ORDER_API}/api/orders/flash-reserve  header X-Customer-Id
//        body {variantId, quantity} -> 202 Accepted {reservationId, status:PENDING}
//   접수만 즉시 반환하고(공정 순번 확정), 실제 유닛 확보는 granter가 도착 순서대로 처리한다.
//   여기서 재는 것은 "접수 처리량/지연". 공정성·오버셀·최종 결과는 실행 후 DB로 확인한다:
//     - stock_unit: RESERVED+CONFIRMED = 정확히 N, 오버셀 0 (row 수가 상한).
//     - flash_reservation: RESERVED = N 이고 그 id들이 최소 id N개(= 도착순 공정).
// ---------------------------------------------------------------------------

const ORDER_API = __ENV.ORDER_API || 'http://service-order:8082';
const URL = `${ORDER_API}/api/orders/flash-reserve`;
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const QUANTITY = Number(__ENV.QUANTITY || 1);
const MODE = (__ENV.MODE || 'spike').toLowerCase();

const PROFILES = {
  smoke: [
    { duration: '5s', target: 20 },
    { duration: '10s', target: 20 },
    { duration: '3s', target: 0 },
  ],
  rampup: [
    { duration: '30s', target: 100 },
    { duration: '30s', target: 250 },
    { duration: '30s', target: 500 },
    { duration: '30s', target: 750 },
    { duration: '30s', target: 1000 },
    { duration: '20s', target: 0 },
  ],
  spike: [
    { duration: '3s', target: 1000 },
    { duration: '60s', target: 1000 },
    { duration: '15s', target: 50 },
    { duration: '10s', target: 0 },
  ],
};
const stages = __ENV.STAGES ? JSON.parse(__ENV.STAGES) : (PROFILES[MODE] || PROFILES.spike);

const accepted = new Counter('flash_accepted'); // 202 — 접수됨
const rej4xx = new Counter('flash_4xx'); // 4xx — 검증/인증 실패(0이어야 함)
const fail5xx = new Counter('flash_5xx'); // 5xx — 서버 오류(0이어야 함)
const clientTimeout = new Counter('flash_client_timeout'); // status 0

export const options = {
  tags: { testid: __ENV.TESTID || `flash-async-${MODE}` },
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    submit: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 200),
      maxVUs: Number(__ENV.MAX_VUS || 3000),
      stages,
      gracefulStop: '10s',
      exec: 'submit',
    },
  },
  thresholds: {
    flash_5xx: ['count<1'],
  },
};

export function submit() {
  const res = http.post(
    URL,
    JSON.stringify({ variantId: VARIANT_ID, quantity: QUANTITY }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Customer-Id': String((__VU % 100000) + 1),
        Authorization: `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`,
      },
      timeout: '10s',
    },
  );
  if (res.status === 0) {
    clientTimeout.add(1);
    return;
  }
  if (res.status === 202) {
    accepted.add(1);
  } else if (res.status >= 500) {
    fail5xx.add(1);
  } else {
    rej4xx.add(1);
  }
  check(res, { 'accepted 202': (r) => r.status === 202 });
}
