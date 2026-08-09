import http from 'k6/http';
import { Counter } from 'k6/metrics';

// Single hot-row RESERVE ceiling. Hits reserve-stock with NO orderId, so it takes the
// pure conditional Atomic UPDATE path (UPDATE product_variant SET stock=stock-1
// WHERE id=? AND stock>=?) on ONE row. The single-row X-lock is held to commit, so
// throughput is bounded by commit serialization on that row. Stock is preloaded huge
// so every request does a real decrement+commit (never sells out during the run).
//
// Open model (ramping-arrival-rate): offered load is independent of response time, so
// when the service saturates, VUs pile up / iterations drop and the COMPLETED rate
// plateaus at the ceiling. Ground-truth throughput is read separately from the DB
// (Com_commit delta), this script drives load + captures latency/failures.

const PRODUCT_API = __ENV.PRODUCT_API || 'http://service-product:8081';
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);

const ok = new Counter('hot_2xx');
const c4xx = new Counter('hot_4xx');
const c5xx = new Counter('hot_5xx');

export const options = {
  tags: { testid: __ENV.TESTID || 'hot-row-atomic' },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.START_RATE || 100),
      timeUnit: '1s',
      preAllocatedVUs: 400,
      maxVUs: 4000,
      stages: JSON.parse(__ENV.STAGES),
    },
  },
};

export default function () {
  const res = http.post(
    `${PRODUCT_API}/api/internal/products/variants/${VARIANT_ID}/reserve-stock`,
    JSON.stringify({ quantity: 1 }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '30s' }
  );
  if (res.status >= 200 && res.status < 300) ok.add(1);
  else if (res.status >= 500) c5xx.add(1);
  else c4xx.add(1);
}
