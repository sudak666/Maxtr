// E2E test for the Finance tab's swipe-to-delete gesture (setupTxSwipe(),
// js/analytics-csv.js) — dispatches synthetic PointerEvents (Playwright has
// no built-in touch-drag API) directly on a .tx-item to simulate a real
// horizontal swipe. Same stubbed-Firebase Playwright recipe as the other
// tests/*.mjs. Run with:
//
//   node tests/tx-swipe-delete.mjs
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
const PORT = 8889;
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'swipe-uid', email:'swipe@example.com'})); return ()=>{}; }
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

    // Dispatches a synthetic pointerdown -> pointermove(s) -> pointerup
    // drag, entirely leftward, directly on the given .tx-item's own
    // element (setupTxSwipe()'s listeners are per-row, not
    // document-delegated, unlike pull-to-refresh's touch handling).
    async function swipeLeft(txId, totalDx, steps) {
      await page.evaluate(({ txId, totalDx, steps }) => {
        const el = document.querySelector(`.tx-item[data-tx-id="${txId}"]`);
        const rect = el.getBoundingClientRect();
        const startX = rect.x + rect.width - 20, y = rect.y + rect.height / 2;
        const fire = (type, x) => el.dispatchEvent(new PointerEvent(type, {
          bubbles: true, cancelable: true, clientX: x, clientY: y, pointerId: 7, pointerType: 'touch',
        }));
        fire('pointerdown', startX);
        for (let i = 1; i <= steps; i++) fire('pointermove', startX - (totalDx * i) / steps);
        fire('pointerup', startX - totalDx);
      }, { txId, totalDx, steps });
    }

    async function createTx(marker) {
      await page.click('.fin-fab');
      await page.waitForSelector('#tx-form-modal', { state: 'visible' });
      await page.fill('#fin-amount', '42');
      await page.fill('#fin-comment', marker);
      await page.click('#fin-submit-btn');
      await page.waitForSelector('#tx-form-modal', { state: 'hidden' });
      await page.waitForTimeout(150);
      const row = page.locator('.tx-item', { hasText: marker });
      if ((await row.count()) !== 1) throw new Error(`expected exactly 1 row after creating "${marker}", found ${await row.count()}`);
      return row.getAttribute('data-tx-id');
    }

    // ── A short swipe (below the 30px open threshold) snaps back — no
    // .swipe-open class, delete button stays behind the row. ──
    const shortId = await createTx(`swipe-short-${Date.now()}`);
    await swipeLeft(shortId, 12, 3);
    await page.waitForTimeout(300);
    const shortOpen = await page.evaluate((id) => document.querySelector(`.tx-item[data-tx-id="${id}"]`).classList.contains('swipe-open'), shortId);
    if (shortOpen) throw new Error('expected a short swipe (below threshold) to snap back, not open');
    console.log('[ok] a short swipe (below threshold) snaps back without revealing delete');

    // ── A long swipe (past the threshold) opens the row and reveals a
    // real, clickable delete button. ──
    const longId = await createTx(`swipe-long-${Date.now()}`);
    await swipeLeft(longId, 45, 6);
    await page.waitForTimeout(300);
    const longRow = page.locator(`.tx-item[data-tx-id="${longId}"]`);
    if (!(await longRow.evaluate((el) => el.classList.contains('swipe-open')))) {
      throw new Error('expected a long swipe (past threshold) to open the row (add .swipe-open)');
    }
    console.log('[ok] a long swipe (past threshold) reveals the delete button');

    // Tapping the still-open row again just closes it — does not open the
    // edit modal (a touchend from the swipe itself can register as a click
    // on the same element, so this guards against an accidental edit-open
    // right after swiping).
    await longRow.click();
    await page.waitForTimeout(150);
    if (await page.isVisible('#tx-form-modal')) throw new Error('tapping an open swiped row should close it, not open edit');
    if (await longRow.evaluate((el) => el.classList.contains('swipe-open'))) throw new Error('expected the first tap on an open row to close the swipe');
    console.log('[ok] tapping an already-open swiped row closes it instead of opening edit');

    // Re-open it and confirm tapping elsewhere (a different row's area)
    // auto-closes it.
    await swipeLeft(longId, 45, 6);
    await page.waitForTimeout(200);
    await page.click('.topbar-brand');
    await page.waitForTimeout(150);
    if (await longRow.evaluate((el) => el.classList.contains('swipe-open'))) throw new Error('expected tapping outside the row to close the open swipe');
    console.log('[ok] tapping outside an open swiped row closes it');

    // Re-open and actually delete via the now-revealed button.
    await swipeLeft(longId, 45, 6);
    await page.waitForTimeout(200);
    await longRow.locator('.tx-swipe-delete').click();
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(200);
    if ((await longRow.count()) !== 0) throw new Error('expected the row to be gone after deleting via the swipe-revealed button');
    console.log('[ok] deleting via the swipe-revealed button removes the row');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the swipe-to-delete flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nTX SWIPE-TO-DELETE TEST PASSED');
}

main().catch((err) => {
  console.error('\nTX SWIPE-TO-DELETE TEST FAILED:', err.message);
  process.exitCode = 1;
});
