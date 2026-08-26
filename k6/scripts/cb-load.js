import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// Circuit Breaker phase. Product internal stock-reservation is slowed via chaos
// (app.chaos.stock-delay-ms). Order-create calls product synchronously, so a slow
// product either (before, no CB) piles up order threads and starves UNRELATED traffic,
// or (after, CB) fast-fails and keeps the bystander path healthy.
//
// PRIMARY value proof = the bystander stream (product browse, a fast catalog read that
// does NOT touch stock reservation): its p95 should SPIKE in before and stay FLAT in after.

const BASE = __ENV.BASE_URL || 'http://localhost:8082';
const PRODUCT_BASE = __ENV.PRODUCT_BASE || BASE;
const JWT = __ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig';
const VARIANT_ID = Number(__ENV.VARIANT_ID || 1);
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const CUSTOMER_ID = Number(__ENV.CUSTOMER_ID || 1);
const ORDER_CONC = Number(__ENV.ORDER_CONC || 60);   // order-create pressure (starves threads in before)
const BYSTANDER_RATE = Number(__ENV.BYSTANDER_RATE || 10);
const DUR = __ENV.DUR || '180s';                       // fault window >> CB timers

const orderT = new Trend('cb_order_create_ms', true);
const bystander = new Trend('cb_bystander_ms', true);   // the cascade proxy
const order503 = new Counter('cb_order_503');           // CB fast-fail count
const orderOk = new Counter('cb_order_2xx');

export const options = {
  tags: { testid: __ENV.TESTID || 'cb' },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    order_create: {
      executor: 'constant-vus', vus: ORDER_CONC, duration: DUR,
      exec: 'orderCreate', tags: { stream: 'order' },
    },
    bystander_browse: {
      executor: 'constant-arrival-rate', rate: BYSTANDER_RATE, timeUnit: '1s', duration: DUR,
      preAllocatedVUs: 50, maxVUs: 200, exec: 'bystanderBrowse', tags: { stream: 'bystander' },
    },
  },
};

export function orderCreate() {
  const payload = JSON.stringify({
    customerId: CUSTOMER_ID,
    items: [{ productVariantId: VARIANT_ID, productId: PRODUCT_ID, productName: 'CB', size: 'M', color: 'B', unitPrice: 29900, quantity: 1 }],
    shippingAddress: { recipientName: 'T', phone: '010-0', zipCode: '06234', address1: 'Seoul', address2: 'A' },
  });
  const res = http.post(`${BASE}/api/orders`, payload, {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${JWT}`, 'X-Customer-Id': String(CUSTOMER_ID) }, timeout: '30s',
  });
  orderT.add(res.timings.duration);
  if (res.status === 503) order503.add(1);
  else if (res.status >= 200 && res.status < 300) orderOk.add(1);
}

export function bystanderBrowse() {
  // Fast catalog read — does NOT touch stock reservation. Starves only if threads are exhausted.
  const res = http.get(`${PRODUCT_BASE}/api/products?page=0&size=10`, { timeout: '30s' });
  bystander.add(res.timings.duration);
  check(res, { 'bystander ok': (r) => r.status === 200 });
}
