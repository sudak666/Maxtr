// CSP regression test — the one class of bug the rest of the suite is blind
// to. Every other tests/*.mjs serves index.html over a plain `python3 -m
// http.server`, which sends NO Content-Security-Policy header, so inline
// event handlers (onclick="" attributes baked into rendered HTML) execute
// fine there. But the deployed site (firebase.json's `hosting.headers`)
// sends a strict CSP whose `script-src` has NO 'unsafe-inline' — under which
// inline event-handler attributes silently DO NOT FIRE. That gap shipped a
// real bug: the transaction list's delete button (an inline
// onclick="event.stopPropagation();deleteTransaction()") no-op'd on the live
// site, so tapping the red trash fell through to the row's own click→edit
// listener and opened the edit modal instead of deleting; and every
// empty-state "add first item" CTA (emptyStateHtml's inline onclick) was
// dead too, so "add purchases" did nothing on an empty list. All were
// converted to data-action wiring (real addEventListener, which CSP allows).
//
// This test reproduces the deployed environment by fulfilling the top-level
// document WITH the exact CSP header read live from firebase.json (so it
// stays in sync if the policy changes), then exercises the two reported
// flows plus asserts no CSP-violation console errors originate from the
// app's own inline handlers. Run:
//
//   node tests/csp-handlers.mjs
//
// Same stubbed-Firebase recipe as tests/e2e-modals.mjs (stub definitions
// duplicated per this repo's per-file-self-contained test convention).
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
const PORT = 8916;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

