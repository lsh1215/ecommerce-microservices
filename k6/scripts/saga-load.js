import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// SAGA phase load: order-create through the gateway. Proves API-decoupling
// (order returns immediately, payment is async) and generates the order
// volume whose convergence + compensation are verified post-run from the DB.
//
// Open model (constant-arrival-rate) so offered load is independent of
// response time. Warm-up stage is separated from the measure stage; the
// measure window (from/to) is what gets captured on the dashboards.

const BASE = __ENV.BASE_URL || 'http://localhost:8082';
const JWT = __ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig';
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const CUSTOMER_ID = Number(__ENV.CUSTOMER_ID || 1);
const RATE = Number(__ENV.RATE || 30);          // offered orders/sec in the measure stage
const WARMUP_RATE = Number(__ENV.WARMUP_RATE || 10);
const WARMUP_DUR = __ENV.WARMUP_DUR || '30s';   // Java JIT / pool / buffer-pool warm-up (discarded)
const MEASURE_DUR = __ENV.MEASURE_DUR || '60s';

const orderCreate = new Trend('order_create_ms', true);
const orders2xx = new Counter('orders_created_2xx');
const ordersErr = new Counter('orders_create_errors');

export const options = {
  tags: { testid: __ENV.TESTID || 'saga-load' },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: __ENV.MODE === 'vus' ? {
    warmup: {
      executor: 'constant-vus', vus: Number(__ENV.WARMUP_VUS || 5),
      duration: WARMUP_DUR, tags: { phase: 'warmup' },
    },
    measure: {
      executor: 'constant-vus', vus: Number(__ENV.VUS || 20),
      duration: MEASURE_DUR, startTime: WARMUP_DUR, tags: { phase: 'measure' },
    },
  } : {
    warmup: {
      executor: 'constant-arrival-rate',
      rate: WARMUP_RATE, timeUnit: '1s', duration: WARMUP_DUR,
      preAllocatedVUs: 100, maxVUs: 400,
      tags: { phase: 'warmup' },
    },
    measure: {
      executor: 'constant-arrival-rate',
      rate: RATE, timeUnit: '1s', duration: MEASURE_DUR,
      preAllocatedVUs: 200, maxVUs: 1000,
      startTime: WARMUP_DUR,
      tags: { phase: 'measure' },
    },
  },
  thresholds: {
    'order_create_ms{phase:measure}': ['p(95)<3000'],
    'http_req_failed{phase:measure}': ['rate<0.05'],
  },
};

export default function () {
  const payload = JSON.stringify({
    customerId: CUSTOMER_ID,
    items: [{
      productVariantId: VARIANT_ID, productId: PRODUCT_ID, productName: 'SAGA Load',
      size: 'M', color: 'Black', unitPrice: 29900, quantity: 1,
    }],
    shippingAddress: {
      recipientName: 'T', phone: '010-0000-0000', zipCode: '06234',
      address1: 'Seoul', address2: 'A',
    },
  });
  const res = http.post(`${BASE}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${JWT}`, 'X-Customer-Id': String(CUSTOMER_ID) },
    timeout: '30s',
  });
  orderCreate.add(res.timings.duration);
  if (res.status >= 200 && res.status < 300) orders2xx.add(1);
  else ordersErr.add(1);
  check(res, { 'order 2xx': (r) => r.status >= 200 && r.status < 300 });
}
