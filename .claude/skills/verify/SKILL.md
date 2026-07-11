# Verify recipe for this repo (Zminka)

No build step, no test runner (see `CLAUDE.md`). The app is one static
`index.html` that talks to real Firebase (`maxtr-c238f`) over the network.
Verification means: serve the file locally, intercept the Firebase SDK
imports with hand-written stubs, and drive the UI in a real headless
browser. There is no cheaper substitute that's still a real runtime
surface — don't `node --check` and call it done.

## 1. Serve the file

```bash
python3 -m http.server 8899 --directory /home/user/Maxtr &
```
Any free port. Kill it when done (`pkill -f "http.server 8899"`) — ports
leak across turns in this sandbox otherwise.

## 2. Syntax-check before you even launch a browser

App logic now lives under `js/*.js` (16 native ES modules, loaded via
`<script type="module" src="./js/app.js">` — see CLAUDE.md's "`js/`
module layout"), not one inline `<script type="module">` block. `node
--check` only treats a file as an ES module by its extension (`.mjs`) or a
`package.json` "type" field — there's neither here (no root `package.json`
— see CLAUDE.md) — so copy each file to a throwaway `.mjs` path first,
same trick `tests/smoke.mjs`'s `checkModuleScriptSyntax()` already
automates (read that function if you want the exact loop rather than
reimplementing it by hand):

```bash
node -e "
const fs=require('fs');
for(const f of fs.readdirSync('js').filter(f=>f.endsWith('.js'))){
  const tmp='/tmp/'+f+'.check.mjs';
  fs.copyFileSync('js/'+f, tmp);
}
"
for f in /tmp/*.js.check.mjs; do node --check "$f" || echo "FAIL: $f"; done
```
This catches typos before you burn time on a browser session that fails
to boot for a silly reason. It does **not** catch cross-file wiring bugs
(a missing `import`, a name only one file exports) — `node --check` parses
each file in isolation, it doesn't resolve the import graph. The only real
check for that is actually loading the page in a browser (step 4) and
watching for `pageerror` events — a missing/wrong export shows up as
`The requested module './x.js' does not provide an export named 'y'` or a
`ReferenceError`, not a syntax error.

## 3. Stub the Firebase SDK

`js/core.js`, `js/auth.js`, and `js/notifications.js` (Firebase init, auth
methods, and Cloud Messaging respectively) import three-to-four
`firebasejs` URLs by exact path. Intercept each with Playwright's
`page.route` and fulfill with a minimal ES module — real network access to
gstatic.com/Firebase does not exist in this sandbox, and you don't want a
real project write anyway.

```js
const STUB_APP = `export function initializeApp(cfg){ return {}; }`;
const STUB_FIRESTORE = `
export function getFirestore(){ return {}; }
export function doc(){ return {}; }
export async function getDoc(){ return { exists:()=>false, data:()=>({}) }; }
export async function setDoc(){ return; }
export async function deleteDoc(){ return; }
`;
// onAuthStateChanged MUST fire asynchronously (Promise.resolve().then(...)),
// never synchronously. init() references a `let initialized` that's
// declared further down the same module — a synchronous callback fires
// before that line runs and throws a TDZ ReferenceError, taking the whole
// app down. This bit for real once; don't reintroduce it.
const STUB_AUTH = `
export function getAuth(){ return {}; }
export function onAuthStateChanged(auth, cb){ Promise.resolve().then(()=>cb({uid:'test-uid', email:'test@example.com'})); return ()=>{}; }
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
`;
// Only needed if the change touches push notifications.
const STUB_MESSAGING = `
export function getMessaging(){ return {}; }
export async function getToken(){ return 'fake-token'; }
export async function deleteToken(){ return true; }
export function onMessage(){ return () => {}; }
export async function isSupported(){ return true; }
`;

const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
const page = await browser.newPage({ viewport: { width: 390, height: 844 } }); // phone-sized; this is a PWA
page.on('pageerror', err => console.log('PAGEERROR', err.message));
await page.route('**/firebasejs/**firebase-app.js', r => r.fulfill({ contentType: 'application/javascript', body: STUB_APP }));
await page.route('**/firebasejs/**firebase-firestore.js', r => r.fulfill({ contentType: 'application/javascript', body: STUB_FIRESTORE }));
await page.route('**/firebasejs/**firebase-auth.js', r => r.fulfill({ contentType: 'application/javascript', body: STUB_AUTH }));
await page.route('**/firebasejs/**firebase-messaging.js', r => r.fulfill({ contentType: 'application/javascript', body: STUB_MESSAGING }));
await page.goto('http://localhost:8899/index.html', { waitUntil: 'networkidle' });
await page.waitForTimeout(1500); // let init()/fbLoadNow() settle
await page.evaluate(() => window.finishOnboarding && window.finishOnboarding()); // dismiss the first-run carousel, it's on top of everything
```

Chromium binary path is fixed in this sandbox:
`/opt/pw-browsers/chromium-1194/chrome-linux/chrome` (not the path Playwright's
own docs assume). `NODE_PATH=/opt/node22/lib/node_modules node script.js` to
run it — `playwright` is a global install here, not a project dependency.

## Gotchas hit repeatedly

- **Toggle switches** (`.toggle input[type=checkbox]`) are visually covered by
  a styled `<span class="toggle-track">` sibling. A plain `page.click()` on
  the checkbox fails actionability ("intercepts pointer events"). Use
  `page.click(selector, { force: true })`.
- **Module-scope functions aren't callable from `page.evaluate`** unless the
  code explicitly does `window.foo = foo`. Grep the `// ── GLOBALS ──` block
  and each feature section for the `window.x=` line before assuming
  something is reachable — plenty of internal helpers (`showToast`,
  `checkBudgetAlerts`) are deliberately not exposed.
- **Service worker registration for push** (`sw.js`, which now also carries
  the FCM background-message handler — there is no separate
  `firebase-messaging-sw.js` anymore) does real `importScripts()` to gstatic
  — no network here, so it'll fail. Stub `navigator.serviceWorker.register`
  to return a fake registration object when testing push-enable logic in
  isolation; that's a sandbox limitation, not a bug to chase.
- **A fresh `uid` per test file** avoids onboarding/localStorage state
  bleeding between runs (the "seen onboarding" flag, PIN, hide-amounts, etc.
  are all keyed by uid).
- Kill your `http.server` background process when done; stray ones on the
  same port break the next session's test.
