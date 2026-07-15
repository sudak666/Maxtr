// E2E test for the bottom-sheet drag-to-dismiss gesture (initSheetDrag(),
// js/ui-widgets.js) at mobile viewport widths, where .modal-card switches to
// the bottom-sheet presentation (index.html's ≤600px media query). Covers a
// real account-owner report: "swipe-down barely works, especially near the
// corner" — the .sheet-grabber hit-box used to be only the visible 36x4px
// bar, centered, so a drag starting from the far left/right of that row (a
// thumb naturally drawn to the sheet's rounded top corners) missed it
// entirely and fell through to .modal-card-body's listener-less padding.
// Fixed by making .sheet-grabber's hit-box span the full card width (the
// visible bar is now a ::after pseudo-element, unchanged in size). Dispatches
// synthetic PointerEvents (Playwright has no built-in touch-drag API), same
// stubbed-Firebase Playwright recipe as the other tests/*.mjs. Run with:
//
//   node tests/sheet-drag-dismiss.mjs
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
const PORT = 8907;
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'sheet-drag-uid', email:'sd@example.com'})); return ()=>{}; }
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
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true });
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

    async function dragGrabber(startXOffset, totalDy, steps) {
      // Ensure a clean closed state before opening — a prior call's "short
      // drag snaps back" case leaves the modal open, which would otherwise
      // intercept the click below.
      if (await page.locator('#tools-modal').isVisible()) {
        await page.keyboard.press('Escape');
        await page.waitForSelector('#tools-modal', { state: 'hidden' });
        await page.waitForTimeout(150);
      }
      await page.click('[data-action="open-tools-manager"]');
      await page.waitForSelector('#tools-modal', { state: 'visible' });
      await page.waitForTimeout(250);
      const grabberBox = await page.locator('#tools-modal .sheet-grabber').boundingBox();
      const startX = grabberBox.x + startXOffset;
      const startY = grabberBox.y + grabberBox.height / 2;
      await page.evaluate(({ startX, startY, totalDy, steps }) => {
        const grabber = document.querySelector('#tools-modal .sheet-grabber');
        const fire = (type, x, y) => grabber.dispatchEvent(new PointerEvent(type, {
          bubbles: true, cancelable: true, clientX: x, clientY: y, pointerId: 5, pointerType: 'touch',
        }));
        fire('pointerdown', startX, startY);
        for (let i = 1; i <= steps; i++) fire('pointermove', startX, startY + (totalDy * i) / steps);
        fire('pointerup', startX, startY + totalDy);
      }, { startX, startY, totalDy, steps });
      await page.waitForTimeout(400);
      return page.locator('#tools-modal').isVisible();
    }

    // ── A short drag from the center of the grabber snaps back (below the
    // 110px dismiss threshold). ──
    const visibleAfterShort = await dragGrabber(170, 40, 4);
    if (!visibleAfterShort) throw new Error('expected a short drag (below the dismiss threshold) to leave the sheet open');
    console.log('[ok] a short drag from the grabber snaps back without dismissing the sheet');

    // ── A long drag from the center dismisses. ──
    const visibleAfterCenterLong = await dragGrabber(170, 150, 8);
    if (visibleAfterCenterLong) throw new Error('expected a long drag from the grabber center to dismiss the sheet');
    console.log('[ok] a long drag from the grabber center dismisses the sheet');

    // ── Regression: a long drag starting from the far-left edge of the
    // grabber's row (the "corner", well outside the old centered 36px-wide
    // bar) also dismisses — this is what used to silently do nothing. ──
    const visibleAfterCornerLong = await dragGrabber(6, 150, 8);
    if (visibleAfterCornerLong) throw new Error('expected a long drag starting from the far-left "corner" of the grabber row to dismiss the sheet too');
    console.log('[ok] a long drag starting from the far-left corner of the grabber row also dismisses the sheet (the reported bug)');

    // ── Same check on the far-right edge, for good measure. ──
    await page.click('[data-action="open-tools-manager"]');
    await page.waitForSelector('#tools-modal', { state: 'visible' });
    await page.waitForTimeout(250);
    const grabberBox2 = await page.locator('#tools-modal .sheet-grabber').boundingBox();
    const startX2 = grabberBox2.x + grabberBox2.width - 6;
    const startY2 = grabberBox2.y + grabberBox2.height / 2;
    await page.evaluate(({ startX, startY }) => {
      const grabber = document.querySelector('#tools-modal .sheet-grabber');
      const fire = (type, x, y) => grabber.dispatchEvent(new PointerEvent(type, {
        bubbles: true, cancelable: true, clientX: x, clientY: y, pointerId: 6, pointerType: 'touch',
      }));
      fire('pointerdown', startX, startY);
      for (let i = 1; i <= 8; i++) fire('pointermove', startX, startY + (150 * i) / 8);
      fire('pointerup', startX, startY + 150);
    }, { startX: startX2, startY: startY2 });
    await page.waitForTimeout(400);
    if (await page.locator('#tools-modal').isVisible()) throw new Error('expected a long drag from the far-right corner of the grabber row to dismiss the sheet too');
    console.log('[ok] a long drag starting from the far-right corner of the grabber row also dismisses the sheet');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the whole flow');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nSHEET DRAG-TO-DISMISS TEST PASSED');
}

main().catch((err) => {
  console.error('\nSHEET DRAG-TO-DISMISS TEST FAILED:', err.message);
  process.exitCode = 1;
});
