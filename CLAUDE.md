# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

**Zminka** (formerly "Xamss") is a single-file PWA: a personal tracker for work shifts, multi-currency finances, and debt/settlement schedules. It's Ukrainian-first (`uk`/`en` toggle), backed by Firebase (Auth + Firestore + Cloud Messaging + one scheduled Cloud Function), and deployed as static files via Firebase Hosting — there is no build step, bundler, or frontend framework. Almost all product code lives in `index.html`.

## Commands

There is no root `package.json`, no bundler, no linter, and no automated test suite — this is intentional, not an oversight. Treat the app as plain static HTML/CSS/JS.

- **Run locally**: serve the repo root with any static file server (e.g. `python3 -m http.server`) and open `index.html`. Firebase Auth/Firestore calls will hit the real `maxtr-c238f` project unless you intercept the `firebasejs` module imports.
- **Verify a change**: there's no test runner. The established approach in this repo is to drive `index.html` with Playwright against a **stubbed Firebase SDK** — intercept the three `https://www.gstatic.com/firebasejs/.../firebase-{app,firestore,auth}.js` (and `firebase-messaging.js` if touching push) module imports via `page.route()` and fulfill them with minimal hand-written stub modules, then exercise the UI with real clicks/fills and assert on DOM state. This lets you test auth-gated flows without network access or real credentials. Always also do a syntax check before testing, since `<script type="module">` bodies aren't validated by naive `node --check` on the whole file — extract the module script's contents with a regex and check that in isolation.
- **Cloud Function deploy**: `cd functions && npm install && firebase deploy --only functions,firestore:rules` from a machine with `firebase-tools` authenticated against the project (Blaze plan required). See `SETUP.md` for the full one-time Firebase Console setup this depends on (enabling sign-in providers, Cloud Messaging VAPID key, required GCP APIs).

## Architecture

### Layout
- `index.html` — the entire app (styles, markup, and logic in three `<script>` blocks — see below).
- `functions/` — a separate Node 20 npm project (Cloud Functions v2 / `firebase-admin` + `firebase-functions`), deployed independently of the static site.
- `firebase.json` / `firestore.rules` / `manifest.json` — Firebase Hosting, Firestore security rules, and PWA manifest.
- `sw.js` — PWA asset-caching service worker (network-first for HTML, cache-first for everything else).
- `firebase-messaging-sw.js` — a **second, separate** service worker, registered only when push is enabled, that handles background FCM push display. Keep these two service workers' responsibilities separate; don't merge them.
- `privacy.html` / `terms.html` — standalone static pages, not part of the SPA.
- `SETUP.md` — the manual, non-code Firebase Console setup required before auth/push/phone-sign-in work in production (enabling sign-in providers, authorized domains, deploying Firestore rules, VAPID key, required GCP APIs for the scheduled function). Keep this in sync whenever a change adds a new external dependency.

### `index.html` script structure
Three `<script>` blocks, in load order:
1. Two tiny inline classic scripts near the top: apply the saved theme before first paint, and register `sw.js`.
2. A classic (non-module) `<script>` containing the custom monochrome SVG icon library (`ICON_PATHS`, `window.Icon(name)`, `setIcon(id, name)`) plus theme/language toggle wiring. Runs before the module script.
3. The main `<script type="module">` — everything else: Firebase init, all app state, and all feature logic, organized under `// ── SECTION ──` banner comments (search for `// ──` to jump between them: STATE, FIREBASE, AUTH, PHONE SIGN-IN, PIN LOCK, ONBOARDING, BIOMETRIC UNLOCK, INIT, TABS, custom SELECT/DATE PICKER/DIALOGS, CALENDAR, charts, SETTINGS MANAGERS, GOALS, PROFILE, NOTIFICATIONS, PUSH NOTIFICATIONS, FINANCE, ANALYTICS, CSV EXPORT, DEBT). Functions called from inline `onclick`/`onchange` HTML attributes must be attached to `window` explicitly (`window.foo = function(){...}`) since module-scope declarations aren't global — this is why you'll see both a bare `function foo(){}` and a `window.foo=foo;` line for anything wired to markup.

