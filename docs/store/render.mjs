// Renders the Play Store icon (512x512) and feature graphic (1024x500)
// from the Android launcher vector (108-unit viewport) via headless Chromium.
// Run: node docs/store/render.mjs <out-dir>  (needs Playwright; a global install works via NODE_PATH)
import { chromium } from 'playwright';
import path from 'node:path';

const out = process.argv[2];

const MARK = `
  <path fill="#FFFFFF" d="M41.47,28.57 A2.32,2.32 0 0 1 43.79,30.89 V34.7 H39.15 V30.89 A2.32,2.32 0 0 1 41.47,28.57 Z"/>
  <path fill="#FFFFFF" d="M66.34,28.57 A2.32,2.32 0 0 1 68.66,30.89 V34.7 H64.02 V30.89 A2.32,2.32 0 0 1 66.34,28.57 Z"/>
  <path fill="#FFFFFF" d="M36.17,34.32 H71.64 A7.42,7.42 0 0 1 79.06,41.74 V69.78 A7.42,7.42 0 0 1 71.64,77.2 H36.17 A7.42,7.42 0 0 1 28.75,69.78 V41.74 A7.42,7.42 0 0 1 36.17,34.32 Z"/>
  <path fill="#1A8BA4" d="M36.17,34.32 H71.64 A7.42,7.42 0 0 1 79.06,41.74 V45.74 H28.75 V41.74 A7.42,7.42 0 0 1 36.17,34.32 Z"/>
  <path fill="none" stroke="#0E6A80" stroke-width="2.41" stroke-linecap="round" stroke-linejoin="round" d="M35.07,69.5 L45.09,62.72 L52.89,66.99 L61.61,56.97 L72.47,50.57"/>
  <circle cx="35.07" cy="69.5" r="2.04" fill="#0E6A80"/>
  <circle cx="45.09" cy="62.72" r="2.04" fill="#0E6A80"/>
  <circle cx="61.61" cy="56.97" r="2.04" fill="#0E6A80"/>
  <circle cx="72.47" cy="50.57" r="2.41" fill="#1A8BA4"/>`;

// Mark bounds: x 28.75..79.06 (center 53.9), y 28.57..77.2 (center 52.9).
// Play icon: full-bleed square (Play applies its own mask); card ~62% wide.
function iconSvg(size, cardShare) {
  const s = (size * cardShare) / 50.31;
  const tx = size / 2 - 53.905 * s, ty = size / 2 - 52.885 * s;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <rect width="${size}" height="${size}" fill="#1F2122"/>
    <g transform="translate(${tx} ${ty}) scale(${s})">${MARK}</g></svg>`;
}

const tile = (size) => {
  const s = (size * 0.6) / 50.31;
  const tx = size / 2 - 53.905 * s, ty = size / 2 - 52.885 * s;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <rect width="${size}" height="${size}" rx="${size * 0.22}" fill="#26292A"/>
    <g transform="translate(${tx} ${ty}) scale(${s})">${MARK}</g></svg>`;
};

const feature = `<!doctype html><html><head><meta charset="utf-8"><style>
  html,body{margin:0;width:1024px;height:500px;background:#17191A;overflow:hidden}
  body{display:flex;align-items:center;gap:56px;padding:0 0 0 112px;box-sizing:border-box;
       font-family:'Segoe UI Variable Display','Segoe UI',system-ui,sans-serif}
  .t{display:flex;flex-direction:column;gap:18px}
  h1{margin:0;font-size:104px;line-height:1;font-weight:800;color:#FFFFFF;letter-spacing:-2px}
  p{margin:0;font-size:34px;line-height:1.25;font-weight:500;color:#C9CDCE;max-width:560px}
  .chips{display:flex;gap:10px;margin-top:8px}
  .chip{font-size:22px;font-weight:600;color:#5CC8DE;border:2px solid #2E5A64;border-radius:999px;padding:6px 16px}
</style></head><body>
  ${tile(240)}
  <div class="t"><h1>Rytm</h1><p>Зміни, гроші й позики<br>в одному застосунку</p>
  <div class="chips"><span class="chip">Графік</span><span class="chip">Фінанси</span><span class="chip">Позики</span></div></div>
</body></html>`;

const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });

await page.setViewportSize({ width: 512, height: 512 });
await page.setContent(`<html><body style="margin:0">${iconSvg(512, 0.62)}</body></html>`);
await page.screenshot({ path: path.join(out, 'play-icon-512.png'), clip: { x: 0, y: 0, width: 512, height: 512 } });

await page.setViewportSize({ width: 1024, height: 500 });
await page.setContent(feature);
await page.screenshot({ path: path.join(out, 'play-feature-1024x500.png'), clip: { x: 0, y: 0, width: 1024, height: 500 } });

await browser.close();
console.log('done');
