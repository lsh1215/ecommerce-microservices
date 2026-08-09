import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// ---------------------------------------------------------------------------
// 선착순 재고 예약 — Shopify식 동기 예약(SKIP LOCKED, 재고 1개=row 1줄).
//
// 계약: POST {PRODUCT_API}/api/internal/products/variants/{id}/reserve-unit
//        body {orderId, quantity} -> 200 GRANTED / 409 SOLD_OUT / 5xx(장애)
//   큐도 폴링도 없다. 요청 하나가 그 자리에서 확보/매진을 받고 끝난다.
//
// 오버셀은 유닛 row 수가 재고 상한이라 구조적으로 불가능하다(실행 후 DB row-count로 최종 확인).
// 측정 규율(docs/loadtest/README.md): open model, in-cluster k6, dropped/5xx 확인.
// ---------------------------------------------------------------------------

const PRODUCT_API = __ENV.PRODUCT_API || 'http://localhost:8081';
const BASE = `${PRODUCT_API}/api/internal/products/variants`;
const AUTH = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const QUANTITY = Number(__ENV.QUANTITY || 1);
const MODE = (__ENV.MODE || 'spike').toLowerCase();

const PROFILES = {
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
  stress: [
    { duration: '15s', target: 1000 },
    { duration: '30s', target: 2000 },
    { duration: '30s', target: 3000 },
    { duration: '30s', target: 4000 },
    { duration: '15s', target: 0 },
  ],
};
const stages = __ENV.STAGES ? JSON.parse(__ENV.STAGES) : (PROFILES[MODE] || PROFILES.spike);

const granted = new Counter('fcfs_granted'); // 200 — 유닛 확보
const soldOut = new Counter('fcfs_sold_out'); // 409 — 재고 없음(정상적인 선착순 탈락)
const fail5xx = new Counter('fcfs_5xx'); // 서버 오류 — 0이어야 함
const clientTimeout = new Counter('fcfs_client_timeout'); // status 0

export const options = {
  tags: { testid: __ENV.TESTID || `flash-sync-${MODE}` },
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    reserve: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.PRE_VUS || 300),
      maxVUs: Number(__ENV.MAX_VUS || 4000),
      stages,
      gracefulStop: '10s',
      exec: 'reserve',
    },
  },
  thresholds: {
    fcfs_5xx: ['count<1'],
    http_req_failed: ['rate<1'],
  },
};

// 매 반복마다 전역 고유 orderId. (__VU, __ITER)는 실행 전체에서 유일하므로 충돌이 없다.
function uniqueOrderId() {
  return __VU * 10000000 + __ITER;
}

export function reserve() {
  const orderId = uniqueOrderId();
  const res = http.post(
    `${BASE}/${VARIANT_ID}/reserve-unit`,
    JSON.stringify({ orderId, quantity: QUANTITY }),
    { headers: { 'Content-Type': 'application/json', Authorization: AUTH }, timeout: '10s' },
  );
  if (res.status === 0) {
    clientTimeout.add(1);
    return;
  }
  if (res.status >= 500) {
    fail5xx.add(1);
    return;
  }
  if (res.status === 200) {
    granted.add(1);
  } else if (res.status === 409) {
    soldOut.add(1);
  }
  check(res, { 'no 5xx': (r) => r.status < 500 });
}
