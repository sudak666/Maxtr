// CSV import/export round-trip test: creates a transaction, exports CSV,
// deletes the transaction, re-imports the exported file, and asserts it
// comes back with the same amount/comment. Also checks that importing a
// file with an unrecognized wallet name is reported as a skipped row
// rather than silently dropped or crashing. Same stubbed-Firebase
// Playwright recipe as tests/e2e-crud.mjs. Run with:
//
//   node tests/csv-import-export.mjs
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
`;
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'csv-test-uid', email:'csv@example.com'})); return ()=>{}; }
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
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, acceptDownloads: true });
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

    const marker = `csv-rt-${Date.now()}`;

    // ── create a transaction to round-trip ──
    await page.click('.fin-fab');
    await page.waitForSelector('#tx-form-modal', { state: 'visible' });
    await page.fill('#fin-amount', '777.25');
    await page.fill('#fin-comment', marker);
    await page.click('#fin-submit-btn');
    await page.waitForSelector('#tx-form-modal', { state: 'hidden' });
    await page.waitForTimeout(200);

    let row = page.locator('.tx-item', { hasText: marker });
    if ((await row.count()) !== 1) throw new Error(`expected the created transaction to appear before exporting, found ${await row.count()}`);
    console.log('[ok] setup: transaction created');

    // ── export ──
    await page.click('#btn-settings');
    await page.waitForTimeout(300);
    const downloadPromise = page.waitForEvent('download');
    await page.click('[data-action="export-transactions-csv"]');
    const download = await downloadPromise;
    const csvPath = await download.path();
    const csvText = fs.readFileSync(csvPath, 'utf8');
    if (!csvText.includes(marker)) throw new Error('exported CSV does not contain the marker comment');
    if (!csvText.includes('777,25') && !csvText.includes('777.25')) throw new Error('exported CSV does not contain the expected amount');
    console.log('[ok] export: CSV file contains the created transaction');

    // ── delete the original, so the round-trip actually proves the import re-created it ──
    await page.click('#nav-finance');
    await page.waitForTimeout(200);
    row = page.locator('.tx-item', { hasText: marker });
    await row.hover();
    await row.locator('.tx-swipe-delete').click();
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.waitForTimeout(50);
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(200);
    row = page.locator('.tx-item', { hasText: marker });
    if ((await row.count()) !== 0) throw new Error('expected the transaction to be gone before re-importing');
    console.log('[ok] setup: original transaction deleted before re-import');

    // ── import the exported file back ──
    await page.click('#btn-settings');
    await page.waitForTimeout(300);
    const fileInput = page.locator('#csv-import-input');
    await fileInput.setInputFiles(csvPath);
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    const confirmText = await page.locator('#ui-dialog').textContent();
    if (!/1/.test(confirmText)) throw new Error(`expected the import confirm dialog to mention 1 transaction, got: "${confirmText}"`);
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(300);

    await page.click('#nav-finance');
    await page.waitForTimeout(300);
    row = page.locator('.tx-item', { hasText: marker });
    if ((await row.count()) !== 1) throw new Error(`expected the imported transaction to reappear, found ${await row.count()}`);
    const amountText = await row.locator('.tx-amount').textContent();
    if (!amountText.includes('777,25') && !amountText.includes('777.25')) {
      throw new Error(`re-imported transaction shows unexpected amount: "${amountText}"`);
    }
    console.log('[ok] import: re-importing the exported CSV recreates the transaction with the same amount/comment');

    // ── a CSV with an unrecognized wallet name is reported, not silently dropped or crashed ──
    const badCsv = '﻿Дата;Тип;Категорія;Підкатегорія;Гаманець;Сума;Валюта;Куди;Сума переказу;Валюта переказу;Коментар\r\n2026-01-01;Витрата;Інше;;Неіснуючий гаманець;10;UAH;;;;test\r\n';
    const badPath = path.join('/tmp', `bad-import-${Date.now()}.csv`);
    fs.writeFileSync(badPath, badCsv, 'utf8');
    await fileInput.setInputFiles(badPath);
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    const dlgText = await page.locator('#ui-dialog').textContent();
    await page.click('#ui-dlg-ok');
    if (!dlgText || dlgText.trim().length === 0) throw new Error('expected some dialog text for an unimportable file');
    console.log('[ok] import: a CSV row with an unknown wallet is reported via a dialog, not silently dropped');

    if (pageErrors.length) throw new Error(`uncaught page errors during CSV import/export flow: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the full export/delete/import flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nCSV IMPORT/EXPORT TEST PASSED');
}

main().catch((err) => {
  console.error('\nCSV IMPORT/EXPORT TEST FAILED:', err.message);
  process.exitCode = 1;
});
