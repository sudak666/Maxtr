// E2E CRUD test for the Finance tab's transaction form — create, edit, and
// delete a transaction through the real UI (not just unit-level helpers),
// against the same stubbed-Firebase Playwright recipe as tests/smoke.mjs
// (see that file's header and CLAUDE.md's "Verify a change" section for why
// this approach works without network access or real credentials). Added
// specifically because the smoke test only asserts that tabs render with no
// console errors — it never exercises the actual add/edit/delete flow, so a
// regression there (e.g. a broken onclick wiring, a modal that won't close)
// would ship unnoticed. Run with:
//
//   node tests/e2e-crud.mjs
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
const PORT = 8898;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

// Same stubs as tests/smoke.mjs (kept separate per-file rather than shared,
// matching how firestore-rules.mjs/smoke.mjs are each self-contained here).
const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
// A real (if minimal) path-keyed in-memory store, not just no-ops — needed
// once collection()/getDocs()/writeBatch() exist (added for the
// transactions-subcollection migration, see CLAUDE.md's Firebase data
// model section) so a getDocs() on a collection a prior setDoc() wrote into
// actually returns something, rather than every Firestore call being an
// inert stub. Module-scoped _docs resets on every fresh page load (this
// module string is re-evaluated per navigation), so there's no cross-test
// leakage.
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
`;
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'e2e-test-uid', email:'e2e@example.com'})); return ()=>{}; }
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

    const marker = `e2e-tx-${Date.now()}`;

    // ── CREATE ──
    await page.click('.fin-fab');
    await page.waitForSelector('#tx-form-modal', { state: 'visible' });
    await page.fill('#fin-amount', '1234.56');
    await page.fill('#fin-comment', marker);
    await page.click('#fin-submit-btn');
    await page.waitForSelector('#tx-form-modal', { state: 'hidden' });

    let row = page.locator('.tx-item', { hasText: marker });
    if ((await row.count()) !== 1) throw new Error(`expected exactly 1 transaction row with marker "${marker}" after create, found ${await row.count()}`);
    const amountAfterCreate = await row.locator('.tx-amount').textContent();
    if (!amountAfterCreate.includes('234,56') && !amountAfterCreate.includes('234.56')) {
      throw new Error(`created transaction shows unexpected amount: "${amountAfterCreate}"`);
    }
    console.log('[ok] create: new transaction appears in the list with the entered amount');

    // ── EDIT ── (tapping the row itself opens edit - the dedicated pencil
    // button was removed, see index.html's tx-item-inner/swipe-to-delete note)
    await row.click();
    await page.waitForSelector('#tx-form-modal', { state: 'visible' });
    const amountField = page.locator('#fin-amount');
    const prefilled = await amountField.inputValue();
    if (prefilled !== '1234.56') throw new Error(`edit form did not prefill the existing amount, got "${prefilled}"`);
    await amountField.fill('999.99');
    await page.click('#fin-submit-btn');
    await page.waitForSelector('#tx-form-modal', { state: 'hidden' });

    row = page.locator('.tx-item', { hasText: marker });
    if ((await row.count()) !== 1) throw new Error(`expected exactly 1 transaction row with marker "${marker}" after edit, found ${await row.count()}`);
    const amountAfterEdit = await row.locator('.tx-amount').textContent();
    if (!amountAfterEdit.includes('999,99') && !amountAfterEdit.includes('999.99')) {
      throw new Error(`edited transaction shows unexpected amount: "${amountAfterEdit}"`);
    }
    console.log('[ok] edit: transaction amount updates in place and the row count stays at 1');

    // ── DELETE ── (the delete button lives behind a swipe-to-reveal panel
    // now; a real touch swipe is dispatched in tests/tx-swipe-delete.mjs -
    // here, hovering reveals it via CSS :hover exactly like a mouse user
    // would see, which is enough for this test's own CRUD-flow purpose)
    await row.hover();
    await row.locator('.tx-swipe-delete').click();
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(200);

    row = page.locator('.tx-item', { hasText: marker });
    if ((await row.count()) !== 0) throw new Error(`expected the transaction row to be gone after delete, found ${await row.count()}`);
    console.log('[ok] delete: confirming the dialog removes the transaction from the list');

    if (pageErrors.length) throw new Error(`uncaught page errors during CRUD flow: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the full create/edit/delete flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nE2E CRUD TEST PASSED');
}

main().catch((err) => {
  console.error('\nE2E CRUD TEST FAILED:', err.message);
  process.exitCode = 1;
});
