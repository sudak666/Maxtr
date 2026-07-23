// E2E test for the Finance tab's Analytics donut chart (js/analytics-csv.js's
// renderAnalytics(), the #analytics-donut element) — this app's second
// Preact-rendered widget, after js/debt.js's payoff-forecast chart (see
// CLAUDE.md's "Preact adoption" note). No existing test covered this widget
// at all before this file. Same stubbed-Firebase Playwright recipe as
// tests/debt-forecast-chart.mjs/tests/e2e-crud.mjs. Run with:
//
//   node tests/analytics-donut-chart.mjs
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
const PORT = 8933;
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'analytics-donut-uid', email:'analytics-donut@example.com'})); return ()=>{}; }
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

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    await page.click('#nav-finance');
    await page.waitForTimeout(300);

    function donutState() {
      return page.evaluate(() => {
        const donut = document.getElementById('analytics-donut');
        return {
          background: donut.style.background,
          totalEls: donut.querySelectorAll('.analytics-donut-total').length,
          totalText: donut.querySelector('.analytics-donut-total')?.textContent || null,
          labelText: donut.querySelector('.analytics-donut-label')?.textContent || null,
          holeEls: donut.querySelectorAll('.analytics-donut-hole').length,
        };
      });
    }

    // ── No transactions yet: empty state, no total element ──
    await page.click('[data-action="open-tools-manager"]');
    await page.waitForSelector('#tools-modal', { state: 'visible' });
    let state = await donutState();
    if (state.totalEls !== 0) throw new Error(`expected no .analytics-donut-total element with zero transactions, found ${state.totalEls}`);
    if (state.holeEls !== 1) throw new Error(`expected exactly one .analytics-donut-hole element, found ${state.holeEls}`);
    if (!state.labelText) throw new Error('expected a non-empty no-data label with zero transactions');
    console.log('[ok] empty state: no total element, no-data label shown, real Preact-rendered markup (not stale/empty)');

    // ── Add an expense transaction (today's date, matches the default
    // "this month" analytics period) — the donut should update live since
    // renderAnalytics() is called from renderFinance() on every data change,
    // with no need to close/reopen the tools modal. ──
    await page.evaluate(() => { document.getElementById('tools-modal').style.display = 'none'; });
    await page.click('.fin-fab');
    await page.waitForSelector('#tx-form-modal', { state: 'visible' });
    await page.fill('#fin-amount', '500');
    await page.fill('#fin-comment', 'donut-test-expense');
    await page.click('#fin-submit-btn');
    await page.waitForSelector('#tx-form-modal', { state: 'hidden' });
    await page.waitForTimeout(200);

    state = await donutState();
    if (state.totalEls !== 1) throw new Error(`expected exactly one .analytics-donut-total element after adding an expense, found ${state.totalEls}`);
    if (!state.totalText.includes('500')) throw new Error(`expected the donut total to include "500", got "${state.totalText}"`);
    if (!state.background.includes('conic-gradient')) throw new Error(`expected a conic-gradient background once there is expense data, got "${state.background}"`);
    console.log('[ok] populated state: real Preact h()/render() markup with the correct computed total and a conic-gradient background');

    // ── Delete the transaction: back to the empty state. This is the
    // specific regression a raw innerHTML= write next to Preact-managed
    // content could reintroduce (a stale .analytics-donut-total lingering
    // from the previous render) — render() must fully replace both
    // children together. ──
    const row = page.locator('.tx-item', { hasText: 'donut-test-expense' });
    await row.hover();
    await row.locator('.tx-swipe-delete').click();
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(200);

    state = await donutState();
    if (state.totalEls !== 0) throw new Error(`expected the .analytics-donut-total element to be gone after deleting the only transaction, found ${state.totalEls} (stale Preact content not fully replaced)`);
    if (!state.labelText) throw new Error('expected the no-data label to reappear after deleting the only transaction');
    console.log('[ok] deleting the only transaction reverts to the empty state with no stale total element left behind');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the whole flow');

    console.log('\nANALYTICS DONUT CHART TEST PASSED');
  } catch (err) {
    console.error('\nANALYTICS DONUT CHART TEST FAILED:', err.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
    server.kill();
  }
}

main();
