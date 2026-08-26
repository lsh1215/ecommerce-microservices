import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

// Reservation throughput ceiling as a function of APP replicas.
//
// The question: is the ceiling set by the application tier or by the database
// row? Run the identical ramp at PODS=1, 2, 3 with the pod CPU limit held at
// 1500m (Guaranteed QoS, one pod per node) and compare.
//
//   single-row path  -> ceiling does NOT move with replicas  => commit
//                       serialisation on that row is the wall
//   unit-row path    -> ceiling scales with replicas         => the wall was
//                       structural and splitting the row removed it
//
// This replaces the previous approach of giving one pod an 8 vCPU node, which
// changed CPU, memory, NIC class and neighbour set at once. See
// docs/observability/loadtest-baseline-audit.md and the pre-registered
// predictions in docs/observability/rebaseline-prediction.md.
//
// Closed model (ramping-VUs -> 150) on purpose: it is the model the original
// hot-row measurement used. Switching to an open model would change a second
// variable and break the comparison with the number being replaced.

const PRODUCT_API = __ENV.PRODUCT_API || 'http://service-product:8081';
const VARIANT_ID = __ENV.VARIANT_ID || '1';
const WRITE_PATH = __ENV.WRITE_PATH || 'reserve-stock-and-snapshot';
const PODS = __ENV.PODS || 'unset';
const PEAK_VUS = Number(__ENV.PEAK_VUS || 150);
const RAMP = __ENV.RAMP || '60s';
const HOLD = __ENV.HOLD || '180s';

const reserve2xx = new Counter('reserve_2xx');
const reserve409 = new Counter('reserve_conflict');
const reserve5xx = new Counter('reserve_5xx');
const reserveMs = new Trend('reserve_ms', true);

export const options = {
  discardResponseBodies: true,
  // Low-cardinality tag so one Prometheus query plots the ceiling against
  // replica count across the three runs.
  tags: { pods: PODS, write_path: WRITE_PATH },
  scenarios: {
    reserve: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { target: PEAK_VUS, duration: RAMP },
        { target: PEAK_VUS, duration: HOLD },
      ],
      gracefulRampDown: '10s',
      exec: 'reserveFn',
    },
  },
  thresholds: {
    // Correctness is not negotiable while probing the ceiling.
    reserve_5xx: ['count==0'],
  },
};

export function reserveFn() {
  // Unique orderId per iteration so the duplicate-reservation guard does not
  // short-circuit the write path under measurement.
  const orderId = __VU * 100000000 + __ITER;
  const res = http.post(
    `${PRODUCT_API}/api/internal/products/variants/${VARIANT_ID}/${WRITE_PATH}`,
    JSON.stringify({ orderId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '30s' },
  );
  reserveMs.add(res.timings.duration);
  if (res.status >= 200 && res.status < 300) reserve2xx.add(1);
  else if (res.status === 409) reserve409.add(1);
  else if (res.status >= 500) reserve5xx.add(1);
}
