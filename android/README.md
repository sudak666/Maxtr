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

Ported so far: email/password sign-in + sign-up; and all 5 of the web
app's bottom-nav tabs, in the same order (`MainScreen.kt`):

- **Фінанси** — hero balance, wallet chips, transaction list, add-transaction bottom sheet.
- **Зміни** — month calendar, earned-this-month hero, hours/shifts/days-off chips, per-day shift-type picker.
- **Розрахунки** — debt-switcher chips, balance hero, start/paid/count chips, payment history, add-debt/add-payment dialogs.
- **Покупки** — add row, checkbox list (bought items sort to the bottom), clear-bought, delete.
- **Налаштування** — MVP-thin: account email + sign-out only (reached via a topbar gear icon, not a bottom-nav tab — see "Bottom nav" below). The web app's Settings tab is by far its largest (wallets/categories/budgets/tags/auto-rules/recurring/rates/widgets/PIN/premium/profiles managers) — none of those managers are ported.

**Multi-profile is ported** — `data/profile/ProfileManager.kt` mirrors
the web client's per-device `activeProfileId`/`@<profileId>` doc-suffix
scheme; a "Профілі" chip row in Settings switches between profiles
instantly (every repository's Firestore listener live-resubscribes to
the new profile's doc paths, no restart). One known gap: unlike the web
client's `switchProfile()`, this app doesn't auto-seed default wallets/
categories for a brand-new profile — see `ProfileRepository`'s doc
comment.

**Bottom nav is 4 tabs, not 5** — `MainScreen.kt` mirrors the web client's
own bottom-nav simplification (see root `CLAUDE.md`'s "Mobile UI redesign"
section): Фінанси/Зміни/Розрахунки/Покупки are `NavigationBarItem`s;
Налаштування is reached via a gear `IconButton` in a `TopAppBar` instead,
same idea as the web client's `#btn-settings`. Kept in sync specifically so
the two clients' navigation *model* matches, independent of how much of
Settings itself is actually ported on either side.

**Push notifications are ported** — `ZminkaMessagingService.onNewToken()`
writes to `push_tokens/{uid}` (via the new `data/repository/PushRepository.kt`,
`merge:true` so the Cloud Function's own dedup fields on that doc survive),
mirroring `js/notifications.js`'s `enablePushNotifications()`. `MainScreen.kt`
also calls `registerCurrentToken()` once per screen entry (covers a token
that was minted before this callback existed, or before the user was
signed in) — unlike the web client, there's no separate in-app on/off
toggle; `POST_NOTIFICATIONS` (requested at runtime from `MainActivity` on
Android 13+) is the one real gate, matching how most native apps handle
push rather than the PWA's explicit switch. `onMessageReceived()` builds
and shows a local notification for foreground-delivered pushes exactly
like `js/notifications.js`'s own `onMessage` handler does — a backgrounded
app gets the system tray notification automatically from the FCM SDK
using this app's default icon, since `functions/index.js`'s
`webpush.notification.icon` (the themed per-type PNGs) is a web-push-only
field the Android FCM path ignores; `res/drawable/ic_notification.xml` is
a plain placeholder bell, not a themed set, for the same reason.
`ZminkaApplication.onCreate()` creates the one notification channel this
needs (mandatory on this app's `minSdk = 26`, or `notify()` silently
does nothing).

Still **not yet ported** at all: local PIN/biometric lock, Google/phone
sign-in. See inline doc comments, which point back at the exact
web-client file/function each piece mirrors.

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