### Firebase data model
- Firestore layout: `users/{uid}/max_tracker/{doc}` where `{doc}` is one of `shifts`, `finance`, `debt`, `backup_v2` (a one-time raw snapshot taken whenever config gets re-seeded). `DCOL = 'max_tracker'`; `userDoc(name)` builds the path. A legacy pre-auth shared `max_tracker/*` collection is explicitly denied in `firestore.rules` — do not resurrect it.
- The `finance` doc is the big one: transactions, wallets, categories, budgets, subcategories, currencyRates, tags, autoRules, recurring, goals, profile, subscription, widgets, **and `notifSettings`** (synced here specifically so the server-side Cloud Function can see each user's notification preferences — it used to be localStorage-only).
- `push_tokens/{uid}` is a separate top-level collection (not nested under `users/{uid}`) holding `{token, sentDaily, sentBudget, sentRecurring}` for the FCM push sweep; it has its own Firestore rule scoped to `request.auth.uid == uid`.
- Client-side caching/sync: every piece of state is mirrored into `localStorage` under per-user keys via `lsKey(name)` → `mx_<name>_<uid>` (loaded first for instant paint), then reconciled against Firestore in `fbLoadNow()`. Writes go through `scheduleSave()` (debounced) → `fbSaveNow()`, which uses optimistic concurrency (`lastKnownUpdatedAt` per doc, compared against each doc's `updatedAt`) and prompts the user on conflict rather than silently overwriting another device's newer save. A few internal localStorage keys still carry the old `xamss*` prefix from before the Zminka rename (e.g. `xamssTheme`, `xamssLang`) — these were left as-is intentionally since renaming them would silently reset existing users' local prefs for zero benefit.

### Auth
Three sign-in methods, all in the AUTH / PHONE SIGN-IN sections: email+password, Google popup, and phone number (SMS code via `signInWithPhoneNumber` + an invisible `RecaptchaVerifier`). Layered on top, independent of Firebase Auth: a local PIN (SHA-256 hash in localStorage, `mx_pin_<uid>`) and optional WebAuthn biometric unlock — these gate the UI locally but never replace the Firebase session.

### Premium / free-tier limits
`subscription = {plan, expiresAt}` lives in the `finance` doc. `FREE_LIMITS` caps wallets/categoriesPerType/autoRules/recurring/goals for `plan:'free'`; `canAddMore(kind, count)` + `showPremiumUpsell(kind)` gate every "add" action. **No payment provider is wired up yet** — premium is a data model + upsell modal only ("Скоро буде доступно").

### Finance-tab widgets
`widgets = {rates, converter, analytics, chart, goals}` (synced in the `finance` doc) toggles which optional sections render on the Finance tab; `applyWidgetVisibility()` is the single place that reconciles the toggle state with the DOM. Adding a new toggleable widget means updating this object's default in every place it's read (there are a few: the Firestore doc load, the localStorage cache load, and the in-memory default) — grep for `widgets.goals` to find all of them.

### Notifications (two independent delivery paths, same three conditions)
Both paths check: daily "did you log anything today" reminder, budget-exceeded, and upcoming-recurring-payment.
1. **Local** (`NOTIFICATIONS` section): fires only while the app is open, via the `Notification` API, checked on load / tab-visibility-change / a 5-minute interval. Dedup uses localStorage keys.
2. **Push** (`PUSH NOTIFICATIONS` section + `functions/index.js`): the client registers an FCM token (`push_tokens/{uid}`) via `getToken()` + `firebase-messaging-sw.js`. A scheduled Cloud Function (`notificationSweep`, hourly) re-implements the same three checks server-side against the `finance` doc and sends real pushes via the Admin SDK, with its own dedup fields stored back on the token doc. If you change the local-notification logic, check whether `functions/index.js` needs the equivalent change — the two are intentionally kept in sync but are separate implementations (client JS vs. Cloud Function), not shared code.

### UI conventions worth following
- Custom-styled `<select>`/`<input type=date>`: any new one just needs `enhanceSelect()` / `enhanceDateInput()` called on it (or add it before `enhanceAllSelects()` runs) — don't hand-roll another dropdown.
- "Settings manager" modals (wallets, categories, budgets, goals, recurring, tags, rules, rates, widgets, pin, premium) all follow the same pattern: a `.modal-overlay` + `.modal-card`, an `open*Manager()` function that renders a list into it and sets `display:flex`, and the shared `closeManagers()` that hides all of them by id — add new manager ids to that shared close-list.
- All icons are the custom monochrome SVG set (`window.Icon(name)` / `setIcon(id, name)`) — no emoji, no icon fonts. Add new glyphs to `ICON_PATHS` rather than reaching for an emoji.
- i18n: a single `I18N = {uk:{...}, en:{...}}` object plus `tr(key)`; static markup uses `data-i18n`/`data-i18n-placeholder` attributes applied by `translateStaticDOM()`. There is no `data-i18n-title` — dynamic titles/tooltips must be set in JS.

## Known gaps / pending work

- **No payment provider wired up.** Premium is upsell-only right now. Plan discussed: Stripe first (still a PWA), RevenueCat later once/if this ships to app stores (Apple/Google require native IAP there).
- A GCP service account `claude-deploy@maxtr-c238f.iam.gserviceaccount.com` exists specifically so an agent session without interactive `firebase login` access can run `firebase deploy`. Its roles (Cloud Functions Developer/Admin, Firebase Rules Admin, Cloud Run Admin, Service Account User) were granted incrementally to get a functions deploy working — expect to hit a "permission denied enabling X API" error on the *first* deploy after any GCP-side change and grant the missing role/API as prompted rather than pre-guessing the full set.
- Debt/settlement entries have no structured due-date field (the date is a freeform text label), so there's no way to add a "debt due soon" notification (unlike budgets/recurring) without first adding a real date field to that data model.
- Facebook sign-in and a "savings goal" style debt reminder were discussed and deliberately deferred, not forgotten.
