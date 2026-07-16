// On-device AI category suggestion test (js/finance.js's
// maybeSuggestCategoryWithAI()). Chrome's Prompt API (self.LanguageModel)
// isn't present in this sandbox's Chromium, so this stubs it via
// page.addInitScript to exercise the real code path instead of only
// proving "does nothing when the API is absent" (which every other test
// already does implicitly, just by never erroring). Same stubbed-Firebase
// Playwright recipe as tests/e2e-crud.mjs. Run with:
//
//   node tests/ai-category-suggestion.mjs
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
const PORT = 8903;
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'ai-test-uid', email:'ai@example.com'})); return ()=>{}; }
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

// Stubs Chrome's Prompt API well enough to exercise
// maybeSuggestCategoryWithAI()'s real control flow: availability check,
// session create with a system prompt, then session.prompt(...) returning
// a fixed answer. Records every prompt() call so the test can assert on
// what the app actually asked for.
const LANGUAGE_MODEL_STUB = (answer) => `
window.__aiPromptCalls = [];
window.LanguageModel = {
  async availability(){ return 'available'; },
  async create(opts){
    return {
      async prompt(text){ window.__aiPromptCalls.push(text); return ${JSON.stringify(answer)}; },
    };
  },
};
`;

async function withStubbedAI(browser, answer, run) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
  const pageErrors = [];
  page.on('pageerror', (err) => pageErrors.push(err.message));
  await page.addInitScript(LANGUAGE_MODEL_STUB(answer));
  await page.route('**/firebasejs/**firebase-app.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
  await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
  await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
  await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
  await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));
  await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1200);
  await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
  await page.click('#nav-finance');
  await page.waitForTimeout(200);
  await page.click('.fin-fab');
  await page.waitForSelector('#tx-form-modal', { state: 'visible' });
  await run(page);
  if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
  await page.close();
}

async function main() {
  const server = spawn('python3', ['-m', 'http.server', String(PORT), '--directory', ROOT], { stdio: 'ignore' });
  await new Promise((r) => setTimeout(r, 500));
  const browser = await chromium.launch(CHROMIUM_PATH ? { executablePath: CHROMIUM_PATH } : {});
  try {
    // ── a long, rule-free comment gets the stubbed model's suggested category ──
    await withStubbedAI(browser, 'Кафе', async (page) => {
      await page.fill('#fin-comment', 'Обідали з колегами в новому закладі біля офісу');
      await page.waitForFunction(() => document.getElementById('fin-category')?.value === 'Кафе', { timeout: 5000 });
      const calls = await page.evaluate(() => window.__aiPromptCalls);
      if (!calls.length) throw new Error('expected session.prompt() to have been called');
      if (!calls[0].includes('Кафе')) throw new Error(`expected the prompt to list "Кафе" among the categories, got: ${calls[0]}`);
      console.log('[ok] a long rule-free comment gets categorized by the stubbed on-device model');
    });

    // ── a short comment never triggers a prompt call ──
    await withStubbedAI(browser, 'Кафе', async (page) => {
      await page.fill('#fin-comment', 'кава');
      await page.waitForTimeout(1200);
      const calls = await page.evaluate(() => window.__aiPromptCalls);
      if (calls.length) throw new Error(`expected no prompt() call for a short comment, got ${calls.length}`);
      console.log('[ok] a short comment does not trigger an AI call');
    });

    // ── the model must never invent a category outside the app's list ──
    await withStubbedAI(browser, 'Not a real category', async (page) => {
      const before = await page.locator('#fin-category').inputValue();
      await page.fill('#fin-comment', 'Оплатив щось незрозуміле через додаток на телефоні');
      await page.waitForTimeout(1200);
      const after = await page.locator('#fin-category').inputValue();
      if (after !== before) throw new Error(`expected the category to stay unchanged when the model returns an unrecognized answer, went from "${before}" to "${after}"`);
      console.log('[ok] an out-of-list model answer is ignored, category field stays unchanged');
    });
  } finally {
    await browser.close();
    server.kill();
  }

  console.log('\nAI CATEGORY SUGGESTION TEST PASSED');
}

main().catch((err) => {
  console.error('\nAI CATEGORY SUGGESTION TEST FAILED:', err.message);
  process.exitCode = 1;
});
