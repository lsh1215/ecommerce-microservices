// Visual audit — loads every dashboard, waits for panels to render,
// then counts the "No data" / error badges and screenshots each one.
//
// Output:
//   /tmp/grafana-shots/<dashboard-uid>.png         full-page screenshot
//   /tmp/grafana-shots/<dashboard-uid>.json        per-panel status JSON
//   stdout: markdown summary

import { chromium } from 'playwright';
import { readdirSync, writeFileSync } from 'node:fs';

const BASE = 'http://34.64.219.137/grafana';
const OUT = '/tmp/grafana-shots';

const DASHBOARDS = [
  'ecommerce-overview',
  'ecommerce-jvm-micrometer',
  'ecommerce-mysql-overview',
  'ecommerce-kafka-exporter-overview',
  'ecommerce-kubernetes-views-pods',
  'ecommerce-logs-app',
  'ecommerce-k6-prometheus',
];

// Playwright 1.x ships chromium but its download path changes by version.
// If multiple versions are cached, launch() picks the one matching the
// installed playwright package.
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 1920, height: 1400 },
  // Grafana needs a real-looking user-agent to avoid certain fast-paths
  userAgent:
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36',
});

const results = [];
for (const uid of DASHBOARDS) {
  const page = await context.newPage();
  const pageErrors = [];
  page.on('pageerror', (e) => pageErrors.push(e.message));
  // refresh=5s forces Grafana to query at least once with a live range
  const url = `${BASE}/d/${uid}/?orgId=1&from=now-5m&to=now&refresh=&kiosk`;
  console.error(`[visit] ${uid}`);
  try {
    await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 });
  } catch (e) {
    console.error(`  nav timeout: ${e.message}`);
  }
  // Let all panels finish their first render; Grafana 11 uses data-viz-panel-key on each.
  // 20s gives template variables (label_values cascades) time to resolve
  // even on slower dashboards like JVM where instance depends on application.
  await page.waitForTimeout(20000);

  // Count panels and no-data / error states. Grafana 11 marks panels
  // with data-viz-panel-key and injects a "No data" text block into panels
  // without series — or an "Error" / status-message for failing queries.
  const snapshot = await page.evaluate(() => {
    const panels = [...document.querySelectorAll('[data-viz-panel-key], [class*="panel-container"]')];
    const byTitle = new Map();
    for (const p of panels) {
      // Find the panel header text
      const headerEl = p.querySelector('h2, h6, [data-testid*="title"], [class*="panel-title"], header');
      const title = (headerEl && headerEl.textContent.trim()) || '(no title)';
      if (byTitle.has(title)) continue; // dedupe on first occurrence
      const text = (p.textContent || '').toLowerCase();
      let status = 'OK';
      if (text.includes('no data')) status = 'NO_DATA';
      if (text.includes('datasource error') || text.includes('failed to') || text.includes('query error') || text.includes('status: 500'))
        status = 'ERROR';
      // "loading" state: if the first render hasn't completed yet, mark it distinct
      if (text.includes('loading') && !text.includes('edit')) status = 'LOADING';
      byTitle.set(title, status);
    }
    return [...byTitle.entries()].map(([title, status]) => ({ title, status }));
  });

  await page.screenshot({
    path: `${OUT}/${uid}.png`,
    fullPage: true,
  });
  writeFileSync(`${OUT}/${uid}.json`, JSON.stringify(snapshot, null, 2));
  const counts = snapshot.reduce((acc, p) => ({ ...acc, [p.status]: (acc[p.status] || 0) + 1 }), {});
  console.error(`  panels=${snapshot.length}  ${JSON.stringify(counts)}`);
  results.push({ uid, snapshot, counts, pageErrors });
  await page.close();
}
await browser.close();

console.log('\n# Playwright visual audit\n');
for (const r of results) {
  console.log(`\n## ${r.uid}`);
  const c = r.counts;
  console.log(
    `OK: ${c.OK || 0}   NO_DATA: ${c.NO_DATA || 0}   ERROR: ${c.ERROR || 0}   LOADING: ${c.LOADING || 0}   total: ${r.snapshot.length}`,
  );
  if ((c.NO_DATA || 0) + (c.ERROR || 0) > 0) {
    console.log('\nfailing panels:');
    for (const p of r.snapshot.filter((x) => x.status !== 'OK')) {
      console.log(`  - [${p.status}] ${p.title}`);
    }
  }
  if (r.pageErrors.length) {
    console.log('\npage errors:');
    for (const e of r.pageErrors.slice(0, 3)) console.log(`  - ${e}`);
  }
}

console.log(`\nScreenshots in ${OUT}/`);
