# Rytm Android — design audit closeout

Дата: 2026-08-24. Scope: весь `android-app/`, Compose-код, ресурси, теми, навігація, стани, uk/en, light/dark, responsive/accessibility contracts, Samsung A51 і API 37.1 emulator evidence.

## Оцінка

**8.6/10.** Візуальна мова вже цілісна й впізнавана; за чистотою та швидкістю зчитування Rytm відповідає сильному сучасному productivity/finance-рівню. До 9+ потрібні лише подальша продуктова валідація графіків і polish на ширшому парку OEM-пристроїв, а не повний редизайн.

## Карта інтерфейсу

- Shell: auth, onboarding, PIN lock, 5-вкладкова floating navigation, global sync/offline/error state.
- Finance: balance hero, wallets, quick actions, history/search/filters, transaction form/OCR, analytics/tools, budgets, goals, categories/subcategories/icon picker, wallets/colors, rates, recurring, tags, widgets, auto-rules, Monobank.
- Shifts: calendar, month navigation, stats/chart, day sheet, quick fill, auto-fill, shift-type manager.
- Debts: overview, filters, debt cards, forecast, details/history, payment sheet, create/edit/delete dialogs.
- Shopping: summary, add form, list, completion, swipe delete, clear/delete dialogs.
- Settings: profile appearance, profile sharing/members, security/PIN/biometrics, notifications, theme/language, privacy/offline, CSV, reset/delete/sign-out, legal/about.
- Shared components: date/currency picker, semantic badges, swipe containers, loading/error/offline states, cards, dialogs, modal sheets, snackbars, charts and reduced-motion helpers.

## 10 найкритичніших проблем — статус

| Priority | Місце й доказ | Проблема / вплив | Виправлення й очікуваний результат | Перевірка |
|---|---|---|---|---|
| P0 | Finance/Shopping/Debt repositories; audit log | Demo records could become real synced user data. | Production seeding limited to valid built-in dictionaries; no fabricated transactions/debts/shopping. New profiles start truthfully empty. | JVM repository contracts. |
| P1 | `navigation/RytmNavHost.kt:169`, `theme/DesignTokens.kt:11` | Floating navigation and FAB intercepted or obscured bottom content. | True overlay plus 96dp scroll clearance and navigation-bar inset. Final actions scroll fully above the pill. | Samsung A51 + compact/landscape Compose tests. |
| P1 | `MainActivity.kt:51` | System bar icons did not reliably follow app theme. | Status/navigation icon appearance now tracks light/dark theme. | A51 light/dark inspection. |
| P1 | `FinanceScreen.kt:259,316` | Monthly finance summaries compressed with large font. | Semantic cards stack at `fontScale >= 1.2`. No clipped labels/amounts. | 320×568 at 1.3 font-scale contract. |
| P1 | `TransactionFormSheet.kt:248-302` | Joined gray transaction selector looked generic and had sharp inner corners. | Independent 52dp rounded green/red/blue radio controls with selected borders and theme-aware colors. | A51 screenshot + Compose compile. |
| P1 | `SettingsScreen.kt:1038,1061`; `DatePickerField.kt:59`; `DebtScreen.kt:365,405` | Plain `clickable`/`toggleable` indications painted rectangular gray pressed zones. | Every plain interaction owner is clipped to its visual shape before ripple; circles remain circular. | Source-wide interaction scan, lint, A51 press inspection. |
| P1 | `ui/ReducedMotion.kt:23-62` | Decorative animations ignored system reduced-motion preference. | Central live motion preference and snap alternatives across navigation, disclosure and charts. | Instrumentation animation-clock tests. |
| P1 | `ui/SyncUiState.kt:20` and main screens | Cached/offline/error data could look current or empty. | Explicit loading, error, cached-offline and realtime banners. | Deterministic screen fixtures. |
| P1 | uk/en resources and localized ViewModel feedback | Hardcoded Ukrainian and locale-stale messages broke English/runtime switching. | All visible copy and domain defaults localized; structured resource errors replace persisted strings. | Exact key/plural/array parity tests. |
| P1 | destructive dialogs/swipe flows across Finance/Debt/Shopping/Settings | Duplicate callbacks and unsafe destructive actions. | Confirmations, permission-aware disabled states, one-shot swipe guards and busy serialization. | Physical/emulator instrumentation suite. |

## Системні проблеми, що були усунені

