// Receipt scan (OCR) UI wiring test for js/receipt-ocr.js + js/finance.js's
// "Сканувати чек" button. The real OCR pipeline (Tesseract.js WASM, a real
// worker, real language data) was verified once by hand against a synthetic
// receipt image under the actual deployed CSP — see CHANGELOG.md — but
// running real WASM OCR in every CI run would be slow and flaky. Instead,
// this stubs js/vendor/tesseract/tesseract.esm.min.js itself (the one
// dynamic import() boundary js/receipt-ocr.js's scanReceiptImage() crosses)
// via page.route(), the same technique every other test here uses for the
// Firebase SDK's own gstatic module URLs. This exercises the *real*
// js/receipt-ocr.js and js/finance.js code, just with a fake OCR backend.
// Same stubbed-Firebase Playwright recipe as tests/ai-category-suggestion.mjs.
// Run with:
//
//   node tests/receipt-scan-ui.mjs
import fs from 'node:fs';
import path from 'node:path';
import { spawn, execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

function resolveGlobalPlaywrightPath() {
  const sandboxPath = '/opt/node22/lib/node_modules/playwright/index.mjs';
  if (fs.existsSync(sandboxPath)) return sandboxPath;
  const globalRoot = execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim();
  const globalPath = path.join(globalRoot, 'playwright', 'index.mjs');
  if (fs.existsSync(globalPath)) return globalPath;
  throw new Error(`could not locate a global playwright install (checked ${sandboxPath} and ${globalPath})`);
}

const { chromium } = await import(resolveGlobalPlaywrightPath());

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const PORT = 8921;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
const STUB_FIRESTORE = `
const _docs = new Map();
export function getFirestore(){ return {}; }
export function doc(parent, ...rest){ if (parent && parent.path !== undefined) return { path: parent.path + '/' + rest[0] }; return { path: rest.join('/') }; }
export function collection(parent, name){ const base = parent && parent.path !== undefined ? parent.path : ''; return { path: (base ? base + '/' : '') + name }; }
export async function getDoc(ref){ const d = _docs.get(ref.path); return { exists: () => d !== undefined, data: () => d }; }
export async function setDoc(ref, data){ _docs.set(ref.path, data); }
export async function deleteDoc(ref){ _docs.delete(ref.path); }
export async function getDocs(ref){ const prefix = ref.path + '/'; const items = []; for (const [k, v] of _docs) { if (k.startsWith(prefix) && !k.slice(prefix.length).includes('/')) items.push({ id: k.slice(prefix.length), data: () => v }); } return { docs: items, forEach(fn){ items.forEach(fn); }, empty: items.length === 0, size: items.length }; }
export function writeBatch(){ const ops = []; return { set(ref, data){ ops.push(() => _docs.set(ref.path, data)); }, delete(ref){ ops.push(() => _docs.delete(ref.path)); }, async commit(){ ops.forEach((fn) => fn()); } }; }
export async function updateDoc(ref, data){
  const existing = _docs.get(ref.path) || {};
  const merged = { ...existing };
  for (const k in data) {
    const v = data[k];
    if (v && v.__isArrayUnion) { const arr = Array.isArray(merged[k]) ? merged[k].slice() : []; v.items.forEach((item) => { if (!arr.includes(item)) arr.push(item); }); merged[k] = arr; }
    else if (v && v.__isArrayRemove) { const arr = Array.isArray(merged[k]) ? merged[k].slice() : []; merged[k] = arr.filter((item) => !v.items.includes(item)); }
    else { merged[k] = v; }
  }
  _docs.set(ref.path, merged);
}
export function arrayUnion(...items){ return { __isArrayUnion: true, items }; }
export function arrayRemove(...items){ return { __isArrayRemove: true, items }; }
`;
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'ocr-test-uid', email:'ocr@example.com'})); return ()=>{}; }
export async function signOut(){ return; }
export async function deleteUser(){ return; }
export async function createUserWithEmailAndPassword(){ throw new Error('stub'); }
export async function signInWithEmailAndPassword(){ throw new Error('stub'); }
export class GoogleAuthProvider{}
export async function signInWithPopup(){ throw new Error('stub'); }
export async function signInWithRedirect(){ throw new Error('stub'); }
export async function getRedirectResult(){ return null; }
export async function sendPasswordResetEmail(){ return; }
export class EmailAuthProvider{ static credential(){ return {}; } }
export async function reauthenticateWithCredential(){ return; }
export async function reauthenticateWithPopup(){ return; }
export class RecaptchaVerifier{ constructor(){} render(){ return Promise.resolve(1); } clear(){} }
export async function signInWithPhoneNumber(){ return { confirm: async () => ({}) }; }
export async function linkWithPhoneNumber(){ return { confirm: async () => ({}) }; }
export async function unlink(){ return; }
`;
const STUB_MESSAGING = `
export function getMessaging(){ return {}; }
export async function getToken(){ return 'fake-token'; }
export async function deleteToken(){ return true; }
export function onMessage(){ return () => {}; }
export async function isSupported(){ return true; }
`;

// Stands in for js/vendor/tesseract/tesseract.esm.min.js. Records the
// options createWorker() was called with (so the test can assert
// receipt-ocr.js still points at self-hosted, same-origin paths and never
// re-enables the blob:-URL worker spawn path a strict worker-src would
// reject) and returns a worker whose recognize() resolves with a fixed
// OCR text — real js/receipt-ocr.js parsing logic then runs on it for real.
const TESSERACT_STUB = (recognizedText) => `
window.__createWorkerCalls = [];
export default {
  OEM: { TESSERACT_ONLY: 0, LSTM_ONLY: 1, TESSERACT_LSTM_COMBINED: 2, DEFAULT: 3 },
  async createWorker(langs, oem, options){
    window.__createWorkerCalls.push({ langs, oem, options });
    return {
      async recognize(file){ return { data: { text: ${JSON.stringify(recognizedText)} } }; },
      async terminate(){},
    };
  },
};
`;

async function withStubbedOCR(browser, recognizedText, run) {
  // Service workers block page.route() interception for requests they've
  // taken control of (sw.js calls clients.claim() right on activation, and
  // by the time this test's later "pick a receipt" step fires its dynamic
  // import() of tesseract.esm.min.js, the SW has often already claimed the
  // page) — the stub route silently never fires and the real ~8MB vendored
  // library loads instead. Every other tests/*.mjs gets lucky by stubbing
  // Firebase SDK imports that resolve immediately on page load, before the
  // SW has finished installing; this test's stub fires later, so it needs
  // serviceWorkers:'block' to be reliable rather than racy.
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, serviceWorkers: 'block' });
  const page = await context.newPage();
  const pageErrors = [];
  page.on('pageerror', (err) => pageErrors.push(err.message));
  await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
  await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
  await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
  await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
  await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));
  await page.route('**/js/vendor/tesseract/tesseract.esm.min.js', (r) => r.fulfill({ contentType: 'application/javascript', body: TESSERACT_STUB(recognizedText) }));
  await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
  await page.click('#nav-finance');
  await page.waitForTimeout(200);
  await page.click('.fin-fab');
  await page.waitForSelector('#tx-form-modal', { state: 'visible' });
  await run(page);
  if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
  await context.close();
}

// A minimal valid 1x1 PNG — content doesn't matter since OCR itself is
// stubbed, but the file needs to actually exist for setInputFiles().
const TINY_PNG_BASE64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';

async function main() {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));
  const tmpPng = path.join(ROOT, '.tmp-receipt-scan-test.png');
  fs.writeFileSync(tmpPng, Buffer.from(TINY_PNG_BASE64, 'base64'));
  const browser = await chromium.launch(CHROMIUM_PATH ? { executablePath: CHROMIUM_PATH } : {});
  try {
    // ── the scan button exists, is hidden for transfers, and triggers the file input ──
    await withStubbedOCR(browser, 'СУМА: 42.00', async (page) => {
      const btn = page.locator('#fin-scan-receipt-btn');
      if (!(await btn.isVisible())) throw new Error('expected the scan-receipt button to be visible for an expense by default');
      await page.click('#btn-trn');
      await page.waitForTimeout(100);
      const groupDisplay = await page.locator('#receipt-scan-group').evaluate((el) => getComputedStyle(el).display);
      if (groupDisplay !== 'none') throw new Error(`expected the scan-receipt button hidden for a transfer, got display:${groupDisplay}`);
      await page.click('#btn-exp');
      console.log('[ok] scan-receipt button is visible for income/expense, hidden for transfers');
    });

    // ── picking a photo prefills amount + date from the (stubbed) OCR result ──
    await withStubbedOCR(browser, 'МАГАЗИН\n16.07.2026\nСУМА: 250.00', async (page) => {
      const fileInput = page.locator('#fin-receipt-input');
      await fileInput.setInputFiles(tmpPng);
      await page.waitForFunction(() => document.getElementById('fin-amount')?.value === '250', { timeout: 5000 });
      const amount = await page.locator('#fin-amount').inputValue();
      const date = await page.locator('#fin-date').inputValue();
      if (amount !== '250') throw new Error(`expected amount 250, got ${amount}`);
      if (date !== '2026-07-16') throw new Error(`expected date 2026-07-16, got ${date}`);
      const calls = await page.evaluate(() => window.__createWorkerCalls);
      if (!calls.length) throw new Error('expected createWorker() to have been called');
      const opts = calls[0].options;
      if (opts.workerBlobURL !== false) throw new Error(`expected workerBlobURL:false (same-origin Worker(), no blob: URL), got ${opts.workerBlobURL}`);
      if (!/^\.\/js\/vendor\/tesseract\//.test(opts.workerPath)) throw new Error(`expected a self-hosted workerPath, got ${opts.workerPath}`);
      if (!/^\.\/js\/vendor\/tesseract\//.test(opts.corePath)) throw new Error(`expected a self-hosted corePath, got ${opts.corePath}`);
      if (!/^\.\/js\/vendor\/tesseract\//.test(opts.langPath)) throw new Error(`expected a self-hosted langPath, got ${opts.langPath}`);
      console.log('[ok] picking a receipt photo prefills amount + date, and the worker is spawned same-origin with no blob: URL');
    });

    // ── an OCR result with nothing recognizable shows a "not found" toast, never blocks the form ──
    await withStubbedOCR(browser, 'blurry unreadable nonsense', async (page) => {
      const fileInput = page.locator('#fin-receipt-input');
      await fileInput.setInputFiles(tmpPng);
      // #toast is a single reused element (see js/ui-widgets.js's showToast) —
      // the "processing" toast fires first, so wait specifically for it to be
      // replaced by the post-scan one rather than just "any toast text".
      await page.waitForFunction(() => {
        const t = document.getElementById('toast');
        return t && t.classList.contains('show') && t.textContent && !/Розпізнаю/.test(t.textContent);
      }, { timeout: 5000 });
      const amount = await page.locator('#fin-amount').inputValue();
      if (amount !== '') throw new Error(`expected amount to stay empty on an unreadable receipt, got ${amount}`);
      // The form itself must still be usable — not blocked/disabled by the failed scan.
      await page.fill('#fin-amount', '10');
      const after = await page.locator('#fin-amount').inputValue();
      if (after !== '10') throw new Error('expected the amount field to remain editable after a failed scan');
      console.log('[ok] an unreadable receipt shows feedback but never blocks manual entry');
    });
    // ── a genuinely hung recognize() call still recovers via the timeout, never leaves the UI stuck ──
    // Real-device report: a scan on an actual phone photo never completed
    // and the button stayed disabled until the tab was closed and reopened.
    // scanReceiptImage() takes an optional timeoutMs override specifically
    // so this can be exercised in ~1s here instead of the real 45s — see
    // js/receipt-ocr.js's own comment on why that parameter exists.
    {
      const context = await browser.newContext({ viewport: { width: 390, height: 844 }, serviceWorkers: 'block' });
      const page = await context.newPage();
      const pageErrors = [];
      page.on('pageerror', (err) => pageErrors.push(err.message));
      await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
      await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
      await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
      await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
      await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));
      await page.route('**/js/vendor/tesseract/tesseract.esm.min.js', (r) => r.fulfill({
        contentType: 'application/javascript',
        body: `export default { OEM: { LSTM_ONLY: 1 }, async createWorker(){ return { async recognize(){ return new Promise(()=>{}); }, async terminate(){} }; } };`,
      }));
      await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
      const outcome = await page.evaluate(async () => {
        const mod = await import('./js/receipt-ocr.js');
        const file = new File([new Uint8Array([137, 80, 78, 71])], 'test.png', { type: 'image/png' });
        const start = Date.now();
        try {
          await mod.scanReceiptImage(file, 1000);
          return { ok: false, reason: 'expected a rejection, resolved instead' };
        } catch (e) {
          return { ok: e.message === 'receipt-ocr-timeout', message: e.message, elapsedMs: Date.now() - start };
        }
      });
      if (!outcome.ok) throw new Error(`hung recognize() did not time out as expected: ${JSON.stringify(outcome)}`);
      if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
      console.log(`[ok] a hung recognize() call rejects via the timeout instead of hanging forever (${outcome.elapsedMs}ms)`);
      await context.close();
    }
  } finally {
    await browser.close();
    server.kill();
    fs.rmSync(tmpPng, { force: true });
  }

  console.log('\nRECEIPT SCAN UI TEST PASSED');
}

main().catch((err) => {
  console.error('\nRECEIPT SCAN UI TEST FAILED:', err.message);
  process.exitCode = 1;
});
