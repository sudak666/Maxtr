// Granular permissions on shared profiles (editor/viewer roles) — client
// wiring test. Same stubbed-Firebase-with-no-security-rules limitation as
// tests/shared-profiles-ui.mjs: the real cross-account enforcement is
// emulator-verified in tests/firestore-rules.mjs (17 viewer-role cases),
// this file only proves the UI/client layer built on top of it: the owner
// can see members and toggle a role, and a member whose role is 'viewer'
// gets a read-only UI (hidden "add" entry points, a guarded mutating
// function) instead of silently being allowed to attempt writes
// firestore.rules would reject anyway. Run with:
//
//   node tests/shared-profile-permissions.mjs
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
const PORT = 8922;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
// SEED is textually substituted per-scenario below with a JSON object of
// path->data, since each scenario needs the stubbed Firestore pre-loaded
// before the page's own init() runs (a runtime seed-then-reload doesn't
// work here — ES modules re-evaluate from scratch on navigation, wiping
// this stub's in-memory _docs Map).
const STUB_FIRESTORE_TEMPLATE = `
const _docs = new Map(Object.entries(__SEED__));
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

async function main() {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));
  const browser = await chromium.launch(CHROMIUM_PATH ? { executablePath: CHROMIUM_PATH } : {});
  try {
    // ── Owner side: members manager lists members, defaults to editor, toggle flips role ──
    {
      const seed = {
        'users/owner-uid/max_tracker/profiles_meta': { list: [{ id: 'default', name: 'Я' }], updatedAt: Date.now() },
        'users/owner-uid/max_tracker/finance': { wallets: [], categories: {}, updatedAt: Date.now() },
        'users/owner-uid/max_tracker/shared_members': { members: ['owner-uid', 'friend1', 'friend2'], roles: { friend2: 'viewer' }, updatedAt: Date.now() },
      };
      const { context, page, pageErrors } = await newPage(browser, 'owner-uid', seed);
      await page.click('#btn-settings');
      await page.waitForTimeout(300);
      await page.click('[data-action="open-profiles-manager"]');
      await page.waitForSelector('#profiles-modal', { state: 'visible' });
      await page.click('[data-action="open-shared-members-manager"]');
      await page.waitForSelector('#shared-members-modal', { state: 'visible' });
      await page.waitForTimeout(200);

      const initialText = await page.locator('#shared-members-list').innerText();
      if (!/friend1[\s\S]*РЕДАКТОР/.test(initialText)) throw new Error(`expected friend1 to default to editor, got: ${initialText}`);
      if (!/friend2[\s\S]*ТІЛЬКИ ПЕРЕГЛЯД/.test(initialText)) throw new Error(`expected friend2 to show as viewer, got: ${initialText}`);
      console.log('[ok] members manager lists both members, friend1 defaults to editor, friend2 shows its stored viewer role');

      await page.locator('[data-action="toggle-member-role"]').first().click();
      await page.waitForTimeout(200);
      const afterToggle = await page.locator('#shared-members-list').innerText();
      if (!/friend1[\s\S]*ТІЛЬКИ ПЕРЕГЛЯД/.test(afterToggle)) throw new Error(`expected friend1 to become viewer after toggle, got: ${afterToggle}`);
      console.log('[ok] toggling a member\'s role updates the list immediately');

      if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
      await context.close();
    }

    // ── Member side: a viewer sees a read-only profile ──
    {
      const seed = {
        'users/viewer-member/max_tracker/profiles_meta': { list: [{ id: 'default', name: 'Мій' }, { id: 'sharedP', name: 'Спільний', kind: 'shared', ownerUid: 'the-owner' }], updatedAt: Date.now() },
        'users/viewer-member/max_tracker/finance': { wallets: [], categories: {}, updatedAt: Date.now() },
        'users/the-owner/max_tracker/finance@sharedP': { wallets: [{ id: 'w1', name: 'Готівка', currency: 'UAH' }], categories: {}, updatedAt: Date.now() },
        'users/the-owner/max_tracker/shared_members@sharedP': { members: ['the-owner', 'viewer-member'], roles: { 'viewer-member': 'viewer' }, updatedAt: Date.now() },
      };
      const initScript = () => { localStorage.setItem('mx_activeProfile_viewer-member', 'the-owner|sharedP'); };
      const { context, page, pageErrors } = await newPage(browser, 'viewer-member', seed, initScript);
      await page.waitForTimeout(300);

      const role = await page.evaluate(() => window.__RYTM_TEST_HOOKS__.AppState.activeProfileRole);
      if (role !== 'viewer') throw new Error(`expected activeProfileRole 'viewer', got ${role}`);
      const hasReadonlyClass = await page.evaluate(() => document.body.classList.contains('profile-readonly'));
      if (!hasReadonlyClass) throw new Error('expected body.profile-readonly to be set');
      console.log('[ok] a member with a stored viewer role loads activeProfileRole=\'viewer\' and gets the readonly body class');

      const fabVisible = await page.locator('.fin-fab.finance-fab').isVisible();
      const quickAddVisible = await page.locator('[data-action="open-new-tx-modal"]').first().isVisible();
      if (fabVisible || quickAddVisible) throw new Error(`expected every "add transaction" entry point hidden, fabVisible=${fabVisible} quickAddVisible=${quickAddVisible}`);
      console.log('[ok] every "add transaction" entry point (FAB + quick-action tile) is hidden for a viewer');

      const toastText = await page.evaluate(async () => {
        document.getElementById('fin-amount').value = '10';
        await window.__RYTM_TEST_HOOKS__.addTransaction();
        return document.getElementById('toast').textContent;
      });
      if (!/лише перегляд/.test(toastText)) throw new Error(`expected a read-only toast, got: "${toastText}"`);
      console.log('[ok] calling addTransaction() directly (simulating a bypassed UI) is still blocked with a read-only toast');

      if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
      await context.close();
    }
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nSHARED PROFILE PERMISSIONS TEST PASSED');
}

main().catch((err) => {
  console.error('\nSHARED PROFILE PERMISSIONS TEST FAILED:', err.message);
  process.exitCode = 1;
});
