// E2E test for pull-to-refresh (setupPullToRefresh(), js/app-init.js) —
// dispatches synthetic TouchEvents (Playwright has no built-in touch-drag
// API, only a single tap) to simulate a real drag-down gesture from the
// top of the page. Same stubbed-Firebase Playwright recipe as the other
// tests/*.mjs. Run with:
//
//   node tests/pull-to-refresh.mjs
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
const PORT = 8888;
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'ptr-uid', email:'ptr@example.com'})); return ()=>{}; }
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
    // hasTouch is required for TouchEvent constructors to behave like a
    // real touch-capable device inside the page.
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true });
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

    // Helper: dispatch a synthetic touchstart -> touchmove(s) -> touchend
    // drag from (x, startY) down to (x, startY+totalDy), in `steps` moves.
    async function dragDown(x, startY, totalDy, steps) {
      await page.evaluate(({ x, startY, totalDy, steps }) => {
        function touchEv(type, cx, cy) {
          const touch = new Touch({ identifier: 1, target: document.body, clientX: cx, clientY: cy, pageX: cx, pageY: cy });
          const opts = { bubbles: true, cancelable: true, composed: true };
          const list = type === 'touchend' ? [] : [touch];
          return new TouchEvent(type, { ...opts, touches: list, targetTouches: list, changedTouches: [touch] });
        }
        document.dispatchEvent(touchEv('touchstart', x, startY));
        for (let i = 1; i <= steps; i++) {
          document.dispatchEvent(touchEv('touchmove', x, startY + (totalDy * i) / steps));
        }
        document.dispatchEvent(touchEv('touchend', x, startY + totalDy));
      }, { x, startY, totalDy, steps });
    }

    // ── A short pull (below the 70px-post-damping threshold) snaps back
    // without refreshing. ──
    await dragDown(195, 100, 60, 4); // 60px raw * 0.5 damping = 30px effective, well under threshold
    await page.waitForTimeout(400);
    const toastVisibleAfterShortPull = await page.evaluate(() => document.getElementById('toast')?.classList.contains('show'));
    if (toastVisibleAfterShortPull) throw new Error('expected no refresh toast after a short (below-threshold) pull');
    const indicatorTransformAfterShortPull = await page.evaluate(() => document.getElementById('ptr-indicator')?.style.transform);
    if (!indicatorTransformAfterShortPull.includes('-60px')) throw new Error(`expected the indicator to snap back to translateY(-60px), got "${indicatorTransformAfterShortPull}"`);
    console.log('[ok] a short pull (below threshold) snaps back without triggering a refresh');

    // ── A long pull (past the threshold) triggers fbLoadNow() + the same
    // "synced" toast the topbar refresh button shows. ──
    await dragDown(195, 100, 220, 8); // 220px raw * 0.5 damping = 110px, capped at MAX_PULL=100, past THRESHOLD=70
    await page.waitForTimeout(600);
    const toastVisibleAfterLongPull = await page.evaluate(() => {
      const t = document.getElementById('toast');
      return t?.classList.contains('show') ? t.textContent : null;
    });
    if (!toastVisibleAfterLongPull) throw new Error('expected a refresh toast to appear after a long (past-threshold) pull');
    console.log(`[ok] a long pull (past threshold) triggers a refresh — toast shown: "${toastVisibleAfterLongPull}"`);

    await page.waitForTimeout(500); // let the settle-back animation finish
    const indicatorTransformAfterRefresh = await page.evaluate(() => document.getElementById('ptr-indicator')?.style.transform);
    if (!indicatorTransformAfterRefresh.includes('-60px')) throw new Error(`expected the indicator to settle back to translateY(-60px) after refreshing, got "${indicatorTransformAfterRefresh}"`);
    console.log('[ok] the indicator settles back out of view after the refresh completes');

    // ── Pulling down while NOT scrolled to the top must not trigger
    // anything (this only activates from the very top of the page). ──
    // Explicitly reset the toast's visibility state left over from the
    // previous scenario first — its own 2800ms auto-hide timer wouldn't
    // have fired yet at this point, and a stale "show" class would give a
    // false positive here regardless of whether this scenario's pull did
    // anything.
    await page.evaluate(() => document.getElementById('toast')?.classList.remove('show'));
    await page.evaluate(() => window.scrollTo(0, 200));
    await page.waitForTimeout(100);
    const scrollYAfterScroll = await page.evaluate(() => window.scrollY);
    if (scrollYAfterScroll < 50) throw new Error(`test setup problem: expected the page to actually scroll down before this check, scrollY=${scrollYAfterScroll} (page may not be tall enough at this viewport)`);
    await dragDown(195, 300, 220, 8);
    await page.waitForTimeout(300);
    const toastAfterMidScrollPull = await page.evaluate(() => document.getElementById('toast')?.classList.contains('show'));
    if (toastAfterMidScrollPull) throw new Error('expected pulling down while not scrolled to the top to do nothing');
    console.log('[ok] pulling down while scrolled away from the top does not trigger a refresh');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the pull-to-refresh flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nPULL TO REFRESH TEST PASSED');
}

main().catch((err) => {
  console.error('\nPULL TO REFRESH TEST FAILED:', err.message);
  process.exitCode = 1;
});
