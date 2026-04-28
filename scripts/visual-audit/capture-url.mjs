// Generic playwright URL → PNG capture. Used for Kafka UI evidence pages
// (topic message lists, consumer-group lag) where dashboard UIDs don't
// apply.
//
// Env:
//   URL          — full URL to navigate (required)
//   OUT          — output PNG path (required)
//   WAIT_MS      — render wait (default 8000)
//   FULL_PAGE    — '1' to capture full scroll, otherwise viewport only

import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const URL = process.env.URL;
const OUT = process.env.OUT;
const WAIT_MS = Number(process.env.WAIT_MS || 8000);
const FULL_PAGE = process.env.FULL_PAGE === '1';

if (!URL || !OUT) {
  console.error('URL and OUT env vars required');
  process.exit(2);
}

mkdirSync(dirname(OUT), { recursive: true });

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 1600, height: 1200 },
});
const page = await context.newPage();
console.error(`[capture-url] ${URL}`);
try {
  await page.goto(URL, { waitUntil: 'networkidle', timeout: 60000 });
} catch (e) {
  console.error(`  nav timeout: ${e.message}`);
}
await page.waitForTimeout(WAIT_MS);
await page.screenshot({ path: OUT, fullPage: FULL_PAGE });
console.error(`  → ${OUT}`);
await browser.close();
