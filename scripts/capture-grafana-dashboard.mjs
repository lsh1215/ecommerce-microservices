// Captures a Grafana dashboard view at a specific time range as PNG.
// Uses the playwright instance vendored under scripts/visual-audit/node_modules.
//
// Usage:
//   node scripts/capture-grafana-dashboard.mjs \
//     --uid ecommerce-outbox \
//     --from 1777547100 \
//     --to   1777547525 \
//     --out  docs/evidence/02-outbox-pattern/problem/dashboards/ecommerce-outbox.png
//
// Optional:
//   --base http://34.64.219.137  (default)
//   --kiosk false                (default true — hides chrome for cleaner caps)

import { chromium } from '/Users/leesanghun/My_Project/ecommerce-microservices/scripts/visual-audit/node_modules/playwright/index.mjs';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

function arg(name, dflt) {
  const idx = process.argv.indexOf(`--${name}`);
  if (idx === -1) return dflt;
  return process.argv[idx + 1];
}

const uid = arg('uid');
const from = arg('from');
const to = arg('to');
const out = arg('out');
const base = arg('base', 'http://34.64.219.137');
const kiosk = arg('kiosk', 'true') !== 'false';

if (!uid || !from || !to || !out) {
  console.error('missing required arg. Usage: --uid UID --from EPOCH --to EPOCH --out PATH');
  process.exit(2);
}

const fromMs = String(from).length === 10 ? Number(from) * 1000 : Number(from);
const toMs = String(to).length === 10 ? Number(to) * 1000 : Number(to);

mkdirSync(dirname(out), { recursive: true });

const url = `${base}/grafana/d/${uid}/?from=${fromMs}&to=${toMs}${kiosk ? '&kiosk=tv' : ''}&theme=light`;
console.error(`navigating: ${url}`);

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width: 1920, height: 1400 } });
const page = await ctx.newPage();

page.on('console', msg => {
  const t = msg.type();
  if (t === 'error' || t === 'warning') {
    console.error(`[browser ${t}] ${msg.text()}`);
  }
});

await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 });
// Grafana renders panels lazily; let queries complete + scroll-trigger lazy loads.
await page.waitForTimeout(4000);
await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
await page.waitForTimeout(2000);
await page.evaluate(() => window.scrollTo(0, 0));
await page.waitForTimeout(1500);

await page.screenshot({ path: out, fullPage: true });
console.error(`wrote ${out}`);

await browser.close();
