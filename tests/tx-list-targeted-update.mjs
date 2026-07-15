// Verifies renderFinance()'s targeted-update behavior (js/analytics-csv.js)
// added alongside the transactions-subcollection migration: existing
// .tx-item DOM nodes are reused (matched by data-id) across unrelated
// re-renders instead of being torn down and rebuilt, and only genuinely
// new/removed transactions cause nodes to be created/destroyed. Proven by
// tagging each row with a custom data attribute that only survives on the
// *same* DOM node (item.innerHTML=... only replaces a .tx-item's children,
// never the element's own attributes, so this attribute is wiped iff the
// node itself was destroyed and replaced). Same stubbed-Firebase Playwright
// recipe as the other tests/*.mjs. Run with:
//
//   node tests/tx-list-targeted-update.mjs
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
const PORT = 8896;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;
const UID = 'targeted-update-uid';

// Seed 2 transactions — comfortably under TX_LIST_COLLAPSED_COUNT (3) in
// js/analytics-csv.js, so all of them stay visible throughout, *including*
// after adding one more (2 seeded + 1 new = 3, still <= the collapsed
// count) — this test isn't about the collapse/expand behavior itself
// (tests/e2e-crud.mjs and tests/stress-tx-list.mjs already cover pieces of
// that), just the DOM-node-reuse diffing.
const seedEntries = [1, 2].map((i) => {
  const id = 1800000000000 + i;
  const tx = {
    id, type: 'expense', amount: 10 * i, currency: 'UAH', category: 'Кава',
    subcategory: null, tags: [], wallet: 'w1', targetWallet: null, targetAmount: null,
    targetCurrency: null, date: '2026-07-10', comment: `seed-${i}`,
  };
  return [`users/${UID}/max_tracker/finance/transactions/${id}`, tx];
});

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'${UID}', email:'tu@example.com'})); return ()=>{}; }
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
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    await page.click('#nav-finance');
    await page.waitForTimeout(300);

    const rowCount = await page.locator('.tx-item').count();
    if (rowCount !== 2) throw new Error(`expected exactly 2 seeded rows visible, found ${rowCount}`);

    // Tag each row's outer element (not its innerHTML, which gets
    // regenerated) with a marker keyed by its data-id.
    await page.evaluate(() => {
      document.querySelectorAll('.tx-item').forEach((el) => { el.dataset.testMarker = 'tagged-' + el.dataset.txId; });
    });
    const markersBefore = await page.evaluate(() => Array.from(document.querySelectorAll('.tx-item')).map((el) => el.dataset.txId + ':' + el.dataset.testMarker));

    // ── Unrelated re-render: click the already-active "all" filter chip,
    // which calls setTxFilter('all') -> renderFinance() without touching
    // any transaction. ──
    await page.click('.filter-chip[data-filter="all"]');
    await page.waitForTimeout(150);

    const markersAfter = await page.evaluate(() => Array.from(document.querySelectorAll('.tx-item')).map((el) => el.dataset.txId + ':' + el.dataset.testMarker));
    if (JSON.stringify(markersBefore) !== JSON.stringify(markersAfter)) {
      throw new Error(`expected the same 3 DOM nodes (with markers intact) after an unrelated re-render, before=${JSON.stringify(markersBefore)} after=${JSON.stringify(markersAfter)}`);
    }
    console.log('[ok] an unrelated renderFinance() call reuses the same 2 .tx-item DOM nodes (markers survived)');

    // ── Add a transaction: the 2 existing marked nodes must stay exactly
    // as they were; only a new 3rd node should appear (2+1=3 stays within
    // TX_LIST_COLLAPSED_COUNT, so nothing gets pushed out of the collapsed
    // view). ──
    await page.click('.fin-fab');
    await page.waitForSelector('#tx-form-modal', { state: 'visible' });
    await page.fill('#fin-amount', '77.77');
    const marker = `targeted-update-new-${Date.now()}`;
    await page.fill('#fin-comment', marker);
    await page.click('#fin-submit-btn');
    await page.waitForSelector('#tx-form-modal', { state: 'hidden' });
    await page.waitForTimeout(150);

    const markersAfterAdd = await page.evaluate(() => Array.from(document.querySelectorAll('.tx-item')).map((el) => el.dataset.txId + ':' + el.dataset.testMarker));
    const stillPresent = markersBefore.every((m) => markersAfterAdd.includes(m));
    if (!stillPresent) throw new Error(`expected both original marked rows to survive adding a 3rd transaction, got ${JSON.stringify(markersAfterAdd)}`);
    if ((await page.locator('.tx-item').count()) !== 3) throw new Error('expected exactly 3 rows after adding one transaction');
    const newRow = page.locator('.tx-item', { hasText: marker });
    if ((await newRow.count()) !== 1) throw new Error('expected the newly-added row to be present');
    console.log('[ok] adding a transaction inserts one new node without disturbing the 2 existing (marked) ones');

    // ── Delete one of the original 2: the other marked node must survive
    // untouched. ──
    const firstTxId = markersBefore[0].split(':')[0];
    await page.evaluate((txId) => {
      const row = document.querySelector(`.tx-item[data-tx-id="${txId}"]`);
      row.querySelector('.tx-swipe-delete').click();
    }, firstTxId);
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(200);

    const markersAfterDelete = await page.evaluate(() => Array.from(document.querySelectorAll('.tx-item')).map((el) => el.dataset.txId + ':' + el.dataset.testMarker));
    const remainingOriginal = markersBefore.filter((m) => m.split(':')[0] !== firstTxId);
    const survived = remainingOriginal.every((m) => markersAfterDelete.includes(m));
    if (!survived) throw new Error(`expected the other original marked row to survive the delete, got ${JSON.stringify(markersAfterDelete)}`);
    if (markersAfterDelete.some((m) => m.split(':')[0] === firstTxId)) throw new Error('expected the deleted row to actually be gone');
    console.log('[ok] deleting a transaction removes only its own node, leaving the other marked node untouched');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the whole flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nTX LIST TARGETED UPDATE TEST PASSED');
}

main().catch((err) => {
  console.error('\nTX LIST TARGETED UPDATE TEST FAILED:', err.message);
  process.exitCode = 1;
});
