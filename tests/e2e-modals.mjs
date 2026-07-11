// E2E test for the generic "settings manager" modal close wiring
// (closeManagers(), see js/settings-managers.js) — same stubbed-Firebase
// Playwright recipe as tests/smoke.mjs. Added after a real bug was caught by
// manual testing while converting inline onclick="" handlers to
// addEventListener wiring: a single delegated document click listener
// registered on the bubble phase silently never fired for in-card
// "Готово"/"Скасувати" buttons, because every .modal-card has its own
// onclick="event.stopPropagation()" (needed so a click inside the card
// doesn't also match the backdrop-close check) — which also blocks bubble
// propagation from ever reaching a document-level listener. The button
// looked fine, produced no console error, and simply did nothing. Fixed by
// registering the delegated listener on the capture phase instead. This
// test locks in all three behaviors so that fix (or the next person's
// modal-related change) can't regress silently again:
//
//   node tests/e2e-modals.mjs
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
const PORT = 8894;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_FIRESTORE = `
export function getFirestore(){ return {}; }
export function doc(){ return {}; }
export async function getDoc(){ return { exists:()=>false, data:()=>({}) }; }
export async function setDoc(){ return; }
export async function deleteDoc(){ return; }
`;
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'e2e-modals-uid', email:'e2e-modals@example.com'})); return ()=>{}; }
export async function signOut(){ return; }
export async function deleteUser(){ return; }
export async function createUserWithEmailAndPassword(){ throw new Error('stub'); }
export async function signInWithEmailAndPassword(){ throw new Error('stub'); }
export class GoogleAuthProvider{}
export async function signInWithPopup(){ throw new Error('stub'); }
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
    await page.click('#nav-settings');
    await page.waitForTimeout(300);

    // ── in-card "Готово" button (data-close-modal) closes the modal ──
    await page.evaluate(() => window.openWalletsManager && window.openWalletsManager());
    await page.waitForSelector('#wallets-modal', { state: 'visible' });
    await page.click('#wallets-modal [data-close-modal]');
    await page.waitForSelector('#wallets-modal', { state: 'hidden' });
    console.log('[ok] in-card "Готово" (data-close-modal) button closes the modal');

    // ── clicking inside the card body (not on data-close-modal) does NOT close it ──
    await page.evaluate(() => window.openWalletsManager && window.openWalletsManager());
    await page.waitForSelector('#wallets-modal', { state: 'visible' });
    await page.click('#wallets-modal .modal-card-body', { position: { x: 10, y: 10 }, force: true });
    await page.waitForTimeout(200);
    if (!(await page.isVisible('#wallets-modal'))) throw new Error('modal closed after clicking inside the card body — should only close via the backdrop or a data-close-modal button');
    console.log('[ok] clicking inside the modal card body leaves it open');

    // ── clicking the backdrop closes the modal ──
    await page.click('#wallets-modal', { position: { x: 5, y: 5 } });
    await page.waitForSelector('#wallets-modal', { state: 'hidden' });
    console.log('[ok] clicking the modal-overlay backdrop closes the modal');

    if (pageErrors.length) throw new Error(`uncaught page errors during modal flow: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the modal open/close flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nE2E MODALS TEST PASSED');
}

main().catch((err) => {
  console.error('\nE2E MODALS TEST FAILED:', err.message);
  process.exitCode = 1;
});
