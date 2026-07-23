// E2E test for the Finance tab's "Курси валют" (FX rates) widget
// (js/settings-managers.js's renderFxWidget(), the #fx-widget-list
// element) — this app's 3rd Preact-rendered widget, after js/debt.js's
// payoff-forecast chart and js/analytics-csv.js's expense donut chart (see
// CLAUDE.md's "Preact adoption" note). No existing test covered this
// widget at all before this file. Same stubbed-Firebase Playwright recipe
// as tests/analytics-donut-chart.mjs/tests/debt-forecast-chart.mjs. Run
// with:
//
//   node tests/fx-widget-rates.mjs
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
const PORT = 8934;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
const STUB_FIRESTORE = `
const _docs = new Map();
export function getFirestore(){ return {}; }
export function initializeFirestore(){ return {}; }
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
    if (v && v.__isArrayUnion) {
      const arr = Array.isArray(merged[k]) ? merged[k].slice() : [];
      v.items.forEach((item) => { if (!arr.includes(item)) arr.push(item); });
      merged[k] = arr;
    } else if (v && v.__isArrayRemove) {
      const arr = Array.isArray(merged[k]) ? merged[k].slice() : [];
      merged[k] = arr.filter((item) => !v.items.includes(item));
    } else {
      merged[k] = v;
    }
  }
  _docs.set(ref.path, merged);
}
export function arrayUnion(...items){ return { __isArrayUnion: true, items }; }
export function arrayRemove(...items){ return { __isArrayRemove: true, items }; }
`;
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'fx-widget-uid', email:'fx-widget@example.com'})); return ()=>{}; }
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

async function main() {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));

  const browser = await chromium.launch(CHROMIUM_PATH ? { executablePath: CHROMIUM_PATH } : {});
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    const pageErrors = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
    await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));

    // js/app-init.js's init() unconditionally calls maybeAutoUpdateRates()
    // on cold start, which fetches live NBU rates (js/settings-managers.js)
    // and overwrites the seed USD/EUR/GBP/PLN rates this test asserts
    // against. This sandbox's own network blocks bank.gov.ua (see
    // CLAUDE.md's "expected sandbox noise" note), which is why this looked
    // deterministic locally — but a CI runner with real internet access
    // lets the live fetch actually succeed, silently replacing the seed
    // rate this test expects. Block it explicitly so the test is
    // deterministic in any environment, not just this sandbox's.
    await page.route('**bank.gov.ua**', (r) => r.abort());
    await page.route('**allorigins.win**', (r) => r.abort());
    await page.route('**/api/privat-rates', (r) => r.abort());

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    await page.click('#nav-finance');
    await page.waitForTimeout(300);

    function widgetRows() {
      return page.evaluate(() => Array.from(document.querySelectorAll('#fx-widget-list .fx-widget-row')).map((row) => ({
        code: row.querySelector('.settings-row-title')?.textContent,
        rate: row.querySelector('.fx-widget-rate')?.textContent,
      })));
    }

    // ── Default seed rates render as 4 real Preact-rendered rows
    // (USD/EUR/GBP/PLN — CURRENCY_LIST minus UAH) ──
    await page.click('[data-action="open-tools-manager"]');
    await page.waitForSelector('#tools-modal', { state: 'visible' });
    let rows = await widgetRows();
    if (rows.length !== 4) throw new Error(`expected 4 currency rows, found ${rows.length}: ${JSON.stringify(rows)}`);
    const usdRow = rows.find((r) => r.code === 'USD');
    if (!usdRow || !usdRow.rate.includes('41')) throw new Error(`expected USD's seed rate (41) in the widget, got ${JSON.stringify(usdRow)}`);
    console.log('[ok] default state: 4 real Preact-rendered currency rows with the correct seed rates');

    // ── Editing a rate in the Rates manager (opened by clicking the widget
    // itself, per its own data-action="open-rates-manager") re-renders the
    // widget via renderFinance() -> renderFxWidget() — proving the row for
    // the edited currency updates in place, and no duplicate/stale row is
    // left behind (the specific regression a raw innerHTML= write next to
    // Preact-managed content could reintroduce). ──
    await page.click('#fx-widget-list');
    await page.waitForSelector('#rates-modal', { state: 'visible' });
    await page.fill('.rate-row-input[data-code="USD"]', '55.5');
    await page.locator('.rate-row-input[data-code="USD"]').dispatchEvent('change');
    await page.waitForTimeout(200);
    await page.evaluate(() => { document.getElementById('rates-modal').style.display = 'none'; });

    rows = await widgetRows();
    if (rows.length !== 4) throw new Error(`expected still exactly 4 currency rows after editing a rate, found ${rows.length} (stale/duplicate Preact row)`);
    const updatedUsdRow = rows.find((r) => r.code === 'USD');
    if (!updatedUsdRow || !updatedUsdRow.rate.includes('55,5') && !updatedUsdRow.rate.includes('55.5')) {
      throw new Error(`expected USD's rate to update to 55.5, got ${JSON.stringify(updatedUsdRow)}`);
    }
    console.log('[ok] editing a rate updates the corresponding row in place via a real Preact re-render, with no duplicate/stale row left behind');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the whole flow');

    console.log('\nFX WIDGET RATES TEST PASSED');
  } catch (err) {
    console.error('\nFX WIDGET RATES TEST FAILED:', err.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
    server.kill();
  }
}

main();
