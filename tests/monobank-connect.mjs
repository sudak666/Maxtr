// Monobank integration UI test (js/monobank.js). Same stubbed-Firebase
// Playwright recipe as the other e2e tests, plus a page.route() stub for
// this app's own "/api/monobank" same-origin proxy endpoint (there's no
// Functions emulator in this repo's test setup — see CLAUDE.md's Commands
// section — so the network boundary is stubbed at the browser level here,
// same principle as stubbing the Firebase SDK's gstatic module imports).
// Covers: connecting creates one wallet per Monobank account/jar and shows
// the connected view; syncing imports non-hold transactions and skips
// hold:true ones; a second sync with the same data imports nothing new
// (dedup by Monobank's own id); disconnecting hides the connected view
// without deleting the already-imported wallets/transactions. The real
// Cloud Function (functions/index.js's monobankProxy) — ID-token
// verification, the actual api.monobank.ua fetch — isn't exercised here;
// functions/lib/monobank.js's URL-building/window-validation logic is
// covered separately by tests/monobank-proxy.mjs (plain node, no browser).
// Run with:
//
//   node tests/monobank-connect.mjs
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
const PORT = 8930;
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
export async function updateDoc(ref, data){ const existing = _docs.get(ref.path) || {}; const merged = { ...existing }; for (const k in data) merged[k] = data[k]; _docs.set(ref.path, merged); }
export function arrayUnion(...items){ return { __isArrayUnion: true, items }; }
export function arrayRemove(...items){ return { __isArrayRemove: true, items }; }
`;
// getIdToken() is the one addition over every other test's stub auth user —
// js/monobank.js's monobankApiRequest() calls it to attach a Bearer token
// to the proxy request.
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'mono-uid', email:'mono@example.com', getIdToken: async () => 'fake-id-token'})); return ()=>{}; }
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

const CLIENT_INFO_RESPONSE = {
  name: 'Тест Клієнт',
  accounts: [{ id: 'acc1', type: 'black', currencyCode: 980, maskedPan: ['537541******1234'], balance: 100000 }],
  jars: [{ id: 'jar1', title: 'На відпустку', currencyCode: 980, balance: 50000 }],
};
// One hold (must be skipped), one settled expense, one settled income —
// ids are namespaced by account so the two linked accounts/jars each
// produce genuinely distinct transactions (real Monobank entry ids are
// globally unique regardless of which account they belong to; reusing the
// same ids across accounts here would incorrectly exercise the dedup path
// on the second account instead of a fresh import).
function statementResponseFor(accountId) {
  return [
    { id: `${accountId}-hold-1`, time: 1752000000, amount: -5000, description: 'Ще не завершено', hold: true },
    { id: `${accountId}-exp-1`, time: 1752000100, amount: -15000, description: 'АТБ маркет' },
    { id: `${accountId}-inc-1`, time: 1752000200, amount: 300000, description: 'Зарахування' },
  ];
}

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

    let statementCallCount = 0;
    await page.route('**/api/monobank*', (route) => {
      const url = new URL(route.request().url());
      const action = url.searchParams.get('action');
      const authHeader = route.request().headers()['authorization'];
      const tokenHeader = route.request().headers()['x-monobank-token'];
      if (!authHeader || !tokenHeader) {
        route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ error: 'missing headers in test stub' }) });
        return;
      }
      if (action === 'client-info') {
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(CLIENT_INFO_RESPONSE) });
        return;
      }
      if (action === 'statement') {
        statementCallCount++;
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(statementResponseFor(url.searchParams.get('account'))) });
        return;
      }
      route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ error: 'unknown action in test stub' }) });
    });

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    await page.waitForTimeout(300);
    // Shrink the real 61s Monobank rate-limit pacing gap so this test
    // doesn't take minutes — see setMonobankSyncGapMsForTesting()'s own
    // comment in js/monobank.js.
    await page.evaluate(() => { window.__RYTM_TEST_HOOKS__.setMonobankSyncGapMsForTesting(30); });

    await page.click('#btn-settings');
    await page.waitForTimeout(300);
    await page.click('[data-action="open-monobank-manager"]');
    await page.waitForSelector('#monobank-modal', { state: 'visible' });

    const formVisibleBefore = await page.locator('#monobank-form').isVisible();
    if (!formVisibleBefore) throw new Error('expected the not-connected form to be visible before connecting');

    // Cold-init already seeded the 2 default wallets (Картка/Готівка,
    // js/core.js's DEFAULT_WALLETS) before this — baseline it rather than
    // assuming an absolute count, so this assertion doesn't silently break
    // if the app's default wallet seed ever changes.
    const walletCountBeforeConnect = await page.evaluate(() => window.__RYTM_TEST_HOOKS__.AppState.wallets.length);

    await page.fill('#monobank-token-input', 'fake-personal-token');
    await page.click('[data-action="connect-monobank"]');
    await page.waitForSelector('#monobank-connected', { state: 'visible', timeout: 5000 });
    console.log('[ok] connecting shows the connected view');

    const clientName = await page.locator('#monobank-client-name').textContent();
    if (!clientName.includes('Тест Клієнт')) throw new Error(`expected the client name from client-info, got: "${clientName}"`);
    console.log('[ok] connected view shows the client name from Monobank\'s client-info response');

    const accountRows = await page.locator('#monobank-accounts-list .mgr-row').count();
    if (accountRows !== 2) throw new Error(`expected 2 linked accounts (1 card + 1 jar), got ${accountRows}`);
    console.log('[ok] connecting links both the account and the jar (2 rows)');

    const walletCount = await page.evaluate(() => window.__RYTM_TEST_HOOKS__.AppState.wallets.length);
    if (walletCount !== walletCountBeforeConnect + 2) throw new Error(`expected 2 newly auto-created wallets on top of the ${walletCountBeforeConnect} pre-existing ones (1 per Monobank account/jar), got ${walletCount} total`);
    console.log(`[ok] connecting auto-created 2 new wallets, one per Monobank account/jar (${walletCountBeforeConnect} -> ${walletCount})`);

    // ── First sync: imports the 2 non-hold entries, skips the hold one ──
    await page.click('[data-action="sync-monobank"]');
    await page.waitForFunction(() => !document.getElementById('monobank-sync-btn').disabled, null, { timeout: 15000 });
    await page.waitForTimeout(200);
    const txCountAfterFirstSync = await page.evaluate(() => window.__RYTM_TEST_HOOKS__.AppState.transactions.length);
    // 2 accounts each returning the same 3-row stub (1 hold + 2 real) = 4 new tx total.
    if (txCountAfterFirstSync !== 4) throw new Error(`expected 4 imported transactions (2 accounts x 2 non-hold rows each), got ${txCountAfterFirstSync}`);
    console.log('[ok] first sync imports non-hold transactions from both linked accounts, skips the hold one');

    const hasHoldTx = await page.evaluate(() => window.__RYTM_TEST_HOOKS__.AppState.transactions.some((t) => (t.monobankId || '').endsWith('-hold-1')));
    if (hasHoldTx) throw new Error('expected the hold:true entries to never be imported');
    console.log('[ok] the hold:true entries were never imported');

    // ── Second sync with the same statement data: dedup means no new rows ──
    // Waits past the 1-second Math.floor(Date.now()/1000) granularity
    // lastSyncAt uses, so this sync's [from,to) window is non-empty and
    // actually calls the statement endpoint again — otherwise a same-second
    // second sync would (correctly) skip the call entirely for having
    // nothing new to ask about, which would prove nothing about dedup.
    await page.waitForTimeout(1100);
    const statementCallsBeforeSecondSync = statementCallCount;
    await page.click('[data-action="sync-monobank"]');
    await page.waitForFunction(() => !document.getElementById('monobank-sync-btn').disabled, null, { timeout: 15000 });
    await page.waitForTimeout(200);
    const txCountAfterSecondSync = await page.evaluate(() => window.__RYTM_TEST_HOOKS__.AppState.transactions.length);
    if (txCountAfterSecondSync !== txCountAfterFirstSync) throw new Error(`expected re-syncing the same data to import nothing new (dedup by Monobank id), went from ${txCountAfterFirstSync} to ${txCountAfterSecondSync}`);
    if (statementCallCount <= statementCallsBeforeSecondSync) throw new Error('expected the second sync to actually call the statement endpoint again (proving dedup, not "it never asked")');
    console.log('[ok] re-syncing the same date range imports nothing new (dedup by Monobank\'s own id)');

    // ── Disconnect hides the connected view, doesn't delete data ──
    await page.click('[data-action="disconnect-monobank"]');
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.click('#ui-dlg-ok');
    await page.waitForTimeout(300);
    const formVisibleAfterDisconnect = await page.locator('#monobank-form').isVisible();
    if (!formVisibleAfterDisconnect) throw new Error('expected the not-connected form to reappear after disconnecting');
    const walletCountAfterDisconnect = await page.evaluate(() => window.__RYTM_TEST_HOOKS__.AppState.wallets.length);
    if (walletCountAfterDisconnect !== walletCount) throw new Error('expected disconnecting to leave the already-created wallets alone');
    const txCountAfterDisconnect = await page.evaluate(() => window.__RYTM_TEST_HOOKS__.AppState.transactions.length);
    if (txCountAfterDisconnect !== txCountAfterFirstSync) throw new Error('expected disconnecting to leave already-imported transactions alone');
    console.log('[ok] disconnecting hides the connected view without deleting wallets/transactions');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the whole flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nMONOBANK CONNECT TEST PASSED');
}

main().catch((err) => {
  console.error('\nMONOBANK CONNECT TEST FAILED:', err.message);
  process.exitCode = 1;
});
