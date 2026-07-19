// E2E test for the two new Finance-tab dashboard widgets
// (js/dashboard-widgets.js): "Порада дня" (a local, zero-network daily tip
// rotation) and "Топ криптовалюти" (live CoinGecko prices + a Preact
// sparkline chart, this app's 4th Preact-rendered widget). Added alongside
// the existing "goals" widget specifically because the Widgets manager
// used to have exactly one toggleable item and read oddly as a settings
// screen — see CLAUDE.md's Finance-tab-widgets section. Same stubbed-
// Firebase Playwright recipe as tests/analytics-donut-chart.mjs/
// tests/fx-widget-rates.mjs. Run with:
//
//   node tests/dashboard-widgets.mjs
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
const PORT = 8935;
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
export async function updateDoc(ref, data){ const existing = _docs.get(ref.path) || {}; const merged = { ...existing, ...data }; _docs.set(ref.path, merged); }
export function arrayUnion(...items){ return { __isArrayUnion: true, items }; }
export function arrayRemove(...items){ return { __isArrayRemove: true, items }; }
`;
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'dashboard-widgets-uid', email:'dashboard-widgets@example.com'})); return ()=>{}; }
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

// Fixed stub response for CoinGecko's /coins/markets — same reasoning as
// tests/monobank-connect.mjs stubbing "/api/monobank": this repo's test
// setup has no way to depend on a real third-party API's actual live
// response, so the network call itself is intercepted and fulfilled with a
// deterministic fixture instead (matches the fx-widget-rates.mjs lesson —
// asserting on a real external data source's exact values is what made
// that test flaky in CI).
const COINGECKO_FIXTURE = JSON.stringify([
  { id: 'bitcoin', current_price: 65000, price_change_percentage_24h: 2.5, sparkline_in_7d: { price: [60000, 61000, 63000, 62000, 64000, 65000, 65000] } },
  { id: 'ethereum', current_price: 3200, price_change_percentage_24h: -1.2, sparkline_in_7d: { price: [3300, 3250, 3280, 3220, 3210, 3195, 3200] } },
]);

async function main() {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));

  const browser = await chromium.launch(CHROMIUM_PATH ? { executablePath: CHROMIUM_PATH } : {});
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    const pageErrors = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));

    let coinGeckoCalls = 0;
    await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
    await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));
    await page.route('**api.coingecko.com**', (r) => { coinGeckoCalls++; r.fulfill({ contentType: 'application/json', body: COINGECKO_FIXTURE }); });

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    await page.click('#nav-finance');
    await page.waitForTimeout(300);

    // ── Daily tip: zero-network, must render real non-empty text immediately ──
    const tipVisible = await page.evaluate(() => getComputedStyle(document.getElementById('daily-tip-section')).display !== 'none');
    const tipText = await page.evaluate(() => document.getElementById('daily-tip-text').textContent.trim());
    if (!tipVisible) throw new Error('expected the daily-tip section to be visible by default');
    if (!tipText) throw new Error('expected non-empty tip text');
    console.log(`[ok] daily tip: visible by default with real non-empty text ("${tipText.slice(0, 40)}...")`);

    // ── Crypto top: fetches the stubbed CoinGecko response on cold init and
    // renders real Preact h()/render() rows (not stale/empty markup) ──
    await page.waitForFunction(() => getComputedStyle(document.getElementById('crypto-top-section')).display !== 'none', { timeout: 5000 });
    const cryptoRows = await page.evaluate(() => Array.from(document.querySelectorAll('#crypto-top-list .crypto-top-row')).map((row) => ({
      symbol: row.querySelector('.settings-row-title')?.textContent,
      price: row.querySelector('.crypto-top-price')?.textContent,
      change: row.querySelector('.settings-row-sub')?.textContent,
      hasSparkline: !!row.querySelector('.crypto-top-spark svg polyline'),
    })));
    if (cryptoRows.length !== 2) throw new Error(`expected 2 crypto rows (BTC, ETH), found ${cryptoRows.length}: ${JSON.stringify(cryptoRows)}`);
    const btc = cryptoRows.find((r) => r.symbol === 'BTC');
    const btcDigits = btc ? btc.price.replace(/[^\d]/g, '') : '';
    if (!btc || btcDigits !== '65000') throw new Error(`expected BTC's price to be 65000 (uk-UA formatted), got ${JSON.stringify(btc)}`);
    if (!btc.change.includes('2.5')) throw new Error(`expected BTC's 24h change to include 2.5, got "${btc.change}"`);
    if (!btc.hasSparkline) throw new Error('expected a real SVG polyline sparkline for BTC (Preact h()/render(), not stale/empty markup)');
    console.log('[ok] crypto top: fetches the (stubbed) live data and renders 2 real Preact rows with correct price/change/sparkline');

    // ── Widgets manager now has 3 toggleable items, not just 1 ──
    await page.click('#btn-settings');
    await page.waitForTimeout(200);
    await page.click('.settings-row[data-action="open-widgets-manager"]');
    await page.waitForSelector('#widgets-modal', { state: 'visible' });
    const widgetTitles = await page.evaluate(() => Array.from(document.querySelectorAll('#widgets-list .settings-row-title')).map((el) => el.textContent));
    if (widgetTitles.length !== 3) throw new Error(`expected 3 widgets in the manager, found ${widgetTitles.length}: ${JSON.stringify(widgetTitles)}`);
    if (!widgetTitles.includes('Порада дня') || !widgetTitles.includes('Топ криптовалюти')) {
      throw new Error(`expected "Порада дня" and "Топ криптовалюти" among the widgets, got ${JSON.stringify(widgetTitles)}`);
    }
    console.log('[ok] Widgets manager lists all 3 toggleable widgets (goals, daily tip, crypto top)');

    // ── Toggling a widget off actually hides its section ──
    await page.evaluate(() => {
      const cb = document.querySelectorAll('#widgets-list input[type=checkbox]')[1]; // dailyTip
      cb.checked = false;
      cb.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await page.waitForTimeout(150);
    await page.evaluate(() => { document.getElementById('widgets-modal').style.display = 'none'; });
    await page.click('#nav-finance');
    await page.waitForTimeout(200);
    const tipHiddenAfterToggle = await page.evaluate(() => getComputedStyle(document.getElementById('daily-tip-section')).display === 'none');
    if (!tipHiddenAfterToggle) throw new Error('expected the daily-tip section to hide after toggling it off in the Widgets manager');
    console.log('[ok] toggling "Порада дня" off in the Widgets manager actually hides the section');

    // ── Rate-limit dedup: calling maybeRefreshCryptoTop() again within the
    // 30-minute refresh window must NOT call CoinGecko a second time — same
    // "doesn't re-fetch within the window" convention as
    // maybeAutoUpdateRates()'s own 24h gate. Triggered directly via the
    // window.__RYTM_TEST_HOOKS__ export (same reasoning
    // setMonobankSyncGapMsForTesting already exists for: verifying a
    // time-gated action without waiting for the real interval or reloading
    // the whole page). ──
    const callsBeforeSecondCall = coinGeckoCalls;
    await page.evaluate(() => window.__RYTM_TEST_HOOKS__.maybeRefreshCryptoTop());
    await page.waitForTimeout(300);
    if (coinGeckoCalls !== callsBeforeSecondCall) throw new Error(`expected no additional CoinGecko calls within the 30-minute refresh window, but count went from ${callsBeforeSecondCall} to ${coinGeckoCalls}`);
    console.log('[ok] a second refresh attempt within the 30-minute window reuses the cache instead of re-fetching (rate-limit dedup)');

    // ── Re-enabling cryptoTop after it was off with no cached data must
    // fetch immediately, not stay hidden until a full reload (a real bug
    // found by Codex review on this PR: toggleWidget() used to only call
    // applyWidgetVisibility(), which renders from whatever's already
    // cached — with cryptoTop off since cold init, app-init.js's init()
    // is the only maybeRefreshCryptoTop() call site and it returns
    // immediately when the widget starts disabled, so nothing would ever
    // trigger the first fetch once re-enabled). ──
    await page.click('#btn-settings');
    await page.waitForTimeout(200);
    await page.click('.settings-row[data-action="open-widgets-manager"]');
    await page.waitForSelector('#widgets-modal', { state: 'visible' });
    await page.evaluate(() => {
      const cb = document.querySelectorAll('#widgets-list input[type=checkbox]')[2]; // cryptoTop
      cb.checked = false;
      cb.dispatchEvent(new Event('change', { bubbles: true }));
      localStorage.removeItem('mxCryptoTopCache');
    });
    await page.evaluate(() => { document.getElementById('widgets-modal').style.display = 'none'; });
    await page.click('#nav-finance');
    await page.waitForTimeout(200);
    const cryptoHiddenAfterDisableAndClearCache = await page.evaluate(() => getComputedStyle(document.getElementById('crypto-top-section')).display === 'none');
    if (!cryptoHiddenAfterDisableAndClearCache) throw new Error('expected the crypto widget to be hidden once disabled with no cached data');

    const callsBeforeReEnable = coinGeckoCalls;
    await page.click('#btn-settings');
    await page.waitForTimeout(200);
    await page.click('.settings-row[data-action="open-widgets-manager"]');
    await page.waitForSelector('#widgets-modal', { state: 'visible' });
    await page.evaluate(() => {
      const cb = document.querySelectorAll('#widgets-list input[type=checkbox]')[2]; // cryptoTop
      cb.checked = true;
      cb.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await page.evaluate(() => { document.getElementById('widgets-modal').style.display = 'none'; });
    await page.click('#nav-finance');
    await page.waitForFunction(() => getComputedStyle(document.getElementById('crypto-top-section')).display !== 'none', { timeout: 5000 });
    if (coinGeckoCalls <= callsBeforeReEnable) throw new Error(`expected re-enabling cryptoTop to trigger a fresh CoinGecko fetch, but call count stayed at ${coinGeckoCalls}`);
    console.log('[ok] re-enabling "Топ криптовалюти" after it was off with no cached data fetches immediately instead of staying hidden');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the whole flow');

    console.log('\nDASHBOARD WIDGETS TEST PASSED');
  } catch (err) {
    console.error('\nDASHBOARD WIDGETS TEST FAILED:', err.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
    server.kill();
  }
}

main();
