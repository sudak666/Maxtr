// Cloud backups UI (js/backups.js): lists the active own profile's backups,
// "back up now"/restore call the backups callable (stubbed via page.route;
// server logic is covered by tests/functions-backup.mjs), reset takes a
// pre_reset safety backup first, a shared profile is refused.
//
//   node tests/backups-ui.mjs
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
const PORT = 8931;
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
let _ignoreUndefinedProperties = false;
function _assertNoUndefinedFields(data, path){
  if (data === undefined) throw new Error('Function setDoc() called with invalid data. Unsupported field value: undefined (found in field ' + JSON.stringify(path || '(root)') + ')');
  if (data === null || typeof data !== 'object') return;
  if (Array.isArray(data)) { data.forEach((v, i) => _assertNoUndefinedFields(v, (path ? path + '.' : '') + i)); return; }
  for (const k of Object.keys(data)) _assertNoUndefinedFields(data[k], (path ? path + '.' : '') + k);
}
window.__stubDocs = _docs;
export function getFirestore(){ return {}; }
export function initializeFirestore(app, settings){ _ignoreUndefinedProperties = !!(settings && settings.ignoreUndefinedProperties); return {}; }
export function doc(parent, ...rest){ if (parent && parent.path !== undefined) return { path: parent.path + '/' + rest[0] }; return { path: rest.join('/') }; }
export function collection(parent, name){ const base = parent && parent.path !== undefined ? parent.path : ''; return { path: (base ? base + '/' : '') + name }; }
export async function getDoc(ref){ const d = _docs.get(ref.path); return { exists: () => d !== undefined, data: () => d }; }
export async function setDoc(ref, data){ if (!_ignoreUndefinedProperties) _assertNoUndefinedFields(data); _docs.set(ref.path, data); }
export async function deleteDoc(ref){ _docs.delete(ref.path); }
export async function getDocs(ref){ const prefix = ref.path + '/'; const items = []; for (const [k, v] of _docs) { if (k.startsWith(prefix) && !k.slice(prefix.length).includes('/')) items.push({ id: k.slice(prefix.length), data: () => v }); } return { docs: items, forEach(fn){ items.forEach(fn); }, empty: items.length === 0, size: items.length }; }
export function writeBatch(){ const ops = []; return { set(ref, data){ if (!_ignoreUndefinedProperties) _assertNoUndefinedFields(data); ops.push(() => _docs.set(ref.path, data)); }, delete(ref){ ops.push(() => _docs.delete(ref.path)); }, async commit(){ ops.forEach((fn) => fn()); } }; }
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:${JSON.stringify(uid)}, email:${JSON.stringify(uid)}+'@example.com', getIdToken: async () => 'test-id-token'})); return ()=>{}; }
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
  await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => typeof window.finishOnboarding === 'function');
  await page.evaluate(() => window.finishOnboarding());
  await page.waitForTimeout(300);
  return { context, page, pageErrors };
}

const FN_URL = 'https://us-central1-maxtr-c238f.cloudfunctions.net/backups';

async function routeBackups(page, calls) {
  await page.route(FN_URL, async (route) => {
    const req = route.request();
    const payload = JSON.parse(req.postData() || '{}').data || {};
    calls.push({ payload, auth: req.headers()['authorization'] });
    const result = payload.action === 'restore' ? { profileId: 'default', txCount: 1 } : { status: 'created', id: 'default_2000' };
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ result }) });
  });
}

async function openBackups(page) {
  await page.click('#btn-settings');
  await page.waitForTimeout(300);
  await page.click('[data-action="open-backups-manager"]');
  await page.waitForTimeout(300);
}

