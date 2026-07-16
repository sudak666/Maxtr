// Shared profiles UI test (js/color-picker.js's shareCurrentProfileUI()/
// joinSharedProfileUI(), js/firebase-sync.js's shareCurrentProfile()/
// redeemSharedInvite()). This is the client-side wiring layer only — the
// actual cross-account security enforcement (can a real other account
// read/write a shared profile's data, can they only join with a valid
// invite, etc.) is exercised against a real Firestore emulator in
// tests/firestore-rules.mjs, not here: the stubbed Firestore this file
// uses has no security rules at all, just an in-memory doc store, and
// every action here happens as the same single stubbed uid (this stub
// recipe has no way to simulate a second real account in one page load).
// What this file actually proves: the UI produces a real invite code, the
// join flow's error paths surface as user-visible feedback rather than
// silent failures or crashes, and clicking through both flows doesn't
// throw. Run with:
//
//   node tests/shared-profiles-ui.mjs
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
const PORT = 8904;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'shared-test-uid', email:'shared@example.com'})); return ()=>{}; }
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

    await page.click('#btn-settings');
    await page.waitForTimeout(300);
    await page.click('[data-action="open-profiles-manager"]');
    await page.waitForSelector('#profiles-modal', { state: 'visible' });

    // ── sharing the current (default) profile produces a real invite code ──
    await page.click('[data-action="share-current-profile"]');
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    const shareDlgText = await page.locator('#ui-dialog').textContent();
    const codeMatch = shareDlgText.match(/[A-Z2-9]{8}/);
    if (!codeMatch) throw new Error(`expected an 8-char invite code in the share dialog, got: "${shareDlgText}"`);
    const code = codeMatch[0];
    console.log(`[ok] sharing the current profile shows an invite code (${code})`);
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });

    // ── redeeming your own just-generated code is rejected, not silently applied ──
    await page.click('[data-action="join-shared-profile"]');
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.fill('#ui-dlg-input', code);
    await page.click('#ui-dlg-ok');
    await page.waitForTimeout(300);
    const toastOwn = await page.locator('#toast').textContent();
    if (!toastOwn || !toastOwn.trim()) throw new Error('expected an error toast for redeeming your own invite code');
    console.log(`[ok] redeeming your own invite code is rejected with visible feedback: "${toastOwn.trim()}"`);

    // ── redeeming a garbage code is also rejected, not a crash ──
    await page.click('[data-action="join-shared-profile"]');
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.fill('#ui-dlg-input', 'NOTAREALCODE');
    await page.click('#ui-dlg-ok');
    await page.waitForTimeout(300);
    const toastBad = await page.locator('#toast').textContent();
    if (!toastBad || !toastBad.trim()) throw new Error('expected an error toast for an unknown invite code');
    console.log(`[ok] redeeming an unknown code is rejected with visible feedback: "${toastBad.trim()}"`);

    // ── the profile list still renders the (still-local, unshared-into) default profile with no crash ──
    const profileRows = await page.locator('#profiles-list .mgr-row').count();
    if (profileRows < 1) throw new Error('expected at least the default profile row to still render');
    console.log('[ok] profiles list still renders correctly after the share/join attempts');

    if (pageErrors.length) throw new Error(`uncaught page errors during shared-profiles flow: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the whole flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nSHARED PROFILES UI TEST PASSED');
}

main().catch((err) => {
  console.error('\nSHARED PROFILES UI TEST FAILED:', err.message);
  process.exitCode = 1;
});
