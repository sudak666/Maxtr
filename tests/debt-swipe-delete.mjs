// E2E test for the Debt tab's payment-history rows: newest-entry-first
// display order, and the swipe-to-delete gesture added to match
// js/analytics-csv.js's setupTxSwipe() (see js/debt.js's setupDebtRowSwipe()
// and index.html's .debt-row/.debt-row-inner/.debt-row-swipe-delete CSS).
// Same stubbed-Firebase Playwright recipe as tests/tx-swipe-delete.mjs. Run
// with:
//
//   node tests/debt-swipe-delete.mjs
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
const PORT = 8902;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
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
`;
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'debt-swipe-uid', email:'debt-swipe@example.com'})); return ()=>{}; }
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
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    await page.click('#nav-debt');
    await page.waitForTimeout(300);

    // ── Create a debt calc via the empty-state button (calls addNewDebt(),
    // a uiPrompt dialog). ──
    await page.locator('[onclick*="addNewDebt"]').first().click();
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.fill('#ui-dlg-input', 'Swipe-test debt');
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(200);

    // Open the collapsed debt-info panel and set a startAmount of 1000, so
    // the running-balance-chain math below (900/700/650) is consistent and
    // doesn't trip the discrepancy-hint check.
    await page.evaluate(() => {
      const body = document.getElementById('debt-info-body');
      if (body && getComputedStyle(body).display === 'none') {
        document.querySelector('[data-action="toggle-debt-info-panel"]')?.click();
      }
    });
    await page.waitForTimeout(150);
    await page.fill('#debt-start', '1000');
    await page.locator('#debt-start').dispatchEvent('change');
    await page.waitForTimeout(150);

    async function addEntry(amount, balance, dateLabel) {
      await page.evaluate(() => window.openNewDebtEntryModal && window.openNewDebtEntryModal());
      await page.waitForSelector('#debt-form-modal', { state: 'visible' });
      await page.fill('#debt-date', dateLabel);
      await page.fill('#debt-amount', amount);
      await page.fill('#debt-balance-input', balance);
      await page.click('[data-action="add-debt-entry"]');
      await page.waitForSelector('#debt-form-modal', { state: 'hidden' });
      await page.waitForTimeout(200);
    }

    // ── Newest-entry-first display order ──
    await addEntry('100', '900', '01.02.2026');
    await addEntry('200', '700', '02.03.2026');
    await addEntry('50', '650', '03.04.2026');

    const rowCount = await page.locator('.debt-row').count();
    if (rowCount !== 3) throw new Error(`expected 3 debt-row entries, found ${rowCount}`);

    const amountsInDomOrder = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.debt-row .debt-field-view')).filter((_, i) => i % 3 === 0).map((el) => el.textContent)
    );
    if (amountsInDomOrder.join(',') !== '50,200,100') {
      throw new Error(`expected newest-first order (50, 200, 100), got: ${amountsInDomOrder.join(',')}`);
    }
    console.log('[ok] payment-history entries render newest-first (most recently added entry is the top row)');

    // ── Underlying balance-chain math still uses the original chronological
    // (push) order — the top (newest) row's balance should read 650, and no
    // discrepancy hint should appear (the amounts/balances above are all
    // internally consistent: 1000-100=900, 900-200=700, 700-50=650). ──
    const hasDiscrepancy = await page.evaluate(() => document.querySelector('.debt-row.discrepancy') !== null);
    if (hasDiscrepancy) throw new Error('expected no discrepancy hints for internally-consistent entries (order flip must not affect balance-chain math)');
    console.log('[ok] reversing display order did not disturb the running-balance-chain calculation');

    // ── Swipe-to-delete: a real touch drag on the *newest* (top) row ──
    async function swipeLeft(row, totalDx, steps) {
      const entryId = await row.getAttribute('data-entry-id');
      const box = await row.boundingBox();
      const startX = box.x + box.width - 20, y = box.y + box.height / 2;
      await page.evaluate(({ entryId, startX, y, totalDx, steps }) => {
        const el = document.querySelector(`.debt-row[data-entry-id="${entryId}"]`);
        if (!el) throw new Error(`no .debt-row found for data-entry-id=${entryId}`);
        const fire = (type, x) => el.dispatchEvent(new PointerEvent(type, {
          bubbles: true, cancelable: true, clientX: x, clientY: y, pointerId: 9, pointerType: 'touch',
        }));
        fire('pointerdown', startX);
        for (let i = 1; i <= steps; i++) fire('pointermove', startX - (totalDx * i) / steps);
        fire('pointerup', startX - totalDx);
      }, { entryId, startX, y, totalDx, steps });
    }

    const topRow = page.locator('.debt-row').first();
    await topRow.scrollIntoViewIfNeeded();

    // Short swipe (below threshold) snaps back.
    await swipeLeft(topRow, 12, 3);
    await page.waitForTimeout(250);
    if (await topRow.evaluate((el) => el.classList.contains('swipe-open'))) {
      throw new Error('expected a short swipe (below threshold) to snap back, not open');
    }
    console.log('[ok] a short swipe on a debt-row snaps back without revealing delete');

    // Long swipe (past threshold) opens the row and reveals delete.
    await swipeLeft(topRow, 45, 6);
    await page.waitForTimeout(250);
    if (!(await topRow.evaluate((el) => el.classList.contains('swipe-open')))) {
      throw new Error('expected a long swipe (past threshold) to reveal the delete button');
    }
    console.log('[ok] a long swipe on a debt-row reveals the delete button');

    // Delete via the revealed button — confirm dialog, then confirm.
    await topRow.locator('.debt-row-swipe-delete').click();
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(200);

    const rowCountAfterDelete = await page.locator('.debt-row').count();
    if (rowCountAfterDelete !== 2) throw new Error(`expected 2 remaining debt-row entries after delete, found ${rowCountAfterDelete}`);
    console.log('[ok] deleting via the swipe-revealed button removes the row');

    // The pencil (edit) button is still always-visible and unambiguous —
    // tapping it directly (no swipe needed) enters inline edit mode.
    const remainingTop = page.locator('.debt-row').first();
    await remainingTop.locator('.debt-row-edit').click();
    await page.waitForTimeout(150);
    if (!(await page.locator('.debt-row-edit-active').count())) {
      throw new Error('expected tapping the always-visible pencil button to enter inline edit mode');
    }
    console.log('[ok] the edit (pencil) button still works directly, unaffected by the swipe-delete change');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the whole flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nDEBT SWIPE-TO-DELETE + REORDER TEST PASSED');
}

main().catch((err) => {
  console.error('\nDEBT SWIPE-TO-DELETE + REORDER TEST FAILED:', err.message);
  process.exitCode = 1;
});
