// Captures the Jaeger search-results page for a given service + time
// window. Used as visual evidence: in the problem leg's window the
// service-payment search returns 0 traces (because nothing reached
// Kafka so service-payment never produced a span); in the solution
// leg's window the same search returns the consumer traces.
//
// Usage:
//   node scripts/capture-jaeger-search.mjs \
//     --service service-payment \
//     --start 2026-05-04T06:50:00 \
//     --end   2026-05-04T07:05:00 \
//     --out   docs/evidence/02-outbox-pattern/problem/traces/jaeger-payment-search.png

import { chromium } from '/Users/leesanghun/My_Project/ecommerce-microservices/scripts/visual-audit/node_modules/playwright/index.mjs';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

function arg(name, dflt) {
  const idx = process.argv.indexOf(`--${name}`);
  if (idx === -1) return dflt;
  return process.argv[idx + 1];
}

const service = arg('service');
const start = arg('start');
const end = arg('end');
const out = arg('out');
const base = arg('base', 'http://34.64.219.137');

if (!service || !start || !end || !out) {
  console.error('missing required arg. Usage: --service S --start TS --end TS --out PATH');
  process.exit(2);
}

mkdirSync(dirname(out), { recursive: true });

// Jaeger UI's start/end query params don't render properly when handed
// absolute microsecond ranges from outside; the lookback path is the
// reliable one. We pick a relative lookback that covers the window we
// care about by computing minutes from now to start.
const startMs = new Date(start + (start.includes('Z') ? '' : 'Z')).getTime();
const minutesAgo = Math.ceil((Date.now() - startMs) / 60000) + 5;
const lookback = `${minutesAgo}m`;

const url = `${base}/jaeger/search?service=${service}&lookback=${lookback}&limit=200`;
console.error(`navigating: ${url}`);

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width: 1600, height: 1200 } });
const page = await ctx.newPage();

page.on('console', msg => {
  const t = msg.type();
  if (t === 'error' || t === 'warning') {
    console.error(`[browser ${t}] ${msg.text()}`);
  }
});

await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 });
// Jaeger loads search results lazily; give it time to render the
// histogram + result list (or "No traces found" message).
await page.waitForTimeout(4500);

await page.screenshot({ path: out, fullPage: true });
console.error(`wrote ${out}`);

await browser.close();
