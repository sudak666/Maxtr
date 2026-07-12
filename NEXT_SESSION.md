# Handoff — start here for the next session

Short pointer doc. Full history/root-causes/verification for everything below already live in `CLAUDE.md`'s "Known gaps / pending work" section (search for the PR number) — this file is just the fast-scan index so a new session doesn't have to read that whole log before doing anything.

## Repo state as of 2026-07-12

- `main` is fully merged, tested, and deployed to all live targets (Firebase Hosting `maxtr-c238f.web.app`, GitHub Pages mirror, Firestore Rules, Cloud Functions). Nothing pending in flight.
- `sw.js` `CACHE_NAME` is at **`zminka-v21`**. If you change any `js/*.js` or `index.html` behavior, bump this again — it's the single most-often-forgotten step in this repo (see the standing rule in CLAUDE.md, flagged repeatedly).
- Designated working branch: `claude/mobile-app-buttons-broken-i2ahtn`, currently reset to match `origin/main` (no unmerged work sitting on it). (This particular fix landed via a separate branch, `claude/untitled-session-x1ofpo`, PR #142 — also already merged/deployed.)

## The one open decision waiting on the user

**Content-Security-Policy is still `Content-Security-Policy-Report-Only`, not enforcing**, in `firebase.json`. Plan (see CLAUDE.md's CSP entries for full detail):
1. User checks the real browser console on `maxtr-c238f.web.app` — real Google Sign-In popup, real phone-auth reCAPTCHA, real FCM push registration, real NBU/PrivatBank rate fetches.
2. If clean (no `Content-Security-Policy` violation reports — a few harmless unrelated warnings are expected and already explained: `Permissions-Policy: interest-cohort` cosmetic Chrome quirk, `Cross-Origin-Opener-Policy ... window.closed` from `popup.ts` reCAPTCHA/Google-popup internals, `api.privatbank.ua` CORS failures which are PrivatBank's server, not us).
3. User says "переключай" → change the header key in `firebase.json` from `Content-Security-Policy-Report-Only` to `Content-Security-Policy`, redeploy hosting from a clean `origin/main` worktree.

Do **not** flip this preemptively — it needs the user's explicit go-ahead after confirming the real auth/push flows are clean, not just an absence-of-violations screenshot.

## Today's session (2026-07-12) — bug-fix pass from real-device screenshots

All merged, deployed, and logged in CLAUDE.md with root cause + verification method. In order:
- **PR #133** — wallet names collapsing to ~1 letter in the tx list (real root cause: `.tx-right` ate 65% of row width; restructured into 2 stacked rows).
- **PR #135** — calendar shift-badge overflow (added ellipsis truncation) + dropped `umbrella` from the random category-icon fallback pool (a category was hashing onto it, looked like a bug).
- **PR #137** — quick-action row: 4 buttons now `justify-content:space-between` instead of clustering left with dead space on the right.
- **PR #139** — disabling push notifications threw a 404 (`firebase-messaging-sw.js` doesn't exist — merged into `sw.js` long ago). Root cause confirmed by reading the actual Firebase JS SDK source on GitHub: `deleteToken()` takes no options argument, only checks `messaging.swRegistration`; fixed by setting that property directly before calling it.
- **PR #142** — "ПриватБанк (готівка)" rates source always silently failed (CORS, no fallback). Fixed by adding the same `api.allorigins.win` relay fallback `fetchLiveRates()` already uses for NBU.

Each of these came from the user pointing a phone camera/screenshot at a real, specific problem — the standing lesson (documented multiple times in CLAUDE.md already) is to **measure real rendered widths / read the actual SDK source** rather than guessing from the CSS or API docs alone; several of these bugs were invisible from a read-through and only surfaced that way.

## If starting new work this session

1. `git fetch origin main && git checkout -B claude/mobile-app-buttons-broken-i2ahtn origin/main` first — don't build on stale local history (squash-merges break `git merge-base`, see CLAUDE.md's branch-hygiene notes).
2. Run the full local suite before opening any PR: `node tests/unit.mjs && node tests/smoke.mjs && node tests/e2e-crud.mjs && node tests/e2e-modals.mjs && node tests/functions-sweep.mjs`.
3. Bump `sw.js` `CACHE_NAME` for any `js/*.js`/`index.html` change.
4. Log what you did in `CLAUDE.md`'s "Known gaps / pending work" section before ending the turn (session-logging convention at the top of that file) — this file (`NEXT_SESSION.md`) is a short index, not a replacement for that log.

## Bigger, still-untouched items (not started, just tracked)

- **Google Play submission**: TWA package built, `.well-known/assetlinks.json` live, but Play Console developer account not yet created by the user ($25, manual). Package ID `ua.zminka.app` still a placeholder.
- **Payments (Stripe)**: no provider wired up at all. Needs the user to have/create a Stripe account and decide what Premium actually includes — free-tier caps were fully removed earlier, so there's currently no gated feature to sell without a product decision first.
- **Transactions → Firestore subcollection migration**: plan + non-destructive backfill script exist (`MIGRATION_PLAN_transactions.md`, `scripts/backfill-transactions.mjs`), never actually run against production, no app code reads the new shape yet. Bigger, separate piece of work.
