// k6 warmup smoke load — runs at the same VU count as the upcoming
// measurement so HikariCP / RestClient / JIT all match the measurement
// connection profile. testid is `warmup-<unit>-<leg>` so warmup
// metrics are tagged separately and excluded from measurement panels.
//
// Env: WARMUP_VUS (default 5), WARMUP_DURATION (default '90s'),
//      ORDER_API (default 34.64.219.137 ingress), JWT (default demo bearer)
import http from 'k6/http';
import { sleep } from 'k6/options';

const ORDER_API = __ENV.ORDER_API || 'http://34.64.219.137';
const AUTH = `Bearer ${__ENV.JWT || 'eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0.sig'}`;

export const options = {
  vus: Number(__ENV.WARMUP_VUS || 5),
  duration: __ENV.WARMUP_DURATION || '90s',
  thresholds: {
    // No thresholds — we don't fail warmup on latency, just exercise the path.
  },
};

export default function () {
  // Browse path (no DB write contention)
  http.get(`${ORDER_API}/api/products?page=0&size=10`, {
    headers: { 'Content-Type': 'application/json' },
  });
  // Order POST path (full SAGA / sync chain)
  http.post(`${ORDER_API}/api/orders`,
    JSON.stringify({
      items: [{
        productVariantId: 1, productId: 1, productName: 'Warmup',
        size: 'S', color: 'B', unitPrice: 29900, quantity: 1,
      }],
      shippingAddress: {
        recipientName: 'warmup', phone: '010', zipCode: '06234',
        address1: 'x', address2: 'y',
      },
    }),
    { headers: { 'Content-Type': 'application/json', Authorization: AUTH } }
  );
  // Pace at ~1 req/s/VU
  // (no sleep — tight loop maximizes JIT exposure)
}
