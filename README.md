# Rytm

A mobile-first personal tracker for work shifts, multi-currency finances, and
debt/settlement schedules — Ukrainian-first (`uk`/`en` toggle), installable as
a PWA and packaged as a Trusted Web Activity for Google Play.

**Live**: [maxtr-c238f.web.app](https://maxtr-c238f.web.app) · mirrored on [GitHub Pages](https://sudak666.github.io/Maxtr/)

## What it does

- **Finance** — transactions, multiple wallets/currencies, categories, budgets, tags, auto-categorization rules, recurring payments, savings goals, CSV export, a live NBU/PrivatBank exchange-rate converter, and a balance chart with a simple trend-based forecast.
- **Shifts** — a calendar for tracking work shifts and hours, with configurable shift types and quick-fill templates.
- **Debt / settlements** — payment schedules with a structured due date, payoff progress bar, and reminders.
- **Shopping list**.
- **Multiple profiles** per account (e.g. separate finances for two family members), local PIN + biometric unlock on top of Firebase Auth, and both in-app and push notifications for daily reminders, budget overruns, and upcoming payments.

## Stack

Deliberately dependency-light: plain HTML/CSS/JS, no bundler, no framework, no build step. The browser's native ES modules do the file splitting. Backed by Firebase (Auth, Firestore, Cloud Messaging, one scheduled Cloud Function) and deployed as static files via Firebase Hosting (with a GitHub Pages mirror).

| Layer | Tech |
|---|---|
| UI | Static HTML/CSS + vanilla JS ES modules (`js/*.js`) |
| Backend | Firebase Auth, Firestore, Cloud Messaging |
| Serverless | One scheduled Cloud Function (Node 20), plus a small HTTPS proxy for a CORS-blocked upstream |
| Hosting | Firebase Hosting + GitHub Pages (static mirror) |
| Android | Trusted Web Activity (TWA) wrapping the live site |

See [`CLAUDE.md`](./CLAUDE.md) for the full architecture reference (module layout, data model, deploy recipes, and the conventions this codebase follows) and [`CHANGELOG.md`](./CHANGELOG.md) for the detailed history of what's shipped.

## Running locally

No build step — serve the repo root with any static file server and open `index.html`:

```bash
python3 -m http.server
```

Firebase Auth/Firestore calls hit the real project unless you intercept the SDK's module imports (see Testing below for how the test suite does this).

## Testing

```bash
node tests/unit.mjs           # pure helpers (currency conversion, timezone/date logic)
node tests/privacy-cache.mjs  # local privacy-cache helper behavior (no browser/Firebase)
node tests/smoke.mjs          # stubbed-Firebase Playwright pass: all 5 tabs render, no console errors
node tests/e2e-crud.mjs       # Finance tab: create/edit/delete a transaction end-to-end
node tests/e2e-modals.mjs     # settings-manager modal open/close behavior
node tests/functions-sweep.mjs   # Cloud Function notification-sweep logic (fake db, no credentials needed)
node tests/firestore-rules.mjs   # Firestore security rules against a real local emulator
```

All but the last run in CI on every push/PR (`.github/workflows/smoke-test.yml`); the rules test runs as a separate CI job against a real Firestore emulator. There is no linter or bundler by design — see `CLAUDE.md` for why.

## Deployment

| Target | Command |
|---|---|
| Firebase Hosting | `firebase deploy --only hosting --project maxtr-c238f` |
| Firestore rules | `firebase deploy --only firestore:rules` |
| Cloud Functions | `cd functions && npm install && firebase deploy --only functions` |
| GitHub Pages | automatic on every push to `main` |

See `CLAUDE.md`'s Commands section for the exact recipes (clean worktree requirements, IAM notes, etc.) before deploying.

## Project structure

```
index.html          Markup, CSS, and the module-script entry point
js/                  App logic — 16 native ES modules, no bundler
functions/           Cloud Functions (separate Node project)
firebase.json        Hosting, Firestore rules, and header/CSP config
firestore.rules      Firestore security rules
tests/               Automated test suite (see Testing above)
scripts/             One-off operator scripts (not part of the app or CI)
landing.html         Marketing landing page
```

## License

No license file yet — all rights reserved by default until one is added.
