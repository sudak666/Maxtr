// Verifies the Finance transaction list always renders newest-first, even
// when the underlying Firestore data was NOT stored in that order.
// Firestore's getDocs() on the transactions subcollection has no orderBy —
// document order is unspecified — so a real account's history can load in
// essentially arbitrary order (confirmed by a real account-owner
// screenshot showing oldest-first). Seeds transactions deliberately
// oldest-first (mimicking that exact real-world case) directly into the
// stubbed Firestore, then asserts js/color-picker.js's fbLoadNow() sort +
// js/analytics-csv.js's renderFinance() sort both produce a newest-first
// list. Same stubbed-Firebase Playwright recipe as the other tests/*.mjs.
// Run with:
//
//   node tests/tx-list-sort-order.mjs
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
const UID = 'sort-order-uid';

// Seeded deliberately OLDEST-FIRST (ascending date), same as the real
// account-owner report — Firestore's own unordered getDocs() means this is
// exactly as valid an arrival order as any other.
const seedEntries = [
  { id: 1700000000001, date: '2026-06-01', comment: 'oldest' },
  { id: 1700000000002, date: '2026-06-15', comment: 'middle' },
  { id: 1700000000003, date: '2026-07-01', comment: 'newest-by-date' },
  // Same date as "newest-by-date" but a *larger* id (created later that
  // same day) — must still sort after it (id used as the tiebreaker).
  { id: 1700000000099, date: '2026-07-01', comment: 'newest-by-date-and-id' },
].map(({ id, date, comment }) => {
  const tx = {
    id, type: 'expense', amount: 10, currency: 'UAH', category: 'Кава',
    subcategory: null, tags: [], wallet: 'w1', targetWallet: null, targetAmount: null,
    targetCurrency: null, date, comment,
  };
  return [`users/${UID}/max_tracker/finance/transactions/${id}`, tx];
});

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'${UID}', email:'sort@example.com'})); return ()=>{}; }
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
    await page.click('#nav-finance');
    await page.waitForTimeout(300);

    const commentsInDomOrder = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.tx-item .tx-meta')).map((el) => el.textContent.split(' · ')[1])
    );
    const expected = ['newest-by-date-and-id', 'newest-by-date', 'middle', 'oldest'];
    if (JSON.stringify(commentsInDomOrder) !== JSON.stringify(expected)) {
      throw new Error(`expected newest-first order ${JSON.stringify(expected)}, got ${JSON.stringify(commentsInDomOrder)}`);
    }
    console.log('[ok] transaction list renders newest-first (by date, then by id as a same-date tiebreaker) despite the seed data arriving oldest-first from Firestore');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nTX LIST SORT ORDER TEST PASSED');
}

main().catch((err) => {
  console.error('\nTX LIST SORT ORDER TEST FAILED:', err.message);
  process.exitCode = 1;
});
