// "Reset all data" (Settings → Акаунт → "Скинути всі дані", js/auth.js's
// resetProfileData()) — wipes shifts/finance/debt/transactions for the
// *current* profile only and reseeds fresh defaults, without touching the
// Firebase Auth account/sign-in. Distinct from deleteAccountUser() (which
// removes the account itself) — this is the "start over, keep my login"
// path. Same stubbed-Firebase-with-no-security-rules limitation as the
// other shared-profile tests: the real cross-account enforcement (can a
// non-owner member ever reach the owner's docs) is emulator-verified in
// tests/firestore-rules.mjs; this file only proves the client-side wiring:
// confirming actually wipes+reseeds, cancelling leaves data untouched, and
// a non-owned shared profile is refused before any confirm dialog even
// shows. Run with:
//
//   node tests/reset-profile-data.mjs
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
const PORT = 8927;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
// SEED is textually substituted per-scenario, since each scenario needs the
// stubbed Firestore pre-loaded before the page's own init() runs (a
// runtime seed-then-reload doesn't work — ES modules re-evaluate from
// scratch on navigation, wiping this stub's in-memory _docs Map).
const STUB_FIRESTORE_TEMPLATE = `
const _docs = new Map(Object.entries(__SEED__));
window.__stubDocs = _docs;
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
    if (v && v.__isArrayUnion) { const arr = Array.isArray(merged[k]) ? merged[k].slice() : []; v.items.forEach((item) => { if (!arr.includes(item)) arr.push(item); }); merged[k] = arr; }
    else if (v && v.__isArrayRemove) { const arr = Array.isArray(merged[k]) ? merged[k].slice() : []; merged[k] = arr.filter((item) => !v.items.includes(item)); }
    else { merged[k] = v; }
  }
  _docs.set(ref.path, merged);
}
export function arrayUnion(...items){ return { __isArrayUnion: true, items }; }
export function arrayRemove(...items){ return { __isArrayRemove: true, items }; }
`;
function stubFirestore(seed) {
  return STUB_FIRESTORE_TEMPLATE.replace('__SEED__', JSON.stringify(seed));
}
function stubAuth(uid) {
  return `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:${JSON.stringify(uid)}, email:${JSON.stringify(uid)}+'@example.com'})); return ()=>{}; }
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
}
const STUB_MESSAGING = `
export function getMessaging(){ return {}; }
export async function getToken(){ return 'fake-token'; }
export async function deleteToken(){ return true; }
export function onMessage(){ return () => {}; }
export async function isSupported(){ return true; }
`;

async function newPage(browser, uid, seed, initScript) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, serviceWorkers: 'block' });
  const page = await context.newPage();
  const pageErrors = [];
  page.on('pageerror', (err) => pageErrors.push(err.message));
  if (initScript) await page.addInitScript(initScript);
  await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
  await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
  await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: stubFirestore(seed) }));
  await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: stubAuth(uid) }));
  await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));
  await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
  await page.waitForTimeout(300);
  return { context, page, pageErrors };
}

async function openReset(page) {
  await page.click('#btn-settings');
  await page.waitForTimeout(300);
  await page.click('[data-action="reset-profile-data"]');
  await page.waitForTimeout(200);
}

async function main() {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));
  const browser = await chromium.launch(CHROMIUM_PATH ? { executablePath: CHROMIUM_PATH } : {});
  try {
    // ── Confirming wipes the profile's data and reseeds fresh defaults ──
    {
      const seed = {
        'users/reset-uid/max_tracker/profiles_meta': { list: [{ id: 'default', name: 'Я' }], updatedAt: Date.now() },
        'users/reset-uid/max_tracker/finance': { wallets: [{ id: 'w1', name: 'Особистий гаманець', color: '#111', icon: 'wallet', currency: 'UAH' }], categories: { income: ['Дохід'], expense: ['Витрата'] }, updatedAt: Date.now() },
        'users/reset-uid/max_tracker/finance/transactions/tx1': { id: 'tx1', type: 'expense', amount: 50, currency: 'UAH', walletId: 'w1', category: 'Витрата', date: '2026-07-18', comment: 'test tx' },
      };
      const { context, page, pageErrors } = await newPage(browser, 'reset-uid', seed);

      const preResetFinance = await page.evaluate(() => window.__stubDocs.get('users/reset-uid/max_tracker/finance'));
      if (!preResetFinance || !preResetFinance.wallets.some((w) => w.name === 'Особистий гаманець')) {
        throw new Error(`expected the seeded custom wallet to be loaded before reset, got: ${JSON.stringify(preResetFinance)}`);
      }

      await openReset(page);
      await page.waitForSelector('#ui-dialog', { state: 'visible' });
      const dlgText = await page.locator('#ui-dialog').textContent();
      if (!/скинути|reset/i.test(dlgText)) throw new Error(`expected a reset confirm dialog, got: "${dlgText}"`);
      await page.click('#ui-dlg-ok');
      await page.waitForTimeout(800);

      const toastText = await page.locator('#toast').textContent();
      if (!toastText || !toastText.trim()) throw new Error('expected a confirmation toast after reset');
      console.log(`[ok] confirming reset shows a completion toast: "${toastText.trim()}"`);

      const financeDoc = await page.evaluate(() => window.__stubDocs.get('users/reset-uid/max_tracker/finance'));
      if (!financeDoc || !Array.isArray(financeDoc.wallets) || financeDoc.wallets.some((w) => w.name === 'Особистий гаманець')) {
        throw new Error(`expected the finance doc to be reseeded with default wallets, not the old custom one, got: ${JSON.stringify(financeDoc)}`);
      }
      if (financeDoc.wallets.length !== 2) throw new Error(`expected the 2 default wallets after reset, got ${financeDoc.wallets.length}`);
      console.log('[ok] finance doc reseeded with default wallets, custom wallet gone');

      const txDoc = await page.evaluate(() => window.__stubDocs.get('users/reset-uid/max_tracker/finance/transactions/tx1'));
      if (txDoc !== undefined) throw new Error('expected the transaction doc to be deleted by the reset');
      console.log('[ok] transaction doc deleted by the reset');

      if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
      await context.close();
    }

    // ── Cancelling the confirm dialog leaves data untouched ──
    {
      const seed = {
        'users/cancel-uid/max_tracker/profiles_meta': { list: [{ id: 'default', name: 'Я' }], updatedAt: Date.now() },
        'users/cancel-uid/max_tracker/finance': { wallets: [{ id: 'w1', name: 'Особистий гаманець', color: '#111', icon: 'wallet', currency: 'UAH' }], categories: {}, updatedAt: Date.now() },
      };
      const { context, page, pageErrors } = await newPage(browser, 'cancel-uid', seed);
      await openReset(page);
      await page.waitForSelector('#ui-dialog', { state: 'visible' });
      await page.click('#ui-dlg-cancel');
      await page.waitForTimeout(300);

      const financeDoc = await page.evaluate(() => window.__stubDocs.get('users/cancel-uid/max_tracker/finance'));
      if (!financeDoc || !financeDoc.wallets.some((w) => w.name === 'Особистий гаманець')) {
        throw new Error(`expected the custom wallet to survive a cancelled reset, got: ${JSON.stringify(financeDoc)}`);
      }
      console.log('[ok] cancelling the confirm dialog leaves the profile\'s data untouched');

      if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
      await context.close();
    }

    // ── A shared profile this account doesn't own is refused before any confirm dialog ──
    {
      const seed = {
        'users/member-uid/max_tracker/profiles_meta': { list: [{ id: 'default', name: 'Мій' }, { id: 'sharedP', name: 'Спільний', kind: 'shared', ownerUid: 'owner-uid' }], updatedAt: Date.now() },
        'users/member-uid/max_tracker/finance': { wallets: [], categories: {}, updatedAt: Date.now() },
        'users/owner-uid/max_tracker/finance@sharedP': { wallets: [{ id: 'w1', name: 'Спільний гаманець', color: '#111', icon: 'wallet', currency: 'UAH' }], categories: {}, updatedAt: Date.now() },
        'users/owner-uid/max_tracker/shared_members@sharedP': { members: ['owner-uid', 'member-uid'], roles: {}, updatedAt: Date.now() },
      };
      const initScript = () => { localStorage.setItem('mx_activeProfile_member-uid', 'owner-uid|sharedP'); };
      const { context, page, pageErrors } = await newPage(browser, 'member-uid', seed, initScript);
      await openReset(page);

      const dialogVisible = await page.locator('#ui-dialog').isVisible().catch(() => false);
      if (dialogVisible) throw new Error('expected no confirm dialog for a shared profile this account does not own');
      const toastText = await page.locator('#toast').textContent();
      if (!toastText || !toastText.trim()) throw new Error('expected a "not your profile" toast');
      console.log(`[ok] resetting a non-owned shared profile is refused with a toast, no confirm dialog: "${toastText.trim()}"`);

      const ownerFinanceDoc = await page.evaluate(() => window.__stubDocs.get('users/owner-uid/max_tracker/finance@sharedP'));
      if (!ownerFinanceDoc || !ownerFinanceDoc.wallets.some((w) => w.name === 'Спільний гаманець')) {
        throw new Error(`expected the owner's shared-profile data to be completely untouched, got: ${JSON.stringify(ownerFinanceDoc)}`);
      }
      console.log('[ok] the shared profile owner\'s data is completely untouched');

      if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
      await context.close();
    }
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nRESET PROFILE DATA TEST PASSED');
}

main().catch((err) => {
  console.error('\nRESET PROFILE DATA TEST FAILED:', err.message);
  process.exitCode = 1;
});