// Read the real CSP straight from firebase.json so this test tracks the
// actual deployed policy rather than a hand-copied snapshot that could drift.
function readDeployedCSP() {
  const fb = JSON.parse(fs.readFileSync(path.join(ROOT, 'firebase.json'), 'utf8'));
  for (const h of (fb.hosting?.headers || [])) {
    for (const kv of (h.headers || [])) {
      if (kv.key === 'Content-Security-Policy') return kv.value;
    }
  }
  throw new Error('no Content-Security-Policy header found in firebase.json hosting.headers');
}
const CSP = readDeployedCSP();

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
const STUB_FIRESTORE = `
const _docs = new Map();
export function getFirestore(){ return {}; }
export function initializeFirestore(){ return {}; }
export function doc(parent, ...rest){
  if (parent && parent.path !== undefined) return { path: parent.path + '/' + rest[0] };
  return { path: rest.join('/') };
}
export function collection(parent, name){
  const base = parent && parent.path !== undefined ? parent.path : '';
  return { path: (base ? base + '/' : '') + name };
}
export async function getDoc(ref){ const d = _docs.get(ref.path); return { exists: () => d !== undefined, data: () => d }; }
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
  return { set(ref, data){ ops.push(() => _docs.set(ref.path, data)); }, delete(ref){ ops.push(() => _docs.delete(ref.path)); }, async commit(){ ops.forEach((fn) => fn()); } };
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'csp-uid', email:'csp@example.com'})); return ()=>{}; }
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
  const cspViolations = [];
  const pageErrors = [];
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true });
    page.on('pageerror', (err) => pageErrors.push(err.message));
    page.on('console', (msg) => {
      const t = msg.text();
      // Only inline-script/handler CSP violations are a regression here; the
      // exchange-rate fetch hosts are legitimately allowed by the real policy
      // and unreachable in this sandbox anyway (a connect-src/network note,
      // not an inline-handler problem), so don't fail on those.
      if (/Content Security Policy/i.test(t) && /script-src|inline|unsafe-eval|Refused to execute/i.test(t)) cspViolations.push(t);
    });

    // Serve the top-level document WITH the deployed CSP header.
    await page.route(`http://localhost:${PORT}/index.html`, (route) => {
      route.fulfill({ status: 200, contentType: 'text/html', headers: { 'Content-Security-Policy': CSP }, body: fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8') });
    });
    await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
    await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    console.log(`[ok] app boots under the deployed CSP (script-src without 'unsafe-inline')`);

    // ── Finance: the swipe-revealed delete button must delete (confirm
    // dialog), not fall through to the row's click→edit. ──
    await page.click('#nav-finance');
    await page.waitForTimeout(300);
    await page.evaluate(() => document.querySelector('.fin-fab[data-action="open-new-tx-modal"]').click());
    await page.waitForSelector('#tx-form-modal', { state: 'visible' });
    await page.fill('#fin-amount', '123');
    await page.evaluate(() => document.getElementById('fin-submit-btn').click());
    await page.waitForSelector('#tx-form-modal', { state: 'hidden' });
    await page.waitForTimeout(150);
    if ((await page.locator('#tx-list-container .tx-item').count()) !== 1) throw new Error('expected one transaction after add');

    // Swipe the row open (synthetic touch drag — Playwright has no touch-drag API).
    const row = page.locator('#tx-list-container .tx-item').first();
    const box = await row.boundingBox();
    await page.evaluate(({ sx, y }) => {
      const el = document.querySelector('#tx-list-container .tx-item');
      const fire = (t, x) => el.dispatchEvent(new PointerEvent(t, { bubbles: true, cancelable: true, clientX: x, clientY: y, pointerId: 9, pointerType: 'touch' }));
      fire('pointerdown', sx);
      for (let i = 1; i <= 6; i++) fire('pointermove', sx - (60 * i) / 6);
      fire('pointerup', sx - 60);
    }, { sx: box.x + box.width - 20, y: box.y + box.height / 2 });
    await page.waitForTimeout(250);
    if (!(await row.evaluate((el) => el.classList.contains('swipe-open')))) throw new Error('row did not open on swipe');

    // Click the revealed trash. Under the old inline-onclick this no-op'd and
    // the click bubbled to the row → edit modal. Now it must run
    // deleteTransaction (a confirm dialog), NOT open the tx form as edit.
    await page.evaluate(() => document.querySelector('#tx-list-container .tx-item .tx-swipe-delete').click());
    await page.waitForTimeout(250);
    const editOpened = await page.evaluate(() => { const m = document.getElementById('tx-form-modal'); return m && getComputedStyle(m).display !== 'none'; });
    if (editOpened) throw new Error('tapping the delete button opened the edit modal — inline-handler CSP regression (delete no-op\'d, click fell through to the row edit listener)');
    const confirmShown = await page.evaluate(() => { const m = document.getElementById('ui-dialog'); return m && getComputedStyle(m).display !== 'none'; });
    if (!confirmShown) throw new Error('delete button did not open the confirm dialog — the delete-transaction handler never fired');
    console.log('[ok] tx-list delete button opens the delete-confirm (not the edit modal) under CSP');
    await page.click('#ui-dlg-ok');
    await page.waitForTimeout(300);
    if ((await page.locator('#tx-list-container .tx-item').count()) !== 0) throw new Error('transaction was not deleted after confirming');
    console.log('[ok] confirming actually deletes the transaction under CSP');

    // ── Shopping: the empty-state "add first item" CTA (data-action, was an
    // inline onclick) must work, and so must the main Додати button. ──
    await page.click('#nav-shopping');
    await page.waitForTimeout(300);
    const emptyCta = page.locator('#shopping-list-container .empty-state [data-action="focus-shopping-name"]');
    if (!(await emptyCta.count())) throw new Error('shopping empty-state CTA button missing');
    await page.evaluate(() => document.querySelector('#shopping-list-container .empty-state [data-action="focus-shopping-name"]').click());
    await page.waitForTimeout(150);
    if (!(await page.evaluate(() => document.activeElement && document.activeElement.id === 'shopping-name-input'))) throw new Error('empty-state CTA did not focus the name input — inline-handler CSP regression');
    console.log('[ok] shopping empty-state CTA works under CSP (focuses the input)');
    await page.fill('#shopping-name-input', 'Хліб');
    await page.evaluate(() => document.querySelector('button.add-btn[data-action="add-shopping-item"]').click());
    await page.waitForTimeout(250);
    if ((await page.locator('#shopping-list-container .shop-row').count()) !== 1) throw new Error('shopping add button did not add an item under CSP');
    console.log('[ok] shopping "Додати" button adds an item under CSP');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    if (cspViolations.length) throw new Error(`inline-script/handler CSP violations (app should have none): ${cspViolations.slice(0, 3).join(' | ')}`);
    console.log('[ok] no inline-script/handler CSP violations from the app');
  } finally {
    await browser.close();
    server.kill();
  }
  console.log('\nCSP HANDLERS TEST PASSED');
}

main().catch((err) => {
  console.error('\nCSP HANDLERS TEST FAILED:', err.message);
  process.exitCode = 1;
});
