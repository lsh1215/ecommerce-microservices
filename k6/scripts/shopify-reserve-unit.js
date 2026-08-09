import http from 'k6/http';
import { Counter } from 'k6/metrics';

// Shopify-style SYNCHRONOUS per-unit reservation. Hits reserve-unit with a unique
// orderId per call: each request runs SELECT ... FOR UPDATE SKIP LOCKED + UPDATE on
// the variant's stock_unit rows and returns 200 GRANTED / 409 SOLD_OUT inline. Under
// concurrency, many HTTP threads run SKIP LOCKED against the SAME variant's rows at
// once, so this measures the concurrent-SKIP-LOCKED ceiling (page-latch / commit
// contention) of the synchronous design. A big AVAILABLE pool is preloaded so it does
// not sell out during the run. Ground-truth throughput is read from the DB
// (Com_commit delta + Threads_running); this script drives load + captures latency.

const PRODUCT_API = __ENV.PRODUCT_API || 'http://service-product:8081';
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);

const granted = new Counter('granted_200');
const soldout = new Counter('soldout_409');
const err5xx = new Counter('err_5xx');

export const options = {
  tags: { testid: __ENV.TESTID || 'shopify-reserve-unit' },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.START_RATE || 100),
      timeUnit: '1s',
      preAllocatedVUs: 500,
      maxVUs: 5000,
      stages: JSON.parse(__ENV.STAGES),
    },
  },
};

export default function () {
  const orderId = __VU * 100000000 + __ITER; // unique per call (idempotency dedup avoidance)
  const res = http.post(
    `${PRODUCT_API}/api/internal/products/variants/${VARIANT_ID}/reserve-unit`,
    JSON.stringify({ orderId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '30s' }
  );
  if (res.status === 200) granted.add(1);
  else if (res.status === 409) soldout.add(1);
  else if (res.status >= 500) err5xx.add(1);
}