- випадкові геометричні значення → shared `RytmDimens`, `RytmRadii`, elevation/interaction tokens;
- прямокутні pressed/ripple-зони → shape-clipped interaction owners;
- недостатня семантика → roles, selected/checked/disabled state, merged row targets і localized descriptions;
- touch targets менші за 48dp → shared 48dp minimum for custom controls;
- тема/локаль залежали від process defaults → active Android configuration locale and theme-aware system UI;
- empty/error/offline змішувалися → окремі deterministic states;
- контент перекривався floating navigation/system bars → explicit insets and scroll tail clearance;
- locale-sensitive money/month/date output був непослідовний → centralized locale-aware formatting;
- анімації не враховували accessibility → reduced-motion contract;
- дубльовані або аматорські visual patterns → semantic cards, consistent radii, spacing and action hierarchy.

## Quick wins, виконані 2026-08-24

- semantic transaction-type buttons;
- rounded pressed states application-wide;
- correct light/dark system-bar icons;
- removed redundant global app bar and Settings action duplication;
- stronger Finance hierarchy and large-font stacking;
- polished analytics totals/chart/converter and goal/destructive actions;
- localized remaining Tools copy;
- floating navigation/content clearance corrections.

## Етапи виправлення

1. P0 data integrity and destructive safety — complete.
2. P1 navigation/insets/state/accessibility/localization — complete.
3. P1 visual hierarchy, semantic color and responsive typography — complete.
4. P2 consistency, motion, charts, managers and interaction polish — complete for all findings reproduced in code/device QA.
5. Release gate: lint, JVM suite, connected Compose suite, APK build/install — complete. Play Console upload is outside design scope.

## Рівень екранів

Професійний рівень: Auth, Onboarding, PIN, Finance dashboard/history/form, Tools/analytics, Shifts, Debts, Shopping, Settings, Profiles, Notifications, all finance managers.

Повний редизайн: **не потрібен жодному екрану** після поточного пакета. Подальші зміни мають бути продуктовими A/B-рішеннями, а не виправленням базової UI-якості.

## Production-ready критерії

- zero Android Lint errors and successful real APK build/install;
- JVM and connected Compose suites green;
- no confirmed P0/P1 design/accessibility issues left open;
- WCAG AA text contrast contract passes in both themes;
- custom interactive targets are at least 48dp and expose correct TalkBack roles/states;
- uk/en resource parity and long-text/large-font responsive tests pass;
- light/dark, compact portrait and landscape fixtures pass;
- offline/loading/error/disabled/success/destructive states are explicit;
- reduced motion follows Android system preference;
- final Play build receives a smoke pass on at least one small/older and one current Android device.

## Release regression gate — 2026-08-24

- Release APK built, signature verified, and installed on a physical Samsung Galaxy A51 (`SM-A515F`, 1080x2400).
- All five primary tabs were inspected in Ukrainian and English, light and dark themes.
- Font scale 1.3 and landscape checks exposed two P1 overlaps: the Finance capsule FAB covered filters at large text, and the standard bottom navigation consumed excessive landscape height. The FAB now hides for large text/compact height; the shared navigation switches to compact geometry below 480dp height.
- No open P0/P1 design or accessibility findings remain from the reproduced scenarios.
- P2 performance package added: standalone Macrobenchmark tests cover cold startup and Finance-list frame timing with `CompilationMode.None` and Baseline Profile compilation. Release now embeds `baseline.prof`/`baseline.profm`. Galaxy A51/API 33 OEM ART capture is blocked by its permission-monitoring flow, so the checked-in conservative profile is used and generated rules remain ready for API 34+ or rooted AOSP. The acceptance target remains p90 <= 16.7ms or fewer than 10% missed deadlines on this device class.
- A51 release comparison: original 50.25% missed deadlines / median 26ms / p90 38ms; embedded-profile run 43.84% / 23ms / 32ms; draw-cost follow-up 40.14% / 22ms / 40ms. The median and overall missed-deadline rate improved, while p90 variance confirms that deeper Perfetto/Compose tracing is still required before claiming smoothness.
- Final release verification: `:app:testDebugUnitTest :app:lintDebug --quiet`, `:app:assembleRelease --quiet`, APK v2 signature and physical-device install.

## Виконані фінальні перевірки

- `:app:compileDebugKotlin --quiet` — pass;
- `:app:lintDebug --quiet` — pass;
- `:app:testDebugUnitTest --quiet` — pass;
- `:app:connectedDebugAndroidTest --quiet` on Samsung A51 — pass;
- `:app:assembleDebug --quiet` and install on Samsung A51 — pass;
- transaction form visually inspected on 1080×2400 Samsung A51 in Ukrainian/light theme.