async function main() {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));
  const browser = await chromium.launch(CHROMIUM_PATH ? { executablePath: CHROMIUM_PATH } : {});
  try {
    // ── List, back up now, restore ──
    {
      const seed = {
        'users/bk-uid/max_tracker/profiles_meta': { list: [{ id: 'default', name: 'Я' }, { id: 'p2', name: 'Інший' }], updatedAt: Date.now() },
        'users/bk-uid/max_tracker/finance': { wallets: [{ id: 'w1', name: 'Картка', color: '#111', icon: 'wallet', currency: 'UAH' }], categories: { income: ['Дохід'], expense: ['Витрата'] }, updatedAt: Date.now() },
        'users/bk-uid/backups/default_1000': { profileId: 'default', createdAt: Date.UTC(2026, 8, 27, 1, 30), reason: 'daily', txCount: 3 },
        'users/bk-uid/backups/default_900': { profileId: 'default', createdAt: Date.UTC(2026, 8, 26, 1, 30), reason: 'pre_reset', txCount: 5 },
        'users/bk-uid/backups/p2_1000': { profileId: 'p2', createdAt: Date.UTC(2026, 8, 27, 1, 30), reason: 'daily', txCount: 9 },
      };
      const { context, page, pageErrors } = await newPage(browser, 'bk-uid', seed);
      const calls = [];
      await routeBackups(page, calls);
      await openBackups(page);

      if (!(await page.locator('#backups-modal').isVisible())) throw new Error('expected the backups modal to open');
      const rows = page.locator('#backups-list [data-action="restore-backup"]');
      const n = await rows.count();
      if (n !== 2) throw new Error(`expected 2 backups of the active profile (other profile filtered out), got ${n}`);
      const firstId = await rows.first().getAttribute('data-id');
      if (firstId !== 'default_1000') throw new Error(`expected newest first, got ${firstId}`);
      const listText = await page.locator('#backups-list').textContent();
      if (!/3 операції/.test(listText) || !/Перед скиданням/.test(listText)) throw new Error(`unexpected list text: ${listText}`);
      console.log('[ok] lists only the active profile backups, newest first, with reason + tx count');

      await page.click('[data-action="create-backup-now"]');
      await page.waitForTimeout(500);
      const create = calls.find((c) => c.payload.action === 'create');
      if (!create || create.payload.reason !== 'manual' || create.payload.profileId !== 'default') throw new Error(`bad create call: ${JSON.stringify(calls)}`);
      if (create.auth !== 'Bearer test-id-token') throw new Error(`expected the ID token as bearer, got ${create.auth}`);
      const toast1 = (await page.locator('#toast').textContent()) || '';
      if (!/Копію створено/.test(toast1)) throw new Error(`expected a "created" toast, got "${toast1}"`);
      console.log('[ok] "back up now" calls the callable with the ID token and toasts');

      await rows.nth(1).click();
      await page.waitForSelector('#ui-dialog', { state: 'visible' });
      const dlg = (await page.locator('#ui-dialog').textContent()) || '';
      if (!/Відновити копію/.test(dlg) || /\{date\}/.test(dlg)) throw new Error(`unexpected restore dialog: ${dlg}`);
      await page.click('#ui-dlg-ok');
      await page.waitForTimeout(800);
      const restore = calls.find((c) => c.payload.action === 'restore');
      if (!restore || restore.payload.backupId !== 'default_900') throw new Error(`bad restore call: ${JSON.stringify(calls)}`);
      const toast2 = (await page.locator('#toast').textContent()) || '';
      if (!/Дані відновлено/.test(toast2)) throw new Error(`expected a "restored" toast, got "${toast2}"`);
      console.log('[ok] restore asks for confirmation, calls the callable with the backup id, reloads and toasts');

      if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
      await context.close();
    }

    // ── Reset takes a pre_reset safety backup before wiping ──
    {
      const seed = {
        'users/rs-uid/max_tracker/profiles_meta': { list: [{ id: 'default', name: 'Я' }], updatedAt: Date.now() },
        'users/rs-uid/max_tracker/finance': { wallets: [], categories: {}, updatedAt: Date.now() },
      };
      const { context, page, pageErrors } = await newPage(browser, 'rs-uid', seed);
      const calls = [];
      let financeAtBackup = 'unset';
      await page.route(FN_URL, async (route) => {
        calls.push(JSON.parse(route.request().postData() || '{}').data);
        financeAtBackup = await page.evaluate(() => !!window.__stubDocs.get('users/rs-uid/max_tracker/finance')).catch(() => 'err');
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ result: { status: 'created' } }) });
      });
      await page.click('#btn-settings');
      await page.waitForTimeout(300);
      await page.click('[data-action="reset-profile-data"]');
      await page.waitForSelector('#ui-dialog', { state: 'visible' });
      await page.click('#ui-dlg-ok');
      await page.waitForTimeout(900);
      if (!calls.some((c) => c.action === 'create' && c.reason === 'pre_reset')) throw new Error(`expected a pre_reset backup call, got ${JSON.stringify(calls)}`);
      if (financeAtBackup !== true) throw new Error('expected the safety backup to run before the data was deleted');
      console.log('[ok] reset takes a pre_reset backup before deleting anything');
      if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
      await context.close();
    }

    // ── Shared profile: refused with a toast ──
    {
      const seed = {
        'users/member-uid/max_tracker/profiles_meta': { list: [{ id: 'default', name: 'Мій' }, { id: 'sharedP', name: 'Спільний', kind: 'shared', ownerUid: 'owner-uid' }], updatedAt: Date.now() },
        'users/member-uid/max_tracker/finance': { wallets: [], categories: {}, updatedAt: Date.now() },
        'users/owner-uid/max_tracker/finance@sharedP': { wallets: [], categories: {}, updatedAt: Date.now() },
        'users/owner-uid/max_tracker/shared_members@sharedP': { members: ['owner-uid', 'member-uid'], roles: {}, updatedAt: Date.now() },
      };
      const initScript = () => { localStorage.setItem('mx_activeProfile_member-uid', 'owner-uid|sharedP'); };
      const { context, page, pageErrors } = await newPage(browser, 'member-uid', seed, initScript);
      await openBackups(page);
      if (await page.locator('#backups-modal').isVisible()) throw new Error('expected no backups modal in a shared profile');
      const toast = (await page.locator('#toast').textContent()) || '';
      if (!/власник/.test(toast)) throw new Error(`expected an owner-only toast, got "${toast}"`);
      console.log('[ok] a shared profile is refused with a toast');
      if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
      await context.close();
    }
  } finally {
    await browser.close();
    server.kill();
  }
  console.log('\nBACKUPS UI TEST PASSED');
}

main().catch((err) => {
  console.error('\nBACKUPS UI TEST FAILED:', err.message);
  process.exitCode = 1;
});
