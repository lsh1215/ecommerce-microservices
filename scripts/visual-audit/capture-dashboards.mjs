// Capture multiple Grafana dashboards for a given time range to PNGs.
//
// Env:
//   FROM, TO       — unix ms time range (required)
//   OUT_DIR        — directory to write {dash-uid}.png into (required)
//   DASHBOARDS     — comma-separated list of dashboard UIDs (required)
//   GRAFANA_URL    — base URL (default: http://34.64.219.137/grafana)
//   WAIT_MS        — render wait per dashboard (default 18000)

import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';

const FROM = process.env.FROM;
const TO = process.env.TO;
const OUT_DIR = process.env.OUT_DIR;
const DASHBOARDS = (process.env.DASHBOARDS || '').split(',').map(s => s.trim()).filter(Boolean);
const GRAFANA = process.env.GRAFANA_URL || 'http://34.64.219.137/grafana';
const WAIT_MS = Number(process.env.WAIT_MS || 18000);
// Template-variable URL params via VAR_<NAME>=<value> envs, e.g.
// VAR_TESTID=u04-solution -> ?var-testid=u04-solution
const VAR_PARAMS = Object.entries(process.env)
  .filter(([k]) => k.startsWith('VAR_'))
  .map(([k, v]) => `&var-${k.slice(4).toLowerCase()}=${encodeURIComponent(v)}`)
  .join('');

if (!FROM || !TO || !OUT_DIR || DASHBOARDS.length === 0) {
  console.error('FROM, TO, OUT_DIR, DASHBOARDS env vars required');
  process.exit(2);
}

mkdirSync(OUT_DIR, { recursive: true });

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 1920, height: 2400 },
  userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/537.36 Chrome/128 Safari/537.36',
});

const summary = [];
for (const uid of DASHBOARDS) {
  const url = `${GRAFANA}/d/${uid}/?orgId=1&from=${FROM}&to=${TO}&kiosk${VAR_PARAMS}`;
  const page = await context.newPage();
  console.error(`[capture] ${uid}`);
  try {
    await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 });
  } catch (e) {
    console.error(`  nav timeout: ${e.message}`);
  }
  await page.waitForTimeout(WAIT_MS);

  const pngPath = `${OUT_DIR}/${uid}.png`;
  await page.screenshot({ path: pngPath, fullPage: true });

  // Quick "no data" panel count to validate completeness
  const stats = await page.evaluate(() => {
    const panels = [...document.querySelectorAll('[data-viz-panel-key]')];
    let total = 0, noData = 0;
    for (const p of panels) {
      total++;
      if ((p.textContent || '').toLowerCase().includes('no data')) noData++;
    }
    return { total, noData };
  });
  console.error(`  panels=${stats.total} no_data=${stats.noData}  → ${pngPath}`);
  summary.push({ uid, png: pngPath, ...stats });
  await page.close();
}
await browser.close();

writeFileSync(`${OUT_DIR}/_capture-summary.json`, JSON.stringify(summary, null, 2));
console.error('done.');
