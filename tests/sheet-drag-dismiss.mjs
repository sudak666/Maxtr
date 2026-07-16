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
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
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
    await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
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

    // ── Regression (flagged by a Codex review on this same PR): a drag
    // starting inside .modal-card-body's own 24px side padding — a real
    // touch near the sheet's literal rounded corner, well within the
    // *card's* own bounds but where .sheet-grabber's box used to stop
    // short before this fix — must also register, not just the grabber's
    // own edges. Uses elementFromPoint + a real hit-test dispatch (not a
    // direct dispatch on .sheet-grabber) so this actually exercises the
    // CSS geometry rather than trivially passing regardless of it. ──
    await page.click('[data-action="open-tools-manager"]');
    await page.waitForSelector('#tools-modal', { state: 'visible' });
    await page.waitForTimeout(250);
    const cardBox = await page.locator('#tools-modal .modal-card').boundingBox();
    const grabberBox3 = await page.locator('#tools-modal .sheet-grabber').boundingBox();
    const paddingStartX = cardBox.x + 8; // inside the card, well within the old 24px dead zone
    const paddingStartY = grabberBox3.y + grabberBox3.height / 2;
    const hit = await page.evaluate(({ x, y }) => {
      const el = document.elementFromPoint(x, y);
      return el ? { tag: el.tagName, cls: el.className } : null;
    }, { x: paddingStartX, y: paddingStartY });
    if (!hit || !String(hit.cls).includes('sheet-grabber')) {
      throw new Error(`expected the point inside the card's side padding (x=${paddingStartX}) to hit .sheet-grabber, got ${JSON.stringify(hit)}`);
    }
    await page.evaluate(({ x, y }) => {
      const el = document.elementFromPoint(x, y);
      const fire = (type, cx, cy) => el.dispatchEvent(new PointerEvent(type, {
        bubbles: true, cancelable: true, clientX: cx, clientY: cy, pointerId: 7, pointerType: 'touch',
      }));
      fire('pointerdown', x, y);
      for (let i = 1; i <= 8; i++) fire('pointermove', x, y + (150 * i) / 8);
      fire('pointerup', x, y + 150);
    }, { x: paddingStartX, y: paddingStartY });
    await page.waitForTimeout(400);
    if (await page.locator('#tools-modal').isVisible()) {
      throw new Error('expected a drag starting inside the card\'s side padding (the old dead zone) to dismiss the sheet too');
    }
    console.log('[ok] a drag starting inside the card\'s side padding (previously a dead zone outside .sheet-grabber\'s content-box width) also dismisses the sheet');

    // ── Body-level dismiss: a downward swipe starting inside
    // .modal-card-body itself (not the grabber/header) also dismisses the
    // sheet, as long as the body is scrolled to its own top when the touch
    // starts — a real account-owner report: for a content-heavy sheet like
    // Інструменти, the grabber/header is a thin strip and almost the whole
    // visible sheet is body content, so a natural swipe-down there used to
    // do nothing. Uses real TouchEvents (not PointerEvents) since that's
    // what the body-level listener in initSheetDrag() actually listens for
    // (a non-passive touchmove is what lets it preventDefault() the native
    // scroll/bounce once it decides to take over — see that function's own
    // comment for why Pointer Events aren't used for this specific path). ──
    async function swipeBody(totalDy, steps) {
      if (!(await page.locator('#tools-modal').isVisible())) {
        await page.evaluate(() => document.querySelector('[data-action="open-tools-manager"]').click());
        await page.waitForSelector('#tools-modal', { state: 'visible' });
        await page.waitForTimeout(250);
      }
      const bodyBox = await page.locator('#tools-modal .modal-card-body').boundingBox();
      const x = bodyBox.x + bodyBox.width / 2;
      const y = bodyBox.y + 40; // well inside the body, below the header/grabber
      await page.evaluate(({ x, y, totalDy, steps }) => {
        const target = document.elementFromPoint(x, y);
        const mk = (type, cy) => {
          const t = new Touch({ identifier: 42, target, clientX: x, clientY: cy, pageX: x, pageY: cy });
          const list = type === 'touchend' ? [] : [t];
          return new TouchEvent(type, { bubbles: true, cancelable: true, touches: list, targetTouches: list, changedTouches: [t] });
        };
        target.dispatchEvent(mk('touchstart', y));
        for (let i = 1; i <= steps; i++) target.dispatchEvent(mk('touchmove', y + (totalDy * i) / steps));
        target.dispatchEvent(mk('touchend', y + totalDy));
      }, { x, y, totalDy, steps });
      await page.waitForTimeout(400);
      return page.locator('#tools-modal').isVisible();
    }

    const visibleAfterBodySwipe = await swipeBody(150, 8);
    if (visibleAfterBodySwipe) throw new Error('expected a downward swipe starting inside .modal-card-body (scrolled to its top) to dismiss the sheet too, not just the grabber/header');
    console.log('[ok] a downward swipe starting inside the modal body (scrolled to top) also dismisses the sheet (the reported "swipe down does nothing in Інструменти" bug)');

    // ── The body-level gesture must never hijack a real scroll: force the
    // body to actually overflow, scroll it away from the top, then swipe
    // down from inside it — the sheet must stay open (this is a normal
    // scroll-back-toward-top gesture, not a dismiss). Reopens via a direct
    // DOM click (not page.click()) — Playwright's actionability re-check
    // races with the modal's own open animation immediately covering the
    // trigger button, which is a Playwright/CSS-animation-timing quirk in
    // this test, not a real app bug (the button works fine for a real
    // user's single tap; every other open in this file uses page.click()
    // fine too, just not reliably back-to-back with zero gap after a
    // just-closed modal). ──
    await page.evaluate(() => document.querySelector('[data-action="open-tools-manager"]').click());
    await page.waitForSelector('#tools-modal', { state: 'visible' });
    await page.waitForTimeout(250);
    const scrollState = await page.evaluate(() => {
      const body = document.querySelector('#tools-modal .modal-card-body');
      body.style.paddingBottom = '2000px'; // force real overflow regardless of seeded data
      body.scrollTop = 50;
      return { scrollTop: body.scrollTop, overflows: body.scrollHeight > body.clientHeight };
    });
    if (!scrollState.overflows || scrollState.scrollTop <= 0) throw new Error(`test setup failed to force a real mid-scroll state: ${JSON.stringify(scrollState)}`);
    const visibleAfterMidScrollSwipe = await swipeBody(150, 8);
    if (!visibleAfterMidScrollSwipe) throw new Error('a downward swipe starting mid-scroll (not at the body\'s own top) incorrectly dismissed the sheet — it should have been left alone as a normal scroll gesture');
    console.log('[ok] a downward swipe starting mid-scroll (not at the body\'s own top) does NOT dismiss the sheet');

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
