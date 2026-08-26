import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

// Mixed browse + order load, stepped until the SLO breaks.
//
// Traffic mix is derived from published e-commerce benchmarks, not guessed —
// see docs/observability/ecommerce-traffic-model.md:
//
//   conversion 1.4~2.9% (Littledata 2,800 sites / Dynamic Yield 300M sessions)
//   2.6 pages per session, ~1.5 of them product detail
//   => 52~107 product reads per order  ->  READ_RATIO default 100
//
// Open model on purpose. A closed model backs off when the system slows down,
// so the ceiling never shows; constant-arrival-rate keeps offering load and the
// break point becomes visible.
//
// Run twice — replica OFF then ON — and compare the last step that holds SLO.

const PRODUCT_API = __ENV.PRODUCT_API || 'http://service-product:8081';
const ORDER_API = __ENV.ORDER_API || 'http://service-order:8082';
const READ_RATIO = Number(__ENV.READ_RATIO || 100);
const VARIANT_POOL = Number(__ENV.VARIANT_POOL || 50000);
const CUSTOMER_ID = Number(__ENV.CUSTOMER_ID || 1);
const REPLICA = __ENV.REPLICA || 'unset';
const STEP = __ENV.STEP || '60s';
// Order arrival rates per step; reads are derived as rate * READ_RATIO.
const ORDER_STEPS = JSON.parse(__ENV.ORDER_STEPS || '[5,10,15,20,25]');

const readOk = new Counter('browse_2xx');
const readErr = new Counter('browse_err');
const orderOk = new Counter('order_2xx');
const order4xx = new Counter('order_4xx');
const orderErr = new Counter('order_5xx');
const readMs = new Trend('browse_ms', true);
const orderMs = new Trend('order_ms', true);

function stages(multiplier) {
  return ORDER_STEPS.map((r) => ({ target: Math.round(r * multiplier), duration: STEP }));
}

export const options = {
  discardResponseBodies: true,
  tags: { replica: REPLICA, read_ratio: String(READ_RATIO) },
  scenarios: {
    browse: {
      executor: 'ramping-arrival-rate',
      startRate: ORDER_STEPS[0] * READ_RATIO,
      timeUnit: '1s',
      preAllocatedVUs: 400,
      maxVUs: 4000,
      stages: stages(READ_RATIO),
      exec: 'browseFn',
      tags: { op: 'browse' },
    },
    order: {
      executor: 'ramping-arrival-rate',
      startRate: ORDER_STEPS[0],
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 1500,
      stages: stages(1),
      exec: 'orderFn',
      tags: { op: 'order' },
    },
  },
  thresholds: {
    // Reported per step from the time series; these are the run-level guards.
    browse_ms: ['p(95)<200'],
    order_ms: ['p(95)<1000'],
    order_5xx: ['count==0'],
  },
};

// Browsing spreads across the catalogue. Concentrating on one variant would
// turn this into a hot-row test, which is phase ①'s job, not this one.
export function browseFn() {
  const variantId = 1 + Math.floor(Math.random() * VARIANT_POOL);
  // name 태그 고정 — URL별 시리즈 분화(카디널리티 폭발) 방지.
  const res = http.get(`${PRODUCT_API}/api/internal/products/variants/${variantId}`,
    { timeout: '10s', tags: { name: 'GET /api/internal/products/variants/:id' } });
  readMs.add(res.timings.duration);
  if (res.status >= 200 && res.status < 300) readOk.add(1);
  else readErr.add(1);
}

export function orderFn() {
  const variantId = 1 + Math.floor(Math.random() * VARIANT_POOL);
  const body = JSON.stringify({
    items: [{
      productVariantId: variantId,
      productId: 1,
      productName: `Bench ${variantId}`,
      size: 'M',
      color: 'BLACK',
      unitPrice: 10000.00,
      quantity: 1,
    }],
    shippingAddress: {
      recipientName: 'Load Test',
      phone: '010-0000-0000',
      zipCode: '06236',
      address1: 'Seoul',
      address2: 'Gangnam',
    },
    memo: 'k6-mixed-threshold',
  });
  const res = http.post(`${ORDER_API}/api/orders`, body, {
    headers: { 'Content-Type': 'application/json', 'X-Customer-Id': String(CUSTOMER_ID) },
    timeout: '30s',
  });
  orderMs.add(res.timings.duration);
  if (res.status >= 200 && res.status < 300) orderOk.add(1);
  else if (res.status >= 400 && res.status < 500) order4xx.add(1);
  else orderErr.add(1);
}
