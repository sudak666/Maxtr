// "Hide amounts" privacy toggle (topbar #btn-hide-amounts, body.amounts-hidden
// CSS in index.html) — a real-device report ("ще не всі суми приховані")
// found several money displays the toggle didn't cover: the Finance tab's
// Budgets widget, the Goals widget, the Debt tab's payment-history rows +
// discrepancy hint + payoff-forecast average, and a cross-currency
// transfer's target-amount note. This test seeds data that triggers each
// of those, toggles the button, and asserts every one of them actually
// gets blur:blur(...) applied — plus one negative check (a debt entry's
// *date* field, which shares a class with the amount/balance fields but
// must NOT be blurred, since it isn't money). Run with:
//
//   node tests/hide-amounts.mjs
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
const STUB_FIRESTORE_TEMPLATE = `
const _docs = new Map(Object.entries(__SEED__));
export function getFirestore(){ return {}; }
export function initializeFirestore(){ return {}; }
export function doc(parent, ...rest){ if (parent && parent.path !== undefined) return { path: parent.path + '/' + rest[0] }; return { path: rest.join('/') }; }
export function collection(parent, name){ const base = parent && parent.path !== undefined ? parent.path : ''; return { path: (base ? base + '/' : '') + name }; }
export async function getDoc(ref){ const d = _docs.get(ref.path); return { exists: () => d !== undefined, data: () => d }; }
export async function setDoc(ref, data){ _docs.set(ref.path, data); }
export async function deleteDoc(ref){ _docs.delete(ref.path); }
export async function getDocs(ref){ const prefix = ref.path + '/'; const items = []; for (const [k, v] of _docs) { if (k.startsWith(prefix) && !k.slice(prefix.length).includes('/')) items.push({ id: k.slice(prefix.length), data: () => v }); } return { docs: items, forEach(fn){ items.forEach(fn); }, empty: items.length === 0, size: items.length }; }
export function writeBatch(){ const ops = []; return { set(ref, data){ ops.push(() => _docs.set(ref.path, data)); }, delete(ref){ ops.push(() => _docs.delete(ref.path)); }, async commit(){ ops.forEach((fn) => fn()); } }; }
export async function updateDoc(ref, data){ const existing = _docs.get(ref.path) || {}; const merged = { ...existing }; for (const k in data) merged[k] = data[k]; _docs.set(ref.path, merged); }
export function arrayUnion(...items){ return { __isArrayUnion: true, items }; }
export function arrayRemove(...items){ return { __isArrayRemove: true, items }; }
`;
function stubFirestore(seed) { return STUB_FIRESTORE_TEMPLATE.replace('__SEED__', JSON.stringify(seed)); }
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'hide-amt-uid', email:'hide-amt@example.com'})); return ()=>{}; }
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

    const now = new Date();
    const monthPrefix = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    const today = `${monthPrefix}-15`;

    const seed = {
      'users/hide-amt-uid/max_tracker/profiles_meta': { list: [{ id: 'default', name: 'Я' }], updatedAt: Date.now() },
      'users/hide-amt-uid/max_tracker/finance': {
        wallets: [
          { id: 'w-uah', name: 'Картка', color: '#8b5cf6', icon: 'card', currency: 'UAH' },
          { id: 'w-usd', name: 'Долари', color: '#22c55e', icon: 'card', currency: 'USD' },
        ],
        categories: { income: ['Зарплата'], expense: ['Продукти'] },
        budgets: { 'Продукти': 5000 },
        subcategories: {}, categoryIcons: {}, currencyRates: { USD: 41 }, tags: [], autoRules: [], recurring: [], shoppingList: [],
        goals: [{ id: 'g1', walletId: 'w-uah', targetAmount: 50000, targetDate: '' }],
        profile: {}, subscription: { plan: 'free', expiresAt: null },
        widgets: { rates: true, converter: true, analytics: true, chart: true, goals: true },
        updatedAt: Date.now(),
      },
      'users/hide-amt-uid/max_tracker/finance/transactions/tx1': {
        id: 'tx1', type: 'expense', amount: 1620, currency: 'UAH', category: 'Продукти', wallet: 'w-uah', date: today, comment: '', tags: [],
      },
      'users/hide-amt-uid/max_tracker/finance/transactions/tx2': {
        id: 'tx2', type: 'transfer', amount: 100, currency: 'UAH', wallet: 'w-uah', targetWallet: 'w-usd',
        targetAmount: 2.44, targetCurrency: 'USD', category: 'Внутрішній переказ', date: today, comment: '', tags: [],
      },
      'users/hide-amt-uid/max_tracker/debt': {
        data: {
          debts: [{
            id: 1, name: 'Борг', currency: 'у.о.', startAmount: 1000,
            // entry 1: expected = 1000 - 100 = 900, but balance is 850 -> a
            // real (>0.01) discrepancy, so .debt-hint renders.
            // entry 2: balance drops further (850 -> 750), giving 2 real
            // paydowns so the forecast card's average-per-payment shows too.
            entries: [
              { id: 'e1', amount: '100', balance: 850, date: '01.07' },
              { id: 'e2', amount: '100', balance: 750, date: '02.07' },
            ],
          }],
          currentDebtId: 1,
        },
        updatedAt: Date.now(),
      },
    };

    await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
    await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: stubFirestore(seed) }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1200);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    await page.waitForTimeout(500);

    const isBlurred = async (selector) => page.evaluate((sel) => {
      const el = document.querySelector(sel);
      if (!el) return null;
      return getComputedStyle(el).filter.includes('blur');
    }, selector);

    // ── Before toggling: nothing is blurred yet ──
    if (await isBlurred('.budget-widget-val')) throw new Error('expected the budget widget amount to be visible before toggling hide-amounts');
    console.log('[ok] before toggling, amounts render normally (sanity check)');

    // ── Toggle hide-amounts on ──
    await page.click('#btn-hide-amounts');
    await page.waitForTimeout(150);

    const checks = [
      ['.budget-widget-val', 'Finance tab Budgets widget spent/limit amount'],
      ['.goal-widget-val', 'Finance tab Goals widget saved/target amount'],
      ['.debt-amount-val', 'Debt tab payment-history amount/balance and forecast average'],
      ['.debt-hint', 'Debt tab discrepancy hint'],
      ['.tx-conv-note', 'a cross-currency transfer\'s target-amount note'],
      ['.hero-balance-val', 'Finance tab hero balance (already covered before this fix, regression check)'],
    ];
    for (const [sel, label] of checks) {
      const el = await page.locator(sel).first();
      if ((await el.count()) === 0) throw new Error(`expected to find ${sel} (${label}) in the rendered page — seed data didn't trigger it`);
      const blurred = await isBlurred(sel);
      if (!blurred) throw new Error(`expected ${sel} (${label}) to be blurred after toggling hide-amounts`);
      console.log(`[ok] ${label} is blurred`);
    }

    // ── Negative check: the debt entry's date field shares .debt-field-view
    // with the amount/balance fields but is NOT money — must stay visible ──
    const dateBlurred = await page.evaluate(() => {
      const labels = Array.from(document.querySelectorAll('.debt-field-label'));
      const dateLabelEl = labels.find((l) => l.textContent && /дата/i.test(l.textContent));
      const view = dateLabelEl && dateLabelEl.parentElement.querySelector('.debt-field-view');
      return view ? getComputedStyle(view).filter.includes('blur') : null;
    });
    if (dateBlurred === null) throw new Error('could not locate the debt entry date field to check');
    if (dateBlurred) throw new Error('expected the debt entry date field to stay visible (it is not money) — over-broad blur selector regression');
    console.log('[ok] the debt entry\'s date field (not money) stays visible, proving the fix didn\'t over-blur');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nHIDE AMOUNTS TEST PASSED');
}

main().catch((err) => {
  console.error('\nHIDE AMOUNTS TEST FAILED:', err.message);
  process.exitCode = 1;
});
