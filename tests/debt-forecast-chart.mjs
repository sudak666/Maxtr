// E2E test for the Debt tab's payoff-forecast widget (js/debt.js's
// renderDebtForecast()) — this app's first Preact-rendered content (see
// CLAUDE.md's "Preact adoption" note). No existing test covered this
// widget at all before this file. Same stubbed-Firebase Playwright recipe
// as tests/debt-swipe-delete.mjs. Run with:
//
//   node tests/debt-forecast-chart.mjs
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
const PORT = 8932;
const SANDBOX_CHROMIUM_PATH = '/opt/pw-browsers/chromium';
const CHROMIUM_PATH = fs.existsSync(SANDBOX_CHROMIUM_PATH) ? SANDBOX_CHROMIUM_PATH : undefined;

const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_APP_CHECK = `export function initializeAppCheck(){ return {}; } export class ReCaptchaEnterpriseProvider{ constructor(){} }`;
const STUB_FIRESTORE = `
const _docs = new Map();
export function getFirestore(){ return {}; }
export function initializeFirestore(){ return {}; }
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
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'debt-forecast-uid', email:'debt-forecast@example.com'})); return ()=>{}; }
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
    await page.route('**/firebasejs/**firebase-app-check.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_APP_CHECK }));
    await page.route('**/firebasejs/**firebase-firestore.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
    await page.route('**/firebasejs/**firebase-auth.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
    await page.route('**/firebasejs/**firebase-messaging.js', (r) => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));

    await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.evaluate(() => window.finishOnboarding && window.finishOnboarding());
    await page.click('#nav-debt');
    await page.waitForTimeout(300);

    await page.locator('[data-action="add-new-debt"]').first().click();
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.fill('#ui-dlg-input', 'Forecast-test debt');
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(200);

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
      await page.locator('.fin-fab[data-action="open-new-debt-entry-modal"]').click();
      await page.waitForSelector('#debt-form-modal', { state: 'visible' });
      await page.fill('#debt-date', dateLabel);
      await page.fill('#debt-amount', amount);
      await page.fill('#debt-balance-input', balance);
      await page.click('[data-action="add-debt-entry"]');
      await page.waitForSelector('#debt-form-modal', { state: 'hidden' });
      await page.waitForTimeout(200);
    }

    function forecastVisible() {
      return page.evaluate(() => getComputedStyle(document.getElementById('debt-forecast')).display !== 'none');
    }

    // ── Not enough signal yet (< 2 entries): widget stays hidden ──
    await addEntry('100', '900', '01.02.2026');
    if (await forecastVisible()) throw new Error('expected the forecast widget to stay hidden with only 1 entry');
    console.log('[ok] forecast widget stays hidden with fewer than 2 payments');

    // ── 2 entries, balance trending down: chart + pace text render ──
    // Series: 1000 (start) -> 900 -> 700. avgDown = (100+200)/2 = 150.
    // currentBalance (last-added entry's balance) = 700.
    // paymentsLeft = ceil(700/150) = 5.
    await addEntry('200', '700', '02.03.2026');
    if (!(await forecastVisible())) throw new Error('expected the forecast widget to become visible with 2 payments');
    const polylinePoints = await page.evaluate(() => {
      const pl = document.querySelector('#debt-burndown polyline');
      return pl ? pl.getAttribute('points').trim().split(/\s+/).length : 0;
    });
    if (polylinePoints !== 3) throw new Error(`expected the burndown chart's polyline to have 3 points (start + 2 payments), got ${polylinePoints}`);
    const paceStrongs = await page.evaluate(() => Array.from(document.querySelectorAll('#debt-forecast-text strong')).map((el) => el.textContent));
    if (paceStrongs.length !== 2) throw new Error(`expected 2 <strong> elements (payments-left, average) in the pace text, got ${JSON.stringify(paceStrongs)}`);
    if (paceStrongs[0] !== '5') throw new Error(`expected payments-left to read "5", got "${paceStrongs[0]}"`);
    if (!paceStrongs[1].includes('150')) throw new Error(`expected the average-payment text to include "150", got "${paceStrongs[1]}"`);
    console.log('[ok] chart renders a real SVG polyline (Preact h()/render(), not stale/empty markup) and the pace text has real <strong> elements with the correct computed values');

    // ── A 3rd, larger entry that fully pays off the debt (balance 0):
    // the "done" state must replace the pace text, not sit alongside it —
    // this is the specific footgun a raw innerHTML= write next to
    // Preact-managed content would risk. ──
    await addEntry('700', '0', '03.04.2026');
    const doneState = await page.evaluate(() => {
      const el = document.getElementById('debt-forecast-text');
      return { doneSpan: !!el.querySelector('.debt-forecast-done'), strongCount: el.querySelectorAll('strong').length };
    });
    if (!doneState.doneSpan) throw new Error('expected the "fully paid off" state to render after a payment brings the balance to 0');
    if (doneState.strongCount !== 0) throw new Error(`expected no leftover <strong> elements from the previous pace-text render, found ${doneState.strongCount}`);
    console.log('[ok] paying off the debt switches to the "done" message and Preact fully replaces the previous pace-text content (no stale <strong> elements)');

    // ── A second debt whose balance only ever grows: avgDown stays <= 0,
    // so the widget shows the chart but a "no clear pace" message instead
    // of a numeric estimate. Once a debt already exists, "add another" is
    // the '+' chip in the debt-switcher row (renderDebtChips(), wired via
    // a plain .onclick= property, not a data-action) rather than the
    // empty-state CTA used above (only present with zero debts). ──
    await page.locator('.debt-chip-add').click();
    await page.waitForSelector('#ui-dialog', { state: 'visible' });
    await page.fill('#ui-dlg-input', 'Growing debt');
    await page.click('#ui-dlg-ok');
    await page.waitForSelector('#ui-dialog', { state: 'hidden' });
    await page.waitForTimeout(200);
    await page.evaluate(() => {
      const body = document.getElementById('debt-info-body');
      if (body && getComputedStyle(body).display === 'none') {
        document.querySelector('[data-action="toggle-debt-info-panel"]')?.click();
      }
    });
    await page.waitForTimeout(150);
    await page.fill('#debt-start', '500');
    await page.locator('#debt-start').dispatchEvent('change');
    await page.waitForTimeout(150);
    await addEntry('-100', '600', '01.05.2026');
    await addEntry('-100', '700', '02.06.2026');
    const noPaceState = await page.evaluate(() => {
      const el = document.getElementById('debt-forecast-text');
      return { strongCount: el.querySelectorAll('strong').length, text: el.textContent };
    });
    if (noPaceState.strongCount !== 0) throw new Error(`expected no <strong> elements in the "no clear pace" state, found ${noPaceState.strongCount}`);
    if (!noPaceState.text.trim()) throw new Error('expected non-empty "no clear pace" text');
    console.log('[ok] a debt that only ever grows shows the chart with a plain "no clear pace" message, not a numeric estimate');

    if (pageErrors.length) throw new Error(`uncaught page errors: ${pageErrors.join(' | ')}`);
    console.log('[ok] no uncaught page errors during the whole flow');

    console.log('\nDEBT FORECAST CHART TEST PASSED');
  } catch (err) {
    console.error('\nDEBT FORECAST CHART TEST FAILED:', err.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
    server.kill();
  }
}

main();
