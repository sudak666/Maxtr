// PWA launch-params test — verifies manifest.json's "shortcuts" entry
// (?action=new-tx) and "share_target" entry (?title=&text=) both land on
// the same result: the Finance tab's new-transaction modal opens
// pre-filled, straight from a cold page load. Same stubbed-Firebase
// Playwright recipe as tests/smoke.mjs/tests/e2e-crud.mjs (self-contained
// stubs, matching this repo's per-file-independent test style). Run with:
//
//   node tests/pwa-launch-params.mjs
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
const PORT = 8899;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

// Same stubs as tests/e2e-crud.mjs (see that file's header for why these
// are duplicated per-test rather than shared).
const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
const STUB_FIRESTORE = `
const _docs = new Map();
export function getFirestore(){ return {}; }
export function doc(parent, ...rest){
  if (parent && parent.path !== undefined) return { path: parent.path + '/' + rest[0] };
  return { path: rest.join('/') };
}
export function collection(parent, name){
  const base = parent && parent.path !== undefined ? parent.path : '';
  return { path: (base ? base + '/' : '') + name };
}
export async function getDoc(ref){
  const d = _docs.get(ref.path);
  return { exists: () => d !== undefined, data: () => d };
}
export async function setDoc(ref, data){ _docs.set(ref.path, data); }
export async function deleteDoc(ref){ _docs.delete(ref.path); }
export async function getDocs(ref){
  const prefix = ref.path + '/';
  const items = [];
  for (const [k, v] of _docs) {
    if (k.startsWith(prefix) && !k.slice(prefix.length).includes('/')) items.push({ id: k.slice(prefix.length), data: () => v });
  }
  return { docs: items, forEach(fn){ items.forEach(fn); }, empty: items.length === 0, size: items.length };
}
export function writeBatch(){
  const ops = [];
  return {
    set(ref, data){ ops.push(() => _docs.set(ref.path, data)); },
    delete(ref){ ops.push(() => _docs.delete(ref.path)); },
    async commit(){ ops.forEach((fn) => fn()); },
  };
}

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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'launch-test-uid', email:'launch@example.com'})); return ()=>{}; }
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

async function withPage(browser, run) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const pageErrors = [];
  page.on('pageerror', (err) => pageErrors.push(err.message));
  await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
  await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
  await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
  await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
  await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));
  await run(page);
  if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
  await page.close();
}

async function main() {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));

  const browser = await chromium.launch(CHROMIUM_PATH ? { executablePath: CHROMIUM_PATH } : {});
  try {
    // ── shortcuts: ?action=new-tx opens the modal empty ──
    await withPage(browser, async (page) => {
      await page.goto(`http://localhost:${PORT}/index.html?action=new-tx`, { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
      await page.waitForSelector('#tx-form-modal', { state: 'visible', timeout: 5000 });
      const url = new URL(page.url());
      if (url.search) throw new Error(`expected the query string to be stripped after handling, still "${url.search}"`);
      console.log('[ok] shortcut launch (?action=new-tx): tx-form-modal opens and the query string is cleared');
    });

    // ── share_target: ?title=&text= opens the modal pre-filled ──
    await withPage(browser, async (page) => {
      const shareUrl = `http://localhost:${PORT}/index.html?title=${encodeURIComponent('Кав’ярня')}&text=${encodeURIComponent('Кава 89.50 грн')}`;
      await page.goto(shareUrl, { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
      await page.waitForSelector('#tx-form-modal', { state: 'visible', timeout: 5000 });
      const comment = await page.locator('#fin-comment').inputValue();
      if (!comment.includes('Кав’ярня') || !comment.includes('89.50')) {
        throw new Error(`expected the comment field to contain the shared title+text, got "${comment}"`);
      }
      const amount = await page.locator('#fin-amount').inputValue();
      if (amount !== '89.50') throw new Error(`expected the amount field to be prefilled from the shared text's number, got "${amount}"`);
      console.log('[ok] share_target launch (?title=&text=): tx-form-modal opens with comment and amount prefilled from the shared text');
    });

    // ── a normal load with no launch params never opens the modal ──
    await withPage(browser, async (page) => {
      await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
      await page.waitForTimeout(500);
      const modalDisplay = await page.locator('#tx-form-modal').evaluate((el) => getComputedStyle(el).display);
      if (modalDisplay !== 'none') throw new Error(`expected tx-form-modal to stay closed on a plain load, computed display was "${modalDisplay}"`);
      console.log('[ok] a plain load (no launch params) leaves tx-form-modal closed');
    });
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nPWA LAUNCH-PARAMS TEST PASSED');
}

main().catch((err) => {
  console.error('\nPWA LAUNCH-PARAMS TEST FAILED:', err.message);
  process.exitCode = 1;
});
