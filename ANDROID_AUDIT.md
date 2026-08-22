# Android quality audit

Status: active remediation, started 2026-08-22. Target: production-grade 10/10, with every item closed by code plus proportional automated/device verification. No item is considered complete from compilation alone.

## P0 — release blockers

- [ ] Remove the plaintext Monobank token from Firestore; store it only with Android Keystore-backed encryption, migrate/delete legacy remote tokens, and test reconnect/sync/disconnect. (Remote serialization excludes the token; migration writes synchronously before remote deletion; real Keystore encryption/isolation/delete instrumentation passes. Firestore migration plus live reconnect/sync/disconnect E2E remains.)
- [ ] Add Firebase App Check with Play Integrity for release and debug provider support for local/emulator testing; document staged enforcement. (Release/debug providers are wired; emulator debug token is registered and a live token exchange passes. Play Integrity provider registration, metrics review and staged service enforcement still require Firebase Console verification.)
- [x] Replace `fallbackToDestructiveMigration` with explicit, tested Room migrations (authoritative v13 schema regenerated from historical commit `1f87df7`; connected 13→14→15 migration preserves data and validates the final schema/indexes. v14 was an internal transition and never a published database version.)
- [x] Disable Android backup for financial, authentication, PIN and integration data.
- [x] Add Firebase-emulator E2E coverage for two-account realtime shared-profile sync, viewer denial, editor writes, reconnect and conflicts (`SharedProfileFirebaseE2eTest`: two anonymous Auth clients, server reads, snapshot listener, role transition, network disable/enable and concurrent Firestore transactions against deployed rules).
- [ ] Verify the final release on a real Samsung A51 before production rollout.

## P1 — security, reliability and scale

- [x] Replace fast unsalted PIN hashing with Keystore-backed verification, escalating persistent attempt throttling, background re-lock and privacy protection in recents.
- [ ] Enable R8/resource shrinking and verify every auth, Firestore, FCM, ML Kit and Credential Manager flow in release. (Enabled. A dedicated `connectedReleaseAndroidTest -PtestRelease=true` gate now runs against the signed, minified/resource-shrunk APK and proves `MainActivity` plus Auth, Firestore, FCM, App Check, Credential Manager and ML Kit entry points survive R8 on-device. Fresh `assembleRelease` + `lintVitalRelease` and the gate pass on `emulator-5554`; authenticated CRUD, real FCM delivery, OCR image and credential-picker release flows still need end-to-end device verification.)
- [ ] Replace full-domain realtime reloads with domain-specific listeners, explicit revisions/conflict policy and durable offline outbox/retry. (Domain-specific listener dispatch completed; revisions/outbox/conflict tests pending.)
- [x] Make Room data profile-scoped (`ownerUid` + `profileId`) so profiles remain isolated and available offline (Room v16 adds composite scope keys to all 17 tables; every DAO read/update/delete is scope-bound; repository Flows rebind on profile changes; legacy v15 rows are adopted once; account/privacy-cache clearing still removes every retained scope. Connected 13→16 migration and same-ID cross-profile isolation tests pass).
- [ ] Add structured error types, safe diagnostics/Crashlytics redaction and actionable retry states. (Realtime/cold sync now classifies network/auth/permission/rate-limit/conflict/data failures, logs only stable redacted codes, localizes actionable messages and exposes tested retry. Remaining feature-specific error paths and crash reporting policy pending.)
- [ ] Show operation-level local/pending/synced/error state instead of only a global sync banner.

## P2 — product and PWA parity

