# Zminka — Android (native)

A genuinely native Android app (Kotlin + Jetpack Compose), **separate from**
the existing PWA (`../js/`, `../index.html`) and separate from the existing
TWA Play Store wrapper described in the root `CLAUDE.md`'s "Android /
Google Play packaging" section. All three now coexist:

1. The PWA itself, reachable from any browser.
2. The TWA wrapper (`ua.zminka.app`, generated on demand via `bubblewrap`,
   not checked into this repo — just opens the live PWA full-screen).
3. **This** — a real native app with its own UI, reading/writing the same
   Firebase project (`maxtr-c238f`) and the same Firestore data model
   (`users/{uid}/max_tracker/...`, see the root `CLAUDE.md`'s "Firebase
   data model" section) so all three surfaces stay in sync as different
   clients of the same account.

## Current scope (MVP)

Ported so far: email/password sign-in + sign-up, and the Finance tab
(hero balance, wallet chips, transaction list, add-transaction bottom
sheet). Everything else the web app has — Shifts/calendar, Debt,
Shopping, Settings, multi-profile, push notifications, local PIN/biometric
lock, Google/phone sign-in — is **not yet ported**. See inline `TODO`
comments (e.g. `data/repository/ZminkaMessagingService.kt`) and the
per-file doc comments, which point back at the exact web-client
file/function each piece mirrors.

The data layer intentionally targets only the **default profile**
(no `@<profileId>` doc suffix) for now — see `FinanceRepository`'s doc
comment for how to extend it once multi-profile support is ported.

## Why this can't be built from this cloud sandbox

Per the root `CLAUDE.md`, this environment's egress proxy blocks
`dl.google.com` (Android SDK/build-tools) and other non-API Google/GitHub
hosts, so `./gradlew build` cannot run here. All code was written
directly as source files; the first real build/run has to happen locally.

## One-time local setup

1. **Android Studio** (includes JDK, Android SDK, Gradle) — this is by far
   the easiest way to get a working toolchain; open this `android/`
   directory as a project (`File > Open`) and let it sync.
2. **`google-services.json`** — not committed (see `.gitignore` and
   `app/google-services.json.example` for why: it embeds an
   Android-app-specific client ID tied to a package name + registered app
   in the Firebase console, which doesn't exist yet). To get a real one:
   - Firebase Console → project `maxtr-c238f` → Project settings → Your
     apps → Add app → Android.
   - Package name: `ua.zminka.app` (matches `applicationId` in
     `app/build.gradle.kts` — keep them in sync if you rename the package).
   - Download the generated `google-services.json` and place it at
     `android/app/google-services.json`.
   - Debug builds also need the debug signing cert's SHA-1/SHA-256 added
     to that Firebase Android app's config (Android Studio's Gradle panel
     → `app` → `Tasks` → `android` → `signingReport`, or
     `./gradlew signingReport`) — without it, Firebase Auth's
     Google/phone sign-in providers reject the app; email/password auth
     works without this step.
3. **Run**: select the `app` run configuration and hit Run — no separate
   backend/emulator setup needed beyond the Firebase project above, since
   it talks to the same live `maxtr-c238f` project the PWA does.

## Package ID / app name / icon

Still the `ua.zminka.app` placeholder and default launcher mark (see
`app/src/main/res/drawable/ic_launcher_foreground.xml`'s comment) — the
account owner deferred both the real app name and a real icon until the
Google Play developer account is purchased, same as the TWA build's own
placeholder (root `CLAUDE.md`). Update `applicationId` in
`app/build.gradle.kts`, `app_name` in `res/values/strings.xml`, and the
launcher icon resources together when that happens — and re-download
`google-services.json` if the package name changes, since it's tied to
the exact package name registered in Firebase.
