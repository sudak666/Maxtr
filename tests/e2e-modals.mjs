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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'e2e-modals-uid', email:'e2e-modals@example.com'})); return ()=>{}; }
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
    await page.click('#btn-settings');
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

    // ── setupModalAccessibility() (js/settings-managers.js): Escape closes
    // the topmost modal, focus lands inside the card on open, Tab is
    // trapped within the card, and focus returns to the triggering element
    // on close. Verifies the generic MutationObserver-based wiring, not any
    // one modal's own bespoke logic — see CLAUDE.md's Firebase-data-model-
    // adjacent UI notes for how this differs from closeManagers()'s id list. ──
    await page.focus('#btn-settings');
    await page.evaluate(() => window.openWalletsManager && window.openWalletsManager());
    await page.waitForSelector('#wallets-modal', { state: 'visible' });
    await page.waitForTimeout(50); // the initial-focus setTimeout(...,0) in setupModalAccessibility()

    const focusedInsideCard = await page.evaluate(() => !!document.activeElement?.closest('#wallets-modal .modal-card'));
    if (!focusedInsideCard) throw new Error('expected focus to land inside the modal card on open');
    console.log('[ok] opening a modal moves focus inside its card');

    await page.keyboard.press('Escape');
    await page.waitForSelector('#wallets-modal', { state: 'hidden' });
    console.log('[ok] pressing Escape closes the topmost open modal');

    const focusReturnedToTrigger = await page.evaluate(() => document.activeElement?.id === 'btn-settings');
    if (!focusReturnedToTrigger) throw new Error(`expected focus to return to #btn-settings after Escape-closing the modal, got #${await page.evaluate(() => document.activeElement?.id)}`);
    console.log('[ok] closing a modal (via Escape) returns focus to the element that triggered it');

    // Focus trap: Tab from the last focusable element in the card wraps
    // back to the first, instead of escaping the modal.
    await page.evaluate(() => window.openWalletsManager && window.openWalletsManager());
    await page.waitForSelector('#wallets-modal', { state: 'visible' });
    await page.waitForTimeout(50);
    const firstFocusableId = await page.evaluate(() => document.activeElement?.id || document.activeElement?.className);
    await page.evaluate(() => {
      const card = document.querySelector('#wallets-modal .modal-card');
      const items = Array.from(card.querySelectorAll('a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])')).filter((el) => el.offsetParent !== null);
      items[items.length - 1].focus();
    });
    await page.keyboard.press('Tab');
    const wrappedToFirst = await page.evaluate((firstId) => {
      const active = document.activeElement;
      return !!active?.closest('#wallets-modal .modal-card') && (active.id === firstId || active.className === firstId);
    }, firstFocusableId);
    if (!wrappedToFirst) throw new Error('expected Tab from the last focusable element to wrap back to the first, staying inside the modal card');
    console.log('[ok] Tab from the last focusable element in an open modal wraps back to the first (focus trap)');
    await page.keyboard.press('Escape');
    await page.waitForSelector('#wallets-modal', { state: 'hidden' });

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
