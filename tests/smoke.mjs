// Minimal smoke test for Zminka (see /home/user/Maxtr/.claude/skills/verify
// for the full recipe this follows). No build step / test runner in this
// repo by design (see CLAUDE.md) — this is a plain node script, run with:
//
//   node tests/smoke.mjs
//
// It needs no real Firebase project or network access: the three
// firebasejs module imports are intercepted and fulfilled with hand-written
// stubs, and sign-in is simulated by firing the stubbed onAuthStateChanged
// callback directly.
//
// playwright is a global install in this sandbox, not a project dependency
// (there's no root package.json — see CLAUDE.md), and Node's ESM resolver
// doesn't consult NODE_PATH the way CJS require() does, so it's imported
// here by its absolute install path rather than by bare specifier.
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const PLAYWRIGHT_PATH = '/opt/node22/lib/node_modules/playwright/index.mjs';
const { chromium } = fs.existsSync(PLAYWRIGHT_PATH)
  ? await import(PLAYWRIGHT_PATH)
  : await import(createRequire(import.meta.url).resolve('playwright'));

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const PORT = 8899;
const CHROMIUM_PATH = '/opt/pw-browsers/chromium';

function checkModuleScriptSyntax() {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const m = html.match(/<script type="module">([\s\S]*?)<\/script>/);
  if (!m) throw new Error('could not find <script type="module"> in index.html');
  const tmp = path.join(ROOT, 'tests', '.mod_check.mjs');
  fs.writeFileSync(tmp, m[1].replace(/https:\/\/www\.gstatic\.com\/[^"]+/g, 'data:text/javascript,export default {}'));
  return tmp;
}

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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'smoke-test-uid', email:'smoke@example.com'})); return ()=>{}; }
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
  const modCheckPath = checkModuleScriptSyntax();
  const { execFileSync } = await import('node:child_process');
  execFileSync(process.execPath, ['--check', modCheckPath]);
  fs.unlinkSync(modCheckPath);
  console.log('[ok] module script syntax check');

  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));

  const browser = await chromium.launch({ executablePath: CHROMIUM_PATH });
  const consoleErrors = [];
  const pageErrors = [];
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    console.log('[ok] page loaded with stubbed Firebase SDK (auth-gated flow: onAuthStateChanged sign-in)');

    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());

    const bodyText = await page.textContent('body');
    if (!bodyText || bodyText.trim().length === 0) throw new Error('page is blank after sign-in/onboarding');
    console.log('[ok] page not blank after onboarding');

    const tabs = ['finance', 'shifts', 'debt', 'shopping', 'settings'];
    for (const tab of tabs) {
      await page.click(`#nav-${tab}`);
      await page.waitForTimeout(300);
      const visible = await page.isVisible(`#tab-${tab}`);
      if (!visible) throw new Error(`#tab-${tab} did not become visible after switching to it`);
      console.log(`[ok] tab "${tab}" renders`);
    }

    // updateRatesOnline() auto-fires on load to fetch live NBU exchange
    // rates from a real external bank API (by design — see CLAUDE.md's
    // "Auto-refresh once a day" note) and fails silently by design when
    // that host is unreachable. That's a sandbox/network-policy fact, not
    // an app bug, so it's not a smoke-test failure on its own.
    const realConsoleErrors = consoleErrors.filter((e) => !/live rates fetch failed|net::ERR_/.test(e));
    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    if (realConsoleErrors.length) throw new Error(`console errors: ${realConsoleErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors or console errors (besides the expected live-rates network call, which this sandbox blocks)');
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nSMOKE TEST PASSED');
}

main().catch((err) => {
  console.error('\nSMOKE TEST FAILED:', err.message);
  process.exitCode = 1;
});
