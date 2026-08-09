import http from 'k6/http';
import { Counter } from 'k6/metrics';

// Mixed read+write load for the NORMAL (평시) reservation path, comparing two write
// strategies under the SAME concurrent browse-read load on the product primary:
//   STRATEGY=redis   -> POST /reserve-stock  (sync settle: Redis capacity check + DB INSERT)
//   STRATEGY=shopify -> POST /reserve-unit   (per-unit SELECT ... FOR UPDATE SKIP LOCKED)
// A constant read scenario (GET variant) simulates browse traffic hitting the same DB,
// while a ramping write scenario finds the reservation ceiling. Ground-truth write rate
// is read from the DB per arm (stock_reservation rows for redis, stock_unit RESERVED for
// shopify); this script drives load and captures per-op latency/failures.

const PRODUCT_API = __ENV.PRODUCT_API || 'http://service-product:8081';
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const STRATEGY = __ENV.STRATEGY || 'shopify';
const READ_RATE = Number(__ENV.READ_RATE || 2000);
const READ_DUR = __ENV.READ_DUR || '70s';

const readOk = new Counter('read_2xx');
const readErr = new Counter('read_err');
const write2xx = new Counter('write_2xx');
const write409 = new Counter('write_409');
const write5xx = new Counter('write_5xx');

const WRITE_PATH = STRATEGY === 'redis' ? 'reserve-stock' : 'reserve-unit';

export const options = {
  tags: { testid: __ENV.TESTID || 'normal-mixed' },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    reads: {
      executor: 'constant-arrival-rate',
      rate: READ_RATE, timeUnit: '1s', duration: READ_DUR,
      preAllocatedVUs: 400, maxVUs: 3000,
      exec: 'readFn', tags: { op: 'read' },
    },
    writes: {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.START_RATE || 100), timeUnit: '1s',
      preAllocatedVUs: 500, maxVUs: 5000,
      stages: JSON.parse(__ENV.STAGES),
      exec: 'writeFn', tags: { op: 'write' },
    },
  },
};

export function readFn() {
  const res = http.get(`${PRODUCT_API}/api/internal/products/variants/${VARIANT_ID}`, { timeout: '30s' });
  if (res.status >= 200 && res.status < 300) readOk.add(1); else readErr.add(1);
}

export function writeFn() {
  const orderId = __VU * 100000000 + __ITER;
  const res = http.post(
    `${PRODUCT_API}/api/internal/products/variants/${VARIANT_ID}/${WRITE_PATH}`,
    JSON.stringify({ orderId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '30s' }
  );
  if (res.status >= 200 && res.status < 300) write2xx.add(1);
  else if (res.status === 409) write409.add(1);
  else if (res.status >= 500) write5xx.add(1);
}
