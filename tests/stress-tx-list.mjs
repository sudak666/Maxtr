// Stress/perf test for the Finance tab's expanded transaction list — added
// alongside the transactions-subcollection migration and the CSS
// content-visibility virtualization on .tx-item (see CLAUDE.md's Firebase
// data model / Finance-tab sections). Neither of those is exercised by
// tests/e2e-crud.mjs (one transaction only) or tests/smoke.mjs (no data at
// all): this seeds several hundred transaction docs directly into the
// stubbed Firestore's transactions subcollection before the page loads,
// then drives the real "view all" expand flow and asserts (1) every row
// actually renders (virtualization is CSS-only — content-visibility:auto
// skips layout/paint for off-screen rows, it does not reduce the DOM node
// count, so a broken render path could still silently under-render), (2)
// the content-visibility/contain-intrinsic-size CSS is actually live on a
// rendered row (not just present unparsed in the stylesheet), and (3) nothing
// errors and the expand completes in a sane amount of time. Run with:
//
//   node tests/stress-tx-list.mjs
import fs from 'node:fs';
import path from 'node:path';
import { spawn, execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Same global-install resolution as tests/smoke.mjs — see that file's header
// comment for why this can't be a bare `import ... from 'playwright'`.
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
const PORT = 8897;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

const TX_COUNT = 400;
const UID = 'stress-test-uid';

// Same stubs as tests/smoke.mjs (kept separate per-file, matching this
// repo's per-file-independent test style), except STUB_FIRESTORE here
// pre-seeds TX_COUNT transaction docs directly into the in-memory store at
// the exact subcollection path js/firebase-sync.js's txCollection() builds
// (users/{uid}/max_tracker/finance/transactions/{txId}), so the app's own
// fbLoadNow() -> loadTransactionsFromSubcollection() picks them up on the
// very first load, same as a real heavy account would.
const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const seedEntries = Array.from({ length: TX_COUNT }, (_, i) => {
  const id = 1700000000000 + i;
  const tx = {
    id, type: i % 3 === 0 ? 'income' : 'expense', amount: 10 + i, currency: 'UAH',
    category: i % 2 === 0 ? 'Кава' : 'Зарплата', subcategory: null, tags: [],
    wallet: 'w1', targetWallet: null, targetAmount: null, targetCurrency: null,
    date: `2026-${String((i % 12) + 1).padStart(2, '0')}-01`, comment: `stress-${i}`,
  };
  return [`users/${UID}/max_tracker/finance/transactions/${id}`, tx];
});
const STUB_FIRESTORE = `
const _docs = new Map(${JSON.stringify(seedEntries)});
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
`;
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'${UID}', email:'stress@example.com'})); return ()=>{}; }
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
    const consoleErrors = [];
    page.on('pageerror', (err) => pageErrors.push(err.message));
    page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

    await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    await page.click('#nav-finance');
    await page.waitForTimeout(300);

    const countText = await page.locator('#tx-count').textContent();
    if (!countText || !countText.startsWith(String(TX_COUNT))) {
      throw new Error(`expected #tx-count to report ${TX_COUNT} records, got "${countText}"`);
    }
    console.log(`[ok] loaded ${TX_COUNT} transactions from the seeded subcollection (fbLoadNow -> loadTransactionsFromSubcollection)`);

    const collapsedCount = await page.locator('.tx-item').count();
    if (collapsedCount >= TX_COUNT) throw new Error(`expected the collapsed list to show far fewer than ${TX_COUNT} rows, got ${collapsedCount}`);
    console.log(`[ok] collapsed view shows only ${collapsedCount} rows by default, not all ${TX_COUNT}`);

    const expandStart = Date.now();
    await page.click('.tx-view-all-btn');
    await page.waitForFunction((n) => document.querySelectorAll('.tx-item').length >= n, TX_COUNT, { timeout: 15000 });
    const expandMs = Date.now() - expandStart;

    const expandedCount = await page.locator('.tx-item').count();
    if (expandedCount !== TX_COUNT) throw new Error(`expected exactly ${TX_COUNT} .tx-item rows after expanding, found ${expandedCount}`);
    console.log(`[ok] "view all" renders all ${TX_COUNT} rows (${expandMs}ms)`);

    // Confirm the content-visibility virtualization CSS is actually applied
    // to a rendered row, not just present unparsed in the stylesheet.
    const cv = await page.locator('.tx-item').first().evaluate((el) => getComputedStyle(el).contentVisibility);
    if (cv !== 'auto') throw new Error(`expected .tx-item's computed content-visibility to be "auto", got "${cv}" (browser may not support it, or the CSS rule didn't apply)`);
    console.log('[ok] content-visibility:auto is live on a rendered .tx-item row');

    // Regression guard for the flex-shrink collapse bug (see CLAUDE.md's
    // swipe-to-delete "Gotcha if you swipe-clip a row inside a flex list
    // container"): #tx-list-container is a flex column with a fixed
    // max-height + overflow-y:auto, and .tx-item carries overflow:hidden for
    // its swipe-clip — which, without an explicit flex-shrink:0, makes the
    // flex algorithm crush every row to a ~12px sliver once the expanded
    // list exceeds the container's max-height, instead of scrolling. This
    // shipped once (rows rendered as thin lines) and ALSO silently broke
    // pull-to-refresh (a non-overflowing container reads as non-scrollable,
    // so PTR fired on swipes over the list). Assert a real row keeps its
    // content height and the container genuinely overflows/scrolls.
    const listGeom = await page.evaluate(() => {
      const lc = document.getElementById('tx-list-container');
      const row = lc.querySelector('.tx-item');
      return { rowH: Math.round(row.getBoundingClientRect().height), clientH: lc.clientHeight, scrollH: lc.scrollHeight };
    });
    if (listGeom.rowH < 40) throw new Error(`.tx-item collapsed to ${listGeom.rowH}px — flex-shrink regression (expected a full-height row, ~60px)`);
    if (listGeom.scrollH <= listGeom.clientH + 1) throw new Error(`#tx-list-container did not overflow (scrollH ${listGeom.scrollH} ≤ clientH ${listGeom.clientH}) — rows were crushed to fit instead of scrolling, which also disables the pull-to-refresh nested-scroll guard`);
    console.log(`[ok] .tx-item rows keep full height (${listGeom.rowH}px) and the container scrolls (scrollH ${listGeom.scrollH} > clientH ${listGeom.clientH}) — no flex-shrink collapse`);

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    const realConsoleErrors = consoleErrors.filter((e) => !/live rates|ERR_|net::/.test(e));
    if (realConsoleErrors.length) throw new Error(`unexpected console errors: ${realConsoleErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors or unexpected console errors while rendering the large list');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nSTRESS TX LIST TEST PASSED');
}

main().catch((err) => {
  console.error('\nSTRESS TX LIST TEST FAILED:', err.message);
  process.exitCode = 1;
});