- [x] Move recurring payments and shift auto-fill to idempotent WorkManager/server-safe scheduling with timezone/DST coverage (unique daily local worker; atomic Room writes; deterministic recurring occurrence IDs; explicit local-date injection with Kyiv spring/autumn DST tests). Background Firestore snapshot egress remains deliberately excluded until its exact payload is separately approved; normal foreground sync uploads generated rows.
- [x] Centralize locale/currency-aware money formatting; remove manually concatenated `грн`/`UAH` strings (shared locale-aware amount/currency/signed/input APIs used across Finance, Tools, Debt, Shifts, budgets, recurring operations and transfer previews).
- [ ] Complete pixel-level PWA parity for every screen, sheet, state and theme; retain the restored Tools card/category/converter/chart design.
- [ ] Add bulk edit, undo, encrypted backup/restore and reliable import/export recovery. (Bulk transaction selection, bulk category edit/delete and remote-aware undo are implemented and interaction-tested. Portable all-17-table active-profile backup uses AES-256-GCM + PBKDF2, strict schema/column validation, atomic scope-safe restore, and passing crypto/round-trip/tamper tests. Settings now exports a password-protected `.rytmbackup` through the system document picker and clears plaintext password/ciphertext buffers after use. Restore UI plus explicit approval for destructive active-profile cloud replacement remain.)
- [x] Add app links, launcher shortcuts, share-to-Rytm and useful widgets where they reduce entry friction (validated `rytm://` routes plus verified `https://maxtr-c238f.web.app/app/*`; hosted assetlinks includes the signed release AAB SHA-256; static shortcuts cover new transaction/shifts/shopping; text sharing opens a safe prefilled transaction draft; privacy-safe home widget exposes the same three actions without financial values. Manifest/parser tests and launcher provider registration pass; debug HTTPS verification intentionally remains off because the debug certificate is not delegated).
- [x] Automate release versioning and signed AAB verification (`version.properties` is the single version source; `:app:verifyReleaseBundle` builds the AAB, rejects missing signing/version regressions, verifies every payload entry and matches the signer certificate to the configured upload key).

## P3 — UX, accessibility and maintainability

- [ ] Complete TalkBack audit: icon-only action descriptions, focus order, 48dp targets, dynamic text, contrast and non-color cues. (Static icon-action/touch-target scan completed; theme body/muted contrast is WCAG-AA tested. Shift quick-fill now exposes button state and a 48dp target; calendar days announce the full localized date and assigned shift names. Full manual TalkBack traversal and remaining dynamic-state audit pending.)
- [x] Give every Canvas chart a semantic summary and accessible data alternative (all finance/debt values exposed to TalkBack; redundant crypto sparkline is decorative beside textual price/change).
- [ ] Standardize spacing, sheet headers/actions, empty/loading/error states, typography, icon mapping and motion.
- [ ] Split oversized coordinators/ViewModels/sheets; move migration-history comments out of production code.
- [x] Add sticky/collapsible navigation or a dedicated screen for long Tools content (fixed four-section chip navigation remains visible above a lazy list and jumps to Analytics, Rates, Converter or 6-month chart; emulator interaction test covers forward/back navigation).

## Verification matrix

- [ ] Unit tests for all business rules, formatting, recurrence, timezones and conflicts.
- [ ] Room DAO and migration tests for every schema version.
- [ ] Firestore rules/emulator integration tests, including App Check rollout assumptions.
- [ ] Compose interaction tests for all primary CRUD flows and process recreation.
- [ ] Golden tests for all main screens and sheets: uk/en, light/dark, 320dp, standard phone, large font and landscape.
- [ ] Accessibility tests plus manual TalkBack pass.
- [ ] Large-dataset/performance/startup/memory/network-failure tests.
- [ ] R8 release smoke test, signed APK/AAB verification and final physical-device regression. (Automated signed/minified APK SDK+activity smoke and signed AAB verification pass; full feature smoke and Samsung A51 regression remain.)

## Competitive quality bar

Rytm must match the mature baseline of Wallet/Spendee/Money Manager for safe sync, budgets, recurring operations, import/export, insights, accessibility and recovery, while exceeding it in Ukrainian-first UX, shared-profile roles, integrated work-shift tracking and transparent privacy/security.
