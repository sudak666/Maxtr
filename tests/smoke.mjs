// Minimal smoke test for Rytm (see /home/user/Maxtr/.claude/skills/verify
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
// playwright is a global install (not a project dependency — there's no
// root package.json, see CLAUDE.md), so it can't be resolved by a bare
// specifier from this file's own location (Node's ESM resolver doesn't
// consult NODE_PATH the way CJS require() does, and createRequire().resolve()
// only walks up from this file's directory, which never finds a global
// install either). This sandbox happens to have a fixed absolute path for
// it, but CI (GitHub Actions' `npm install -g playwright`) puts it somewhere
// else entirely, so ask npm itself where global packages live and build the
// path from that, falling back to the sandbox path if npm isn't on PATH.
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
const PORT = 8899;
// This sandbox has a fixed Chromium binary path outside Playwright's own
// managed browser cache; CI (and any other environment) won't have that
// exact path, so fall back to Playwright's own installed browser (requires
// `playwright install chromium` to have been run) when it's absent.
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

// index.html's app logic is split across js/*.js (see CLAUDE.md's module
// layout) and loaded via <script type="module" src="./js/app.js">, rather
// than one inline <script type="module"> block. `node --check` can't
// resolve bare/relative ES module graphs on its own, so each file is
// checked independently for syntax errors here; the real cross-file
// import/export wiring is only meaningfully verified by actually loading
// the page in a browser, which the rest of this script already does.
function checkModuleScriptSyntax() {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const inline = html.match(/<script type="module">([\s\S]*?)<\/script>/);
  if (inline) {
    const tmp = path.join(ROOT, 'tests', '.mod_check.mjs');
    fs.writeFileSync(tmp, inline[1].replace(/https:\/\/www\.gstatic\.com\/[^"]+/g, 'data:text/javascript,export default {}'));
    return [tmp];
  }
  const srcMatch = html.match(/<script type="module" src="([^"]+)">/);
  if (!srcMatch) throw new Error('could not find a <script type="module"> (inline or src=) in index.html');
  const jsDir = path.join(ROOT, path.dirname(srcMatch[1]));
  return fs.readdirSync(jsDir).filter((f) => f.endsWith('.js')).map((f) => path.join(jsDir, f));
}

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
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
export function initializeFirestore(){ return {}; }
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

export async function updateDoc(ref, data){
  const existing = _docs.get(ref.path) || {};
  const merged = { ...existing };
  for (const k in data) {
    const v = data[k];
    if (v && v.__isArrayUnion) {
      const arr = Array.isArray(merged[k]) ? merged[k].slice() : [];
      v.items.forEach((item) => { if (!arr.includes(item)) arr.push(item); });
      merged[k] = arr;
    } else if (v && v.__isArrayRemove) {
      const arr = Array.isArray(merged[k]) ? merged[k].slice() : [];
      merged[k] = arr.filter((item) => !v.items.includes(item));
    } else {
      merged[k] = v;
    }
  }
  _docs.set(ref.path, merged);
}
export function arrayUnion(...items){ return { __isArrayUnion: true, items }; }
export function arrayRemove(...items){ return { __isArrayRemove: true, items }; }
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
  const modCheckPaths = checkModuleScriptSyntax();
  // `node --check` only treats a file as an ES module by its extension
  // (.mjs) or a package.json "type" field - js/ has neither (deliberately;
  // this repo has no root package.json - see CLAUDE.md), so each real
  // source file is copied to a throwaway .mjs path for the syntax check
  // only, then removed; the inline-script case's own temp file is always
  // its own throwaway and gets removed too.
  for (const p of modCheckPaths) {
    const isThrowaway = p.endsWith('.mod_check.mjs');
    const tmp = isThrowaway ? p : `${p}.checktmp.mjs`;
    if (!isThrowaway) fs.copyFileSync(p, tmp);
    try {
      execFileSync(process.execPath, ['--check', tmp]);
    } finally {
      fs.unlinkSync(tmp);
    }
  }
  console.log(`[ok] module script syntax check (${modCheckPaths.length} file(s))`);

  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));

  const browser = await chromium.launch(CHROMIUM_PATH ? { executablePath: CHROMIUM_PATH } : {});
  const consoleErrors = [];
  const pageErrors = [];
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });
    page.on('pageerror', (err) => pageErrors.push(err.message));

    await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
    await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));

    // js/app-init.js's init() unconditionally calls maybeAutoUpdateRates() on
    // cold start, which fetches live NBU rates in the background. This
    // sandbox's own network blocks bank.gov.ua outright (a net::ERR_* the
    // filter below already tolerates), but a GitHub Actions runner has real
    // internet access and gets a real response rejected by the browser's own
    // CORS policy instead — a differently-worded console message
    // ("blocked by CORS policy") that the net::ERR_*-only filter doesn't
    // match, intermittently failing this test in CI depending on that
    // runner's actual network reachability. Same lesson
    // tests/fx-widget-rates.mjs's own CI flakiness taught: block the call
    // explicitly via page.route() so the outcome is deterministic in any
    // environment, rather than pattern-matching whatever error text a given
    // environment happens to produce.
    await page.route('**bank.gov.ua**', (r) => r.abort());
    await page.route('**allorigins.win**', (r) => r.abort());

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    console.log('[ok] page loaded with stubbed Firebase SDK (auth-gated flow: onAuthStateChanged sign-in)');

    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());

    const bodyText = await page.textContent('body');
    if (!bodyText || bodyText.trim().length === 0) throw new Error('page is blank after sign-in/onboarding');
    console.log('[ok] page not blank after onboarding');

    // "settings" has no #nav-settings bottom-tab button (removed - see
    // CLAUDE.md's Mobile UI redesign section); it's reached via the topbar
    // gear button (#btn-settings) instead, so it's driven separately below.
    const tabs = ['finance', 'shifts', 'debt', 'shopping'];
    for (const tab of tabs) {
      await page.click(`#nav-${tab}`);
      await page.waitForTimeout(300);
      const visible = await page.isVisible(`#tab-${tab}`);
      if (!visible) throw new Error(`#tab-${tab} did not become visible after switching to it`);
      console.log(`[ok] tab "${tab}" renders`);
    }
    await page.click('#btn-settings');
    await page.waitForTimeout(300);
    if (!(await page.isVisible('#tab-settings'))) throw new Error('#tab-settings did not become visible after clicking #btn-settings');
    console.log('[ok] tab "settings" renders (via topbar gear button)');

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
