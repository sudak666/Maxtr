# ANDROID_MIGRATION.md — Rytm Native Android

Ціль: нативний Android-клієнт (Kotlin, Jetpack Compose + Material 3, MVVM/Clean, Room + DataStore) зі 100% функціоналом PWA `Rytm`, з покращеннями під нативні Android-патерни там, де PWA-рішення були браузерним компромісом.

Директорія: `./android-app`.

## Почни звідси наступну сесію

**Статус (оновлено після кроку 32, 2026-08-21)**: Кроки 1-32 зроблені (кроки 1-31 **реально перевірені наскрізь**; крок 32 — compile+UI+crash-fix перевірені, повний invite/redeem round-trip НЕ підтверджений через середовищну GMS-проблему на цьому AVD, див. крок 32 нижче). Working screens: Фінанси (підкатегорії, теги з бейджами в списку, **регулярні платежі з матеріалізацією — крок 26**, іконки категорій на 3 поверхнях + **ручний пікер — крок 31**), Гаманці/Категорії(+підкатегорії+іконки)/Бюджети/Теги/Регулярні платежі/Типи змін, Покупки, Графік змін, Розрахунки/Debt, Налаштування → Вигляд+Акаунт+Безпека(PIN+біометрія)+**Сповіщення (Push-токен + гранулярні типи — кроки 27-28)**+**Профілі (власні + спільні invite/join/leave v1 без ролей — кроки 30, 32)**, реальний вхід через Google. **Firestore cold-sync (одноразовий, при вході або при перемиканні профілю, не continuous)** покриває **11 доменів**, усі profileId-aware через `ProfileDocNames.kt`/`ProfileSyncCoordinator`, і тепер (крок 32) resolvable проти чужого `ownerUid`-дерева для спільного профілю: гаманці, типи змін, категорії, підкатегорії, **categoryIcons (крок 31)**, транзакції, покупки, розрахунки/debt, призначення змін на дні календаря, бюджети, теги, **регулярні платежі (крок 26)**. autoFillSchedule — єдине, що ще не має Room-моделі/UI. Не почато: мова (uk/en), гранулярні editor/viewer ролі для спільних профілів, видалення акаунту.

**Наступний крок**: **перш за все повторити наскрізний invite/join/leave тест кроку 32** (не підтверджений цього разу через зламаний GMS-стан AVD, можливо потрібен `-wipe-data` перезапуск) — не розширювати спільні профілі далі, поки цей базовий цикл не підтверджено реально працюючим. Після цього — на вибір:
- **Гранулярні editor/viewer ролі для спільних профілів** — розширення кроку 32. `firestore.rules` уже підтримує (`memberRole()`/`isEditorMember()`), потрібен лише клієнтський UI: "Учасники"-менеджер для власника (список + перемикач ролі, мірор PWA-шних `listSharedMembers()`/`setMemberRole()`), і опційно клієнтська UX-підказка для viewer (сервер однаково реальний захист).
- **Мова (i18n, uk/en)** — заблокована реальним prerequisite: усі Kotlin/Compose-файли хардкодять українські рядки напряму. Потрібна повна міграція сотень рядків у string resources ПЕРЕД тим, як можна додати перемикач — окремий, великий, механічний рефакторинг-крок, не "фіча".
- Дрібніший, контрольований варіант: підключити `CategoryIconBadge` до `RecurringManagerSheet`'s згорнутого рядка (розкрита, не зроблена дрібниця з кроків 29/31) — кілька рядків коду.

**Темп верифікації (з 2026-08-21, явний фідбек власника акаунта)**: попередні кроки (особливо 26-31) витрачали багато токенів на емулятор-цикли (повторні запуски AVD+Firebase-емулятора, десятки скріншотів, `uiautomator dump` на кожен тап). Власник акаунта попросив **зменшити інтенсивність перевірки** — для наступних кроків: перевіряй `assembleDebug` завжди, але емулятор-цикл лише один раз на крок (не кілька повторних запусків), менше скріншотів (один-два на реально ризиковану дію, не на кожен тап), і уникай раунду "переперевірки після дрібного виправлення", якщо саме виправлення тривіальне й очевидно коректне.

**Як ми працюємо (закріплено, дотримуйся):**
1. **Кожна фіча грунтується на реальному коді PWA** (`index.html`+`js/*.js`+`I18N.uk` у `js/classic-globals.js`) — читай реальні рядки/логіку перед тим як писати Compose-код, не вигадуй поведінку.
2. **"Компілюється" ≠ "працює"**. Кожен крок закривається реальним прогоном на емуляторі, не лише `assembleDebug`. Для кроків, що торкаються Firestore/Auth — реальним записом/читанням через локальний Firebase-емулятор (рецепт нижче), не просто "код виглядає правильно".
3. **Знайдені під час перевірки баги — фіксувати одразу**, показувати чесно (не мовчати, не видавати за "так і мало бути"). Приклади з попередніх сесій: завузьке поле валюти в WalletsManagerSheet, втрата `createdAt` при toggle в ShoppingRepository, `INSERT OR REPLACE` міняв SQLite rowid і перевпорядковував списки (крок 9), race між `seedIfEmpty()` і sync-запитом (крок 15), два різні інстанси `PinViewModel` (крок 17), заморожене авто-заповнення в формі платежу через порядок присвоєння (крок 16).
4. **Не плутай "виглядає як баг" з "я протестував неправильно"** — перш ніж дописувати код, перевір логіку сценарію (приклад: гаманець "не видалявся" — виявилось, що він дійсно використовується transfer-транзакцією в seed-даних, не баг).
5. **Координати тапів на емуляторі бери з `adb shell uiautomator dump` + `grep bounds`, НІКОЛИ з масштабування скріншота на око.** Скріншот повертається в РЕАЛЬНІЙ роздільності пристрою (напр. 1344×2992), а в чаті показується масштабовано (напр. до 898px) — плутанина "координата з відображеного зображення" vs "координата з дампу/реального пристрою" витратила багато циклів у кількох сесіях поспіль. Завжди перевіряй dump перед серією тапів по новому екрану.
6. **Перший рендер після `am start` на swiftshader повільний** (5-15с чорний екран — нормально, не баг) — чекай, не роби висновків з першого скріншота.
7. **"System UI isn't responding" ANR — артефакт цього емулятора (swiftshader-рендеринг, довгий uptime), не баг застосунку.** Тисни "Wait" (координати з dump, не на око — сам ANR-діалог теж підпадає під правило 5). Якщо повторюється кілька разів поспіль і тапи стають ненадійними (спостерігалось: випадковий тап зачепив і видалив тестові транзакції) — `adb emu kill` і перезапуск з `-wipe-data`, дешевше за боротьбу з зависанням.
8. **Гнучкий скоуп, чесно розкритий**: коли токен-економія важлива, звужуй фічу (напр. Shifts без quick-fill) і явно пиши "Свідомо не в цьому кроці" в секції кроку — не вдавай, що зроблено більше, ніж є.
9. **Кожен крок — окрема секція в цьому файлі** з "що зроблено"/"чесно not done"/як перевірено. Пиши її одразу після кроку, не в кінці сесії.
10. **Toolchain-гочі, вже вирішені, не переоткривай**: `android.disallowKotlinSourceSets=false` в `gradle.properties` (KSP+Room під AGP 9's built-in Kotlin); `fallbackToDestructiveMigration(dropAllTables=true)` в `RytmApplication` (пре-лонч — версію БД можна бампати вільно); Firebase BOM 33+ дропнув `-ktx`-артефакти, використовуй `firebase-auth`/`firebase-firestore`/`firebase-messaging` без суфікса.
11. **Room-синк-домен (як гаманці/типи змін)**: пиши `SetOptions.merge()`, торкайся ЛИШЕ своїх ключів документа — ніколи повний `setDoc(merge:false)` (PWA-документи мають чужі поля, яких Android ще не знає). Перед `syncXOnSignIn()` явно виклич `repository.seedIfEmpty()` для свого домену в `MainActivity`'s `LaunchedEffect` — не покладайся, що відповідний ViewModel уже ініціалізувався (він міг просто не встигнути, якщо користувач ще не відвідав ту вкладку — саме так стався баг кроку 15).
12. **ViewModel, що ділить стан між Activity-гейтом (`MainActivity`) і екраном усередині `RytmNavHost`** (напр. `PinViewModel`), мусить явно передавати той самий `viewModelStoreOwner` (Activity) в обох місцях виклику `viewModel(factory=..., viewModelStoreOwner=...)` — інакше це два різні інстанси з незалежним станом (баг кроку 17).
13. Building/testing recipe: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (PowerShell) → `.\gradlew.bat assembleDebug --no-daemon` з `android-app/`; емулятор через PowerShell `Start-Process` (`-avd Pixel_10_Pro_XL -no-snapshot -gpu swiftshader_indirect`), взаємодія і screenshot через Bash `adb`.
14. **Firebase-емулятор рецепт** (для кроків, що торкаються Auth/Firestore) — постійна тестова інфраструктура, не одноразовий хак:
    - `firebase emulators:start --only firestore,auth --project maxtr-c238f` (потрібен `PATH` з `JAVA_HOME/bin`, інакше "Could not spawn java -version"). Порти/host уже в `firebase.json`'s `emulators` секції (0.0.0.0:8080/9099).
    - Збірка з `-PuseFirebaseEmulator=true` (вимикає нормальну збірку/деплой — прапор `false` за замовчуванням).
    - **`adb reverse tcp:8080 tcp:8080` + `tcp:9099 tcp:9099`, НЕ стандартний AVD-аліас `10.0.2.2`** — Windows Firewall у цьому середовищі тихо ковтає вхідні з'єднання від AVD-підмережі до `10.0.2.2`; adb reverse тунелює через уже дозволений ADB-канал.
    - На LoginScreen у такій збірці з'являється `[emulator] Анонімний вхід для тестів` — без реальних credentials, легітимний шлях верифікації sync-коду.
    - Перевірка запису/читання напряму: `curl -H "Authorization: Bearer owner" http://127.0.0.1:8080/v1/projects/maxtr-c238f/databases/(default)/documents/users/{uid}/max_tracker/{doc}` (bypass rules). UID анонімного юзера — з `firebase emulators:export <dir>` → `auth_export/accounts.json`.
    - Після тестів: `adb reverse --remove-all`, `adb emu kill`, зупинити `firebase emulators:start`-процес (`Get-Process | Where ProcessName -match "java|node" | Stop-Process`), прибрати `firebase-debug.log`/`firestore-debug.log`.
15. **Firebase CLI (`firebase` command) уже авторизований і заскоупований на `maxtr-c238f`** у цьому середовищі (`firebase projects:list` працює одразу) — реєстрація застосунків, SHA fingerprints, `google-services.json` — усе через CLI (`firebase apps:create`, `apps:android:sha:create`, `apps:sdkconfig`), без потреби у власника акаунта клікати в Console.
16. **Гілка/коміт питання ВИРІШЕНО (2026-08-20), не переоткривай**: уся Android-робота тепер реально в `main` (кроки 1-22 змерджені одним великим PR [#383](https://github.com/sudak666/Maxtr/pull/383) на новій гілці `claude/native-android-rytm` — стара `claude/native-applications-638ap0` лишилась покинутою, `ua.zminka.app`/`./android/`, і CLAUDE.md вже оновлено посилатись на поточну роботу). **З кроку 23 і далі встановлено постійний робочий процес, дотримуйся його для кожного нового кроку**: (1) `git checkout -b claude/android-<коротка-назва>-step<N>` від актуального `main`; (2) реалізувати + реально перевірити на емуляторі (як завжди); (3) оновити секцію кроку в цьому файлі + "Почни звідси" на початку; (4) `git commit` + `git push -u origin <гілка>`; (5) `gh pr create`; (6) дочекатись зелених CI (`gh pr checks <N>` — 3 джоби, `android-app/` їх не зачіпає, мають пройти автоматично); (7) `gh pr merge <N> --squash --delete-branch=false`; (8) `git checkout main && git pull && git branch -D <гілка>`; (9) окремий коміт-запис у `CHANGELOG.md` прямо в `main` (без PR, docs-only, за прецедентом кроків 383-386). **Один крок = один PR** — не змішуй кілька кроків в одному PR, навіть дрібні (крок 25 — суто візуальний — теж отримав власний PR).
17. **"Cold sync уже відбувся цього ж тестового сеансу" — постійна методологічна пастка, вдарила в кроках 22-24/30/31**: щойно доданий/змінений локальний запис у sync-домені (наприклад, новий тег, категорійна іконка, налаштування бюджету) при наступному force-stop+relaunch **тихо відкочується назад**, якщо remote-документ для цього поля вже існував (навіть порожнім) з попереднього сign-in цього самого сеансу — cold-sync бере "remote wins" і перезаписує локальне порожнім/старим значенням. **Це не баг коду** — очікувана поведінка одноразового (не continuous) sync. Правильна методологія перевірки persistence: видали конкретне поле через Firestore REST `PATCH` (`updateMask.fieldPaths=<поле>` + порожнє `{"fields":{}}` тіло) ПЕРЕД тим, як встановлювати тестове значення і перезапускати — тоді push реально спрацює. Якщо перша спроба перевірки показала "відкат" — не списуй одразу на баг, спершу перевір, чи це саме ця пастка.
18. **Координати з `uiautomator dump` під час ВІДКРИТОГО екрана, не з попереднього скріншота-оцінки** — навіть маючи правило 5 (dump замість скріншот-піксель-оцінки), крок 31 показав додатковий нюанс: dump зроблений ДО відкриття шторки/сітки не дає реальних bounds елементів усередині неї. Завжди роби dump ПІСЛЯ того, як цільовий екран/шторка вже на місці, безпосередньо перед тапом по ньому.
19. **Темп верифікації, зменшений з 2026-08-21 (явний фідбек власника акаунта)** — див. "Почни звідси" вище для деталей. Коротко: один емулятор-цикл на крок, менше скріншотів, не переперевіряй тривіальні виправлення.

---

Джерело правди для бекенду: **той самий Firebase проєкт** (`maxtr-c238f`) — Auth, Firestore, FCM, Cloud Functions (`monobankProxy`, `notificationSweep`). Android-клієнт читає/пише ті самі колекції, жодних змін на бекенді не потрібно (крім, можливо, рерайту `Content-Type`/CORS для Functions — не актуально, Android б'є в Functions напряму, не через браузер).

---

## 1. Екрани та компоненти (чекліст)

### 1.1 Bottom navigation (4 таби) — `NavigationBar` (Material 3)
- [ ] **Фінанси** (за замовчуванням активна)
- [ ] **Графік змін**
- [ ] **Розрахунки** (борги/взаєморозрахунки)
- [ ] **Список покупок**
- [ ] **Налаштування** — окремий екран, доступний через шестерню (не в нижній навігації, як і в PWA)

### 1.2 Finance screen
- [ ] Hero balance card (баланси по гаманцях)
- [ ] Quick-actions row: Операція (FAB), Інструменти, Бюджети, Цілі
- [ ] Історія транзакцій: пошук, фільтр-чіпи (тип/період/категорія), список з swipe-to-delete
- [ ] FAB → форма нової/редагування транзакції (bottom sheet)
- [ ] Секції-віджети (порядок керується Widgets manager): Цілі, Порада дня, Топ криптовалюти, Бюджети місяця
- [ ] **Tools bottom sheet**: Аналітика (донат-чарт + список категорій, фільтр періоду), FX-курси, конвертер валют, 6-місячний лінійний графік

### 1.3 Shifts screen
- [ ] Hero metric: зароблено цього місяця + прогрес-бар до цілі
- [ ] Chip stats: години, кількість змін, вихідні
- [ ] 6-місячний бар-чарт доходу
- [ ] Collapsible "Швидке заповнення": тип зміни + патерн (кожен день/через день/2-2/3-3), автозаповнення з конфігом (тип/патерн/дата-якір)
- [ ] Календар: місяць/рік селектор, легенда, grid днів (тап на день → модалка призначення зміни/змін)

### 1.4 Debt (Розрахунки) screen
- [ ] Chips вибору активного боргу/розрахунку (підтримка кількох одночасно)
- [ ] Hero metric: залишок
- [ ] Прогрес-бар % сплачено
- [ ] Chip stats: стартова сума, сплачено, к-сть платежів, дедлайн
- [ ] Payoff-forecast burndown chart (при ≥2 платежах + встановленій стартовій сумі)
- [ ] Collapsible "Дані розрахунку": назва, нотатка, валюта, стартова сума, дедлайн, видалення
- [ ] Collapsible історія платежів (swipe-to-delete)
- [ ] FAB → форма нового платежу

### 1.5 Shopping screen
- [ ] Chip stats: залишилось, куплено
- [ ] Форма додавання (назва + кількість)
- [ ] Список з чекбоксом done + swipe-to-delete

### 1.6 Settings screen
- [ ] Пошук + групові фільтр-чіпи (все/фінанси/безпека/додаток)
- [ ] Профіль (аватар + нікнейм), Профілі (мульти-профіль + спільні профілі + учасники/ролі)
- [ ] Premium (інформаційний, наразі все безкоштовно)
- [ ] Фінанси: Гаманці, Monobank, Курси, Віджети, Категорії (+підкатегорії, іконки, кольори), Теги, Бюджети, Цілі, Регулярні операції, Авто-правила, CSV експорт/імпорт
- [ ] Безпека: PIN, Push (тумблер + час нагадування + тумблери бюджет/регулярні/борг)
- [ ] Вигляд: тема, мова, кеш офлайн
- [ ] Акаунт: вихід, скидання даних, видалення акаунту
- [ ] Про додаток: посилання, умови, приватність

### 1.7 Модалки/менеджери (усі — окремі Compose bottom sheets/dialogs)
biometric-onboard, pin-settings, shift, shift-types, wallets, monobank, tx-form, debt-form, categories (+category-icon, cat-action), color-pick (спільний), rates, widgets, tools, premium, tags, profiles, shared-members, profile-avatar-pick, rules, budgets, goals, recurring.

---

## 2. Структура даних (Room + DataStore)

### 2.1 Room entities (mirror Firestore-синхронізованих полів `AppState`)
| Entity | Поля (з PWA typedef) | Примітка |
|---|---|---|
| `TransactionEntity` | id, type, amount, date, walletSourceId(`ws`), walletTargetId(`wt`?), category(`cat`?), subcategory(`sub`?), comment, tagIds, createdAt, monobankId? | окрема таблиця = окрема Firestore-підколекція, 1:1 |
| `WalletEntity` | id, name, color, icon, currency? | |
| `ShiftTypeEntity` | id, name, short, code?, color, amount, hours, isOff | |
| `ShiftEntity` | dateKey (PK), shiftTypeIds: List<String> | у Firestore — map, у Room — окрема таблиця з FK-списком (join table `ShiftDayCrossRef`) |
| `DebtEntity` + `DebtEntryEntity` | id/name/note/currency/startAmount/dueDate + payments (amount/date) | 1-to-many |
| `GoalEntity` | id, walletId, targetAmount, targetDate | |
| `AutoRuleEntity` | id, type, keyword, category | |
| `TagEntity` | id, name, color | |
| `ShoppingItemEntity` | id, name, qty, done, createdAt | |
| `BudgetEntity` | category (PK), amount | зараз `Record<category,number>` |
| `CategoryEntity` | name, type(income/expense), icon, colorRef, orderIndex | зараз паралельні масиви — нормалізується в таблицю (покращення, див. §3) |
| `SubcategoryEntity` | category (FK), name | |
| `RecurringEntity` | (форма транзакції + частота/наступна дата) | |
| `ProfileMetaEntity` | id, name, avatar?, createdAt?, kind?, ownerUid? | |
| `CurrencyRateEntity` | code (PK), rate, source, updatedAt | |

### 2.2 DataStore (Preferences) — не-синхронізовані/локальні-глобальні налаштування
`theme`, `language`, `hideAmounts`, `activeProfileId` (per-device), `pinHash`, `biometricEnabled`, `notifSettings` (enabled/time/toggles/timeZone), `widgetOrder`, `widgets` (toggle map), `ratesSource`, `analyticsPeriod`, `financeChartSeries`, `mxCryptoTopCache`, monobank integration blob (token — **шифрований**, DataStore + EncryptedFile/Keystore, ніколи plain).

### 2.3 Синхронізація з Firestore
- Той самий шлях `users/{uid}/max_tracker/{shifts,finance,debt,backup_v2,profiles_meta}` + `transactions` підколекція + `shared_members`/`profile_invites`/`push_tokens`.
- Room — offline-first локальний кеш; `Repository` шар: Room → UI (Flow), фонова синхронізація Room↔Firestore через WorkManager (заміна `scheduleSave()` debounce з PWA) + snapshot listeners для реального часу.
- Optimistic concurrency (`lastKnownUpdatedAt`) переноситься 1:1.

---

## 3. Пропозиції покращень для Android-версії (з обґрунтуванням)

| # | Область PWA | Проблема на Android | Нативне рішення |
|---|---|---|---|
| 1 | Swipe-to-delete (CSS `width`-shrink) | Хак під браузерні обмеження (transform кліпить контент) | Compose `SwipeToDismissBox` (Material 3) — плавніше, менше кастомного touch-коду |
| 2 | Pull-to-refresh (кастомний JS drag + damping) | Ручна фізика, не відповідає системним очікуванням | `PullToRefreshBox` — системна поведінка "з коробки" |
| 3 | PIN + WebAuthn | WebAuthn на Android — обхідний шлях, не системний UI | `BiometricPrompt` напряму (Face/Fingerprint) + PIN fallback через `EncryptedSharedPreferences`/Keystore |
| 4 | Receipt OCR (Tesseract.js WASM, ~8MB) | Важкий bundle, повільний non-SIMD WASM на слабких пристроях | **ML Kit Text Recognition** (on-device, без бандлу моделі, апаратно прискорено) — та сама keyword-priority + "найбільше десяткове число" логіка парсингу переноситься 1:1 у Kotlin |
| 5 | Custom `<select>`/date input (`enhanceSelect`) | Обхід нестильованого нативного `<select>` браузера | Material 3 `ExposedDropdownMenuBox`/`DatePicker` — вже стильовані нативно, кастомний шар не потрібен |
| 6 | Color picker (`color-pick-modal`) | Кастомна модалка через обмеження `<input type=color>` | Compose swatch-grid dialog — той самий підхід, без причини для обходу (Android і так не має цього обмеження, але патерн лишаємо для консистентності） |
| 7 | On-device AI category suggestion (Chrome Prompt API) | Немає прямого системного еквіваленту | Fallback на **keyword `AutoRule`** (вже є, платформо-незалежний) як базова поведінка; опційно пізніше — Gemini Nano/AICore на підтримуваних пристроях. Не блокер для MVP |
| 8 | `categories`/`subcategories`/`categoryIcons` — паралельні `Record<>` у одному document | Три окремі мапи, синхронізовані вручну по ключу | Нормалізована таблиця `CategoryEntity` (name, type, icon, order) + `SubcategoryEntity` (FK) — усуває клас багів "забув оновити один з трьох record" |
| 9 | Модалки як `.modal-overlay` overlay поверх сторінки | Всі 20+ менеджерів — однакові full-screen overlay без diff в UX | Розрізнити за вагою: короткі форми (тег, колір, іконка) → `ModalBottomSheet`; довгі списки-менеджери (гаманці, категорії, регулярні) → повноцінний `NavHost` screen з системною back-навігацією (Android-користувачі очікують back button, а не "click outside to close") |
| 10 | Список транзакцій — ручний targeted DOM diff (`data-id` matching) | Обхід React/Preact-подібного diffing без фреймворку | Compose `LazyColumn` + stable `key = tx.id` — той самий targeted-update ефект "з коробки", без ручного DOM diff коду |
| 11 | Календар — ручний grid render (645 рядків JS) | Кастомна геометрія | Compose `LazyVerticalGrid(columns=7)` — значно менше коду, той самий візуал |
| 12 | i18n (`I18N` flat object + `data-i18n`) | Ручний `tr()`-виклик у кожному місці | Стандартні Android `strings.xml` (values/values-uk) + `stringResource()` — той самий набір ключів переноситься 1:1, але з компайл-тайм перевіркою відсутніх перекладів |
| 13 | Хвиля toast/haptic (`showToast` + `navigator.vibrate`) | Кастомний UI toast | `SnackbarHost` (Material 3) + `HapticFeedback` API — системний вигляд, консистентний з рештою ОС |
| 14 | Push-іконки (`icon`/`badge` PNG generation) | Ручна генерація растрових іконок під Android-маскування | Векторні `notification` icons (`drawable` XML, tinted) — стандартний Android-підхід, без генерації альфа-силуетів вручну |
| 15 | Monobank client-side pacing (61s між запитами, ручний rate-limit у JS) | Дублювання серверної логіки на клієнті | Тримати pacing **лише на сервері** (Cloud Function вже містить ту саму логіку) — Android просто чекає на відповідь/показує progress, не дублює `SYNC_REQUEST_GAP_MS` константу локально |
| 16 | "Hide amounts" — фіксований CSS selector list (`body.amounts-hidden`) | Легко забути додати новий елемент у список | Единий Compose `Modifier.blurSensitive(enabled)` застосований явно на кожному money-composable — той самий "явний opt-in", менше шансів забути (лінтер/code review легше ловить відсутній модифікатор, ніж відсутній CSS-клас) |

**Що НЕ змінюється** (навмисно, щоб зберегти паритет UX і не зламати впізнаваність бренду для користувачів обох платформ): кольорова палітра, іконографія (переносимо як vector drawables 1:1), структура вкладок/меню, бізнес-логіка розрахунків (курси, борги, авто-правила, повторювані платежі), Firestore data model.

---

## 4. Тема / Material 3 mapping (з `:root` CSS index.html)

### `Color.kt`
- Primary (purple): `#8b5cf6` / dark-variant `#a78bfa` / deep `#6d28d9` (light theme: `#7c3aed`)
- Success/green: `#10b981`/`#34d399` (light: `#059669`)
- Error/red: `#ef4444`/`#f87171` (light: `#dc2626`)
- Info/blue: `#3b82f6`/`#60a5fa` (light: `#2563eb`)
- Warning/orange: `#f59e0b`/`#fbbf24` (light: `#c2760a`)
- Accent: pink `#ec4899`, cyan `#06b6d4`
- Dark surfaces: bg `#1c1c1f`, bg1 `#242327`, bg2 `#2c2b30`, bg3 `#38373d`, border `#403f45`/`#525158`, text `#e9e8ea`/`#fff`, muted `#96959c`/`#98979e`
- Light surfaces: bg `#f4f3f1`, bg1 `#fff`, bg2 `#ececea`, bg3 `#e2e0dd`, border `#e4e4e9`/`#d1d1d6`, text `#1c1c1e`/`#0b0b0d`, muted `#6b6b70`/`#626269`
- → Material3 `ColorScheme`: `primary`=purple, `surface`=bg1, `surfaceContainer`=bg2/bg3 tiers, `onSurfaceVariant`=muted — обидві теми (`lightColorScheme`/`darkColorScheme`), системна `dynamicColor` **вимкнена** (бренд-палітра фіксована, як і в PWA).

### `Type.kt`
Розмірний скейл: 10.5/11.5/13/14/16/22/30/36sp → `labelSmall`…`displaySmall`. Ваги: body 500, strong 700, display 800, mega 900 (`FontWeight.Medium/Bold/ExtraBold/Black`).

### `Shapes.kt`
Card radius 22dp, row radius 16dp → `RoundedCornerShape` для `shapes.large`/`shapes.medium`.

### Elevation / glow
PWA використовує кастомні tinted-purple тіні (`--shadow-fab`, `--shadow-lock-logo`, донат-глов), які стандартна Material3 elevation не відтворює — потрібен кастомний `Modifier.shadow(color=purple.copy(alpha=…))` на FAB/hero-картках/логотипі, не default `tonalElevation`.

### Іконки
Весь monochrome SVG-набір (`ICON_PATHS`, `window.Icon()`) переноситься як vector drawables (`ImageVector`/XML) 1:1 — жодних emoji, як і в PWA.

---

## 5. i18n
`I18N.uk`/`I18N.en` (~300+ ключів кожна) → `res/values/strings.xml` + `res/values-uk/strings.xml`, ті самі ключі (легко звірити діфом при міграції).

---

## Toolchain (Крок 1, зафіксовано)

Реальні версії, підібрані під локально встановлений Android Studio (JBR = JDK 25) і перевірені живою збіркою (`gradlew assembleDebug` → `app-debug.apk`, успішно):

| Компонент | Версія | Примітка |
|---|---|---|
| Gradle | 9.5.0 | мінімум для AGP 9.3.0; підтримує JDK 25 (host JBR) |
| AGP | 9.3.0 | найновіша стабільна (лип. 2026); має **built-in Kotlin** — плагін `org.jetbrains.kotlin.android` більше не застосовується окремо (обов'язково прибирається в AGP 10) |
| Kotlin (built-in) | 2.2.10 | версія, яку AGP 9.3 підтягує сам; `org.jetbrains.kotlin.plugin.compose` лишається окремим плагіном лише для Compose-компілятора |
| Compose BOM | 2026.08.00 | вимагає compileSdk 37 |
| compileSdk/targetSdk | 37 | мінімум за Compose BOM |
| minSdk | 26 | без змін (BiometricPrompt та решта нативних API з §3 доступні) |
| navigation-compose | 2.9.8 | стабільна лінія; Navigation3 (1.1.6, стабільна з лют. 2026) розглядався, але свідомо не взятий для скелета — надто нова архітектурна зміна для першого кроку, повернемось за потреби |

`gradlew`/`gradlew.bat`/`gradle-wrapper.jar` згенеровані і закомічені в `android-app/` — збірка відтворювана без Android Studio (`JAVA_HOME` на будь-який JDK 17+, `./gradlew assembleDebug`). `local.properties` (шлях до SDK) навмисно в `.gitignore` — машинно-специфічний.

## Крок 2 — Finance screen (зроблено, перевірено на емуляторі)

Специфікація: [FINANCE_SCREEN_SPEC.md](android-app/FINANCE_SCREEN_SPEC.md) — грунтована на реальному коді PWA (`index.html`, `js/analytics-csv.js`), не на здогадках: точна логіка hero-балансу (мультивалютний `≈`-режим, trend-чіп, mini-stats), 4 quick actions, пошук+2 незалежні ряди фільтр-чіпів, рядок транзакції 1:1 з `txItemInnerHtml()`, **два різні** порожні стани (немає даних / нічого не знайдено — не один спільний), колапс списку на 5 записів.

Реалізовано в Compose (`ua.rytm.app.ui.screens.finance`): `FinanceViewModel` (MVVM, поки на sample-даних — жодного Room/Firestore), `FinanceScreen` + підкомпоненти, `SwipeToDismissBox` для видалення. Живий скріншот з емулятора Pixel 10 Pro XL надіслано вище — не заявлено "готово" без реальної перевірки.

## Крок 3 — форма нової/редагування операції (зроблено, перевірено на емуляторі)

Точні поля/лейбли/валідація — 1:1 порт `js/finance.js` (`setFinanceType`, `readTransactionForm`, `updateTransferHint`) і `js/tx-validation.js`, задокументовано в [FINANCE_SCREEN_SPEC.md §9](android-app/FINANCE_SCREEN_SPEC.md). Реалізовано: сегмент типу (дохід/витрата/переказ), гаманець з лейблом що змінюється по типу, гаманець-ціль + hint конвертації для переказу, сума + quick-amount чіпи, категорія/підкатегорія (show/hide за наявністю підкатегорій), дата + "Сьогодні", коментар з лічильником, валідація з тими самими UK-повідомленнями, toast після сабміту.

**Перевірено живим наскрізним сценарієм на емуляторі** (не лише компіляцією): відкрив форму через FAB → задав суму quick-чіпом "50" → підкатегорія коректно з'явилась для категорії "Продукти" → сабміт → snackbar "Запис додано" → новий запис одразу у списку, лічильник 7→8, баланс/mini-stats/wallet-чіп перерахувались (22 483,5→22 433,5 грн). Скріншоти надіслано вище.

**Чесно not done**: сканування чека (ML Kit — окремий крок), теги (немає ще `Tag`-сутності з кольором), бюджет-попередження в toast (немає `Budget`-сутності), Інструменти/Бюджети/Цілі quick actions — досі no-op.

## Крок 4 — Room/Repository для Finance (зроблено, перевірено на емуляторі)

`WalletEntity`/`TransactionEntity` + DAO (`Flow<List<T>>` для реактивних запитів) + `RytmDatabase` + `FinanceRepository` (`android-app/app/src/main/java/ua/rytm/app/data/`). `FinanceViewModel` тепер читає/пише через репозиторій, не тримає дані в пам'яті — `SampleFinanceData` лишився лише як **одноразовий seed** при першому запуску (порожня БД), чесно позначено в коментарях, не видається за синк.

**Реальний технічний виклик, вирішений емпірично, не здогадкою**: Room 2.8.4 + KSP не запрацювали одразу під AGP 9.3's built-in Kotlin (без окремого `org.jetbrains.kotlin.android` плагіна) — реальна помилка `Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin`. Рішення — офіційний escape-hatch із самого тексту помилки: `android.disallowKotlinSourceSets=false` у `gradle.properties` (задокументовано коментарем на місці). Підтверджено окремим `:app:kspDebugKotlin` запуском перед тим, як писати весь shema-код.

**Перевірено живим сценарієм, що доводить справжню персистентність** (не просто "не впало під час сесії"): додав транзакцію → `adb shell am force-stop ua.rytm.app` (справжнє вбивство процесу, не згортання) → `am start` заново → запис і перерахований баланс лишились на місці. Скріншот надіслано вище.

**Чесно not done**: категорії/підкатегорії лишаються статичними константами (`SampleFinanceData`), не Room-таблицею — окрема майбутня `CategoryEntity`/`BudgetEntity` (§2.1 таблиця), не блокер для решти кроків. Гаманці зараз без CRUD-екрана (створюються лише через seed).

## Крок 5 — менеджер гаманців, реальний CRUD (зроблено, перевірено на емуляторі)

`WalletsManagerViewModel`/`WalletsManagerSheet` (`android-app/.../ui/screens/finance/`), 1:1 з `js/settings-managers.js`'s `wallets-modal` — точні правила в [FINANCE_SCREEN_SPEC.md §10](android-app/FINANCE_SCREEN_SPEC.md). Підключено через перший реальний рядок у `SettingsScreen` ("Гаманці") — решта Settings навмисно лишається пустою, не підробним списком заглушок.

**Реальний UI-баг, знайдений і виправлений під час живого тесту**: поле валюти без `singleLine=true` й завузьким `width=96.dp` рвало "UAH"/"USD" на 2 рядки. Побачив на скріншоті → виправив (`110.dp` + `singleLine=true`) → перезібрав → підтвердив скріншотом.

**Наскрізна перевірка обох guard'ів на видалення, з чесним самовиправленням помилки тестування**: спроба видалити "Готівка" (використовується в транзакціях) → правильно заблоковано toast'ом. Спроба видалити "Заощадження" — спочатку здалось, що нічого не відбувається (той самий текст помилки на екрані), але це виявилось **не багом коду, а моєю власною помилкою в тестовому сценарії**: "Заощадження" й справді використовується — як ціль переказу в seed-транзакції t4. Перевірив теорію на свіжододаному, справді порожньому гаманці — діалог підтвердження з'явився коректно. Підтвердив видалення (з другої спроби — перша не влучила в кнопку через неправильний розрахунок координат) → гаманець реально зник зі списку.

## Крок 6 — екран "Покупки" (зроблено, перевірено на емуляторі)

Другий повний вертикальний зріз (design spec → Room → ViewModel → Compose → перевірка), тепер на іншому патерні (чекліст, не список транзакцій) — підтверджує, що підхід з Кроків 2-5 узагальнюється. Специфікація: [SHOPPING_SCREEN_SPEC.md](android-app/SHOPPING_SCREEN_SPEC.md), 1:1 з `js/shopping.js`. `ShoppingItemEntity`/`ShoppingDao` додані до `RytmDatabase` (version 1→2, `fallbackToDestructiveMigration` — пре-лонч конвенція з CLAUDE.md, без реальних даних для захисту).

**Чесний момент під час перевірки**: перший скріншот показав порожній список замість seed-даних — не списав на "працює" і не проігнорував. Перевірив logcat (без винятків), звірив, що Finance-дані теж скинулись (очікувано — версія БД зросла, `fallbackToDestructiveMigration` спрацював на весь файл, не тільки нову таблицю), і після довшого очікування дані з'явились коректно — це виявився таймінг рендеру на повільному software-GPU емулятора, не баг коду.

Наскрізно перевірено: seed (3 товари, правильне сортування invalid→done), додавання нового товару (лічильник +1, поле очищується), toggle "куплено" з живим пересортуванням і перерахунком лічильників, "Очистити куплені" — умовна видимість кнопки + `AlertDialog` підтвердження + реальне видалення лише позначених.

## Крок 7 — категорії на Room (зроблено, перевірено на емуляторі)

`CategoryEntity`/`CategoryDao` (БД version 2→3), `CategoriesManagerViewModel`/`CategoriesManagerSheet` — той самий CRUD-патерн, що й гаманці (§10), тому без нової дизайн-специфікації. Другий реальний рядок у Settings. `FinanceViewModel`/`TransactionFormSheet` тепер читають категорії з Room (`categoriesByType`), не зі статичного `SampleFinanceData` — форма нової операції більше не sample-only в цій частині. Перевірено на емуляторі: реальний seed (7 витрат/3 доходи), перемикання таба типу, діалог видалення.

## Наступні кроки

## Крок 8 — екран "Графік змін" (зроблено, scoped, перевірено на емуляторі)

[SHIFTS_SCREEN_SPEC.md](android-app/SHIFTS_SCREEN_SPEC.md) — свідомо звужений скоуп (token economy): календарна сітка (Пн-перший тиждень, `LazyVerticalGrid(7)`), tap-on-day bottom sheet з мультивибором типів змін, hero metric (зароблено + прогрес до `SALARY_GOAL`), chip stats (год/змін/вихідних), легенда. Room: `ShiftTypeEntity`+`ShiftDayEntity` (нормалізована таблиця день×тип, а не CSV-колонка), seed = `LEGACY_SHIFT_TYPES` (6 вбудованих типів, 1:1 з `js/core.js`). Свідомо відкладено: швидке заповнення, автозаповнення, менеджер типів змін (CRUD), графік доходу за 6 міс — той самий "чесно not done", не приховано.

Перевірено на емуляторі: seed відобразився (легенда/порожній календар), tap на сьогодні → модалка з датою і 6 чекбоксами, вибір "Денна зміна" → Готово → токен "Д" з'явився в клітинці, hero metric/chip stats перерахувались наскрізь (0→1000 грн, 0→12 год, 0→1 зміна, прогрес-бар 0%→5%).

## Крок 9 — менеджер "Типи змін" (зроблено, перевірено на емуляторі)

`ShiftTypesManagerSheet`/`ShiftTypesManagerViewModel` (`android-app/app/src/main/java/ua/rytm/app/ui/screens/shifts/`) — той самий CRUD-патерн, що й Гаманці/Категорії, підключений через Settings. 1:1 з `js/settings-managers.js`'s `openShiftTypesManager()`/`renderShiftTypesList()`/`updateShiftType()`/`addShiftType()`/`deleteShiftType()`: collapsed-summary-row з pencil-toggle-to-expand (назва/оплата/години/вихідний), inline live-save з 400ms debounce (той самий патерн, що й `WalletRow`), видалення знімає id з усіх днів календаря (`ShiftDayDao.deleteByShiftTypeId`), назва обрізає `short` до 4 символів як у PWA. Колір — фіксований при створенні (PALETTE-ротація), без інтерактивного пікера — той самий свідомий скоуп, що й у `WalletsManagerSheet`.

**Реальний баг знайдено й виправлено під час перевірки на емуляторі**: редагування назви типу зміни переміщало рядок в кінець списку. Причина — `ShiftTypeDao.insert()`/`WalletDao.insert()` використовували `@Insert(onConflict=REPLACE)` і для нових записів, і для апдейтів; SQLite виконує `REPLACE` як delete+insert, що змінює rowid рядка і перевпорядковує `observeAll()` (без `ORDER BY`). Той самий баг існував і в `WalletDao`/`FinanceRepository.upsertWallet()` (не тільки в новому коді цього кроку) — виправлено в обох місцях: додано реальний `@Update` DAO-метод, `ShiftsRepository`/`FinanceRepository` розділили `add*()` (insert, тільки нові записи) і `update*()` (update, зберігає rowid/порядок). Перевірено повторно на емуляторі — після фіксу редагування більше не рухає рядок.

Перевірено на емуляторі: список із 6 seed-типів відобразився, tap-редагувати розгортає форму (назва/оплата/години/чекбокс), inline-редагування назви зберігається і виживає закриття/повторне відкриття sheet (підтверджує реальний Room-запис + Flow-читання), після фіксу порядок рядків стабільний після редагування.

Чесно not done: колір-пікер для типу зміни (той самий disclosed-скоуп, що й гаманці), гард на видалення (PWA питає підтвердження, тут теж є — але без перевірки "останній тип" типу walletInUse, бо в PWA такого гарду для типів змін і немає).

## Крок 10 — екран "Розрахунки" (Debt) (зроблено, scoped, перевірено на емуляторі)

`DebtScreen`/`DebtViewModel`/`DebtRepository` (`android-app/app/src/main/java/ua/rytm/app/ui/screens/debt/` + `DebtScreen.kt` в `ui/screens/`), замінили плейсхолдер-заглушку. 1:1 з `js/debt.js`: чипи вибору розрахунку (+ "Новий розрахунок"), hero-картка залишку, прогрес-бар "% сплачено" (прихований при `startAmount<=0`, як у PWA), chip stats (стартова сума/сплачено/платежів), чип терміну сплати (колір змінюється, коли лишилось ≤3 дні), collapsible "Дані розрахунку" (live-save полів, видалення розрахунку), collapsible "Історія платежів" зі swipe-to-delete рядками (Compose `SwipeToDismissBox`, той самий патерн, що й `TransactionRow`, замість ручного pointer-drag PWA-версії — навмисне нативне покращення з §3 пропозицій), FAB "Платіж" → bottom sheet з авто-обчисленням залишку з суми платежу (мірорить `autoFillDebtBalance()`). Room: `DebtEntity`+`DebtEntryEntity` (не `@Update`, а окремі `add*()`/`update*()` шляхи з самого початку — та сама причина, що й фікс у кроці 9). Seed = один розрахунок "Кредит" (0/0), мінімальний — на відміну від `LEGACY_SHIFT_TYPES`, у PWA немає дефолтних розрахунків для копіювання 1:1.

**Свідомо не в цьому кроці (chesno not done)**: SVG/Preact payoff-forecast burndown-графік (`renderDebtForecast()`/`DebtBurndownChart` у PWA) — найскладніша частина екрану, відкладена за тим самим принципом token economy, що й Shifts' quick-fill у кроці 8. Немає плану "колись портуємо" — якщо буде потрібен, це окремий крок.

Перевірено на емуляторі повним циклом: seed-розрахунок відобразився, редагування стартової суми (5000) миттєво оновило hero-баланс/прогрес-бар/chip stats (live-write через Room Flow, без релоаду), FAB → форма → сума 1000 автоматично порахувала залишок 4999, "Додати" записав платіж і оновив усі похідні значення (баланс 4999, "Сплачено" 1, "Платежів" 1), swipe-to-delete на рядку платежу відкрив дельта-кнопку і викликав диалог підтвердження, підтвердження видалення повернуло всі значення до 0/5000.

## Крок 11 — Налаштування → Вигляд (тема) (зроблено, scoped, перевірено на емуляторі)

`SettingsStore` (`android-app/app/src/main/java/ua/rytm/app/data/local/SettingsStore.kt`) — DataStore Preferences, перший case для не-Room, пристрій-глобальних (не per-акаунт) налаштувань, той самий клас проблеми, що `mxTheme`/`mxLang` у PWA (`localStorage`, поза `lsKey()`-обгорткою). Один булевий ключ `dark_theme`, дефолт `true` — 1:1 з `js/theme-preinit.js`/`applyTheme()`'s фолбеком на `dark`, коли нічого не збережено. `MainActivity` збирає `Flow` і передає в `RytmTheme(darkTheme=...)` (параметр уже існував, просто раніше завжди отримував `isSystemInDarkTheme()`). `SettingsScreen` отримав перший `SettingsSectionLabel` (групування, як PWA-групи фінанси/безпека/додаток) і `SingleChoiceSegmentedButtonRow` з двома опціями (Світла/Темна) — **без "Системна" опції**, оскільки її немає й у PWA (лише ручний dark/light-перемикач).

**Чому саме це, а не Push/PIN/акаунт**: перевірка (`grep -rln firebase`) підтвердила, що в `android-app` **взагалі немає Firebase-інтеграції** — ні Auth, ні Firestore, ні FCM, застосунок повністю офлайн-локальний (Room). Push/PIN-з-хмарним-бекапом/акаунт/профілі всі транзитивно залежать від Firebase Auth, якого просто немає — це не "ще один рядок Settings", а окремий, більший передумовний крок. Мова (uk/en) теж заблокована на реальному передумовнику: кожен екран хардкодить українські рядки прямо в Compose-коді (не через `stringResource`), `strings.xml` покриває лише bottom-nav — реальний перемикач мов вимагає міграції сотень рядків у `values`/`values-en`, це окремий багатосесійний крок, не куточок цього. Обидва чесно розкриті, не приховані.

Перевірено на емуляторі: тема за замовчуванням — темна (раніше застосунок завжди рендерився світлою темою Material3-дефолтом; тепер дефолт відповідає PWA), перемикання на "Світла" миттєво перефарбувало весь застосунок (не тільки Settings-екран), force-stop + повторний запуск підтвердив, що вибір теми пережив холодний перезапуск (реальний DataStore-запис, не просто in-memory стан).

## Крок 12 — Firebase SDK підключено (зроблено, ТІЛЬКИ wiring, перевірено на емуляторі)

**Реєстрація**, зроблена через `firebase` CLI (авторизовано на `maxtr-c238f`, не через ручний клік у Console):
- `firebase apps:create ANDROID "Rytm" --package-name ua.rytm.app` → новий Firebase Android app (App ID `1:311094677098:android:d53316e0a18e57f9340413`), **окремий від існуючого "DoRytm"** (`com.habittracker.app`, чужий застосунок — не займали і не змінювали, лише прочитали список).
- `firebase apps:android:sha:create` — додано SHA-1 стандартного debug-keystore (`06ae83f466d6298a2a768e783ee937f29f2fd634`, той самий, що вже був у DoRytm — стандартний спільний debug-keystore цього середовища), потрібен для Google Sign-In навіть у debug-збірці.
- `firebase apps:sdkconfig ANDROID ... --out android-app/app/google-services.json` — реальний конфіг з OAuth `client_type:1` для `ua.rytm.app` (без SHA не було б цього клієнта, тільки web-фолбек). **Не секрет, комітиться в git як завжди** (Firebase API-ключі захищені Security Rules/App Check, не секретністю).

**Gradle wiring**: `com.google.gms.google-services` плагін (root+app `build.gradle.kts`), Firebase BOM `34.5.0` + `firebase-auth`/`firebase-firestore`/`firebase-messaging` (**без `-ktx` суфіксу** — новіші BOM злили KTX-розширення в основні артефакти, `-ktx`-варіанти більше не резолвляться й ламають збірку з незрозумілою помилкою "Could not find ...-ktx:" — перша спроба впала на цьому, виправлено), плюс `androidx.credentials`+`credentials-play-services-auth`+`googleid` (сучасний Credential Manager API для Google Sign-In, не застарілий `GoogleSignInClient`).

**Свідомо ТІЛЬКИ це в цьому кроці**: жодного коду, який реально викликає Firebase (немає `FirebaseAuth.getInstance()`, немає екрану входу, немає жодного Firestore-запиту) — застосунок і далі повністю офлайн/локальний, як і до цього кроку. Це навмисно маленький, безпечний, повністю верифікований перший крок великого Firebase-блоку (Auth-екран + Firestore sync-шар — це вже кроки 13+, кожен окремо).

Перевірено: `./gradlew assembleDebug` — успішно (після виправлення `-ktx`-помилки), `processDebugGoogleServices` task пройшов без помилок (підтверджує, що `google-services.json`'s `package_name` збігається з `applicationId`). Встановлено й запущено на емуляторі — застосунок стартує нормально, Finance-екран рендериться з реальними даними, `pidof ua.rytm.app` підтверджує живий процес, `logcat` без FATAL/крашів. (Під час перевірки емулятор показав "System UI isn't responding" — ANR окремого системного процесу емулятора, не нашого застосунку; ймовірно перевантаження swiftshader після багатьох сесій у цій розмові — не пов'язано з Firebase-змінами.)

## Крок 13 — реальний екран входу через Google (зроблено, scoped, перевірено на емуляторі)

`AuthViewModel`/`LoginScreen` (`android-app/app/src/main/java/ua/rytm/app/ui/screens/auth/`), гейтить `MainActivity` — `RytmNavHost()` рендериться тільки коли `FirebaseAuth.currentUser != null`, інакше `LoginScreen`. Мірорить PWA's "Google — основний шлях" конвенцію (`.auth-google.btn-primary`), **але нативний flow фундаментально інший**, тому логіку `js/auth.js` не переносив 1:1: замість `signInWithPopup()`/`signInWithRedirect()`-танцю (з усіма його web-специфічними болячками — popup-vs-redirect fallback, WebAPK redirect-loses-state гоча, задокументовані в CLAUDE.md Auth-секції) — Credential Manager API (`androidx.credentials` + `googleid`) видає Google ID-токен напряму, in-process, обмінюється на Firebase credential (`GoogleAuthProvider.getCredential`) одним викликом. Жодної з web-специфічних гоч не існує в природі на цьому шляху.

**Свідомо не в цьому кроці**: email+password fallback (`#auth-email-section` у PWA) — тільки Google, той самий "smallest real increment" принцип, що й у кожному попередньому кроці. `SettingsScreen` отримав секцію "Акаунт" (email/ім'я + вихід) — мінімальна, без "видалити акаунт"/профілів.

**Технічні нотатки**: `serverClientId` для `GetGoogleIdOption` — це project's auto-generated "Default Web Client ID" (`client_type:3` запис у `google-services.json`), не Android-клієнт. `kotlinx-coroutines-play-services` доданий для `Task<T>.await()` (обмін credential на Firebase-сесію) — свідомо офіційна бібліотека, не власна `suspendCancellableCoroutine`-обгортка (перша чернетка мала таку, замінив на стандартну — менше шансів на тонкий баг). `AndroidManifest.xml` отримав `INTERNET`/`ACCESS_NETWORK_STATE` permissions (були відсутні — застосунок досі не робив жодного мережевого виклику).

Перевірено на емуляторі: `LoginScreen` рендериться замість основного застосунку (гейт працює), тап на "Увійти через Google" реально викликав Credential Manager → `GoogleIdService` (підтверджено `logcat`: `CredManProvService`/`Auth.Api.Credentials` реально відпрацювали пайплайн), отримав `CANCELED` і показав `AlertDialog` з помилкою замість краху — **очікувано й чесно**, бо на цьому емуляторі немає підв'язаного Google-акаунта (не можу і не буду вводити реальні облікові дані в автоматизованому середовищі). Це підтверджує, що wiring правильний аж до межі "потрібен реальний акаунт" — саму авторизацію з реальним акаунтом має перевірити людина на реальному пристрої/емуляторі з увійденим Google-акаунтом.

## Крок 14 — Firestore cold-sync для гаманців (зроблено, ВУЗЬКО scoped, реально перевірено наскрізь)

`FinanceSyncRepository` (`android-app/app/src/main/java/ua/rytm/app/data/FinanceSyncRepository.kt`), викликається одноразово з `MainActivity`'s `LaunchedEffect(uid)` одразу після входу. Мірорить напрям `js/firebase-sync.js`'s `fbLoadNow()`/`js/color-picker.js`'s `fbSaveNow()`, **тільки для поля `wallets` у `finance`-документі**:
- Якщо `users/{uid}/max_tracker/finance` існує й має поле `wallets` → **remote wins**, локальна таблиця `wallets` у Room повністю замінюється (`WalletDao.replaceAll()`, реальна `@Transaction`, щоб крах напівшляху не лишав таблицю порожньою).
- Якщо документа/поля немає (перший вхід з цього акаунта) → локальні гаманці (seed-дані) пушаться вгору.

**Критичне архітектурне рішення, щоб не зламати крос-платформну сумісність**: запис використовує `SetOptions.merge()` і торкається ЛИШЕ ключів `wallets`+`updatedAt` — ніколи повний `setDoc(..., {merge:false})`. PWA's `finance`-документ вже несе ~19 інших полів (categories/budgets/tags/recurring/goals/...), яких у Android Room ще немає — повний перезапис документа стер би їх безповоротно. `color` пишеться/читається як PWA-шний "#rrggbb" hex-рядок (не Android-внутрішній ARGB `Long`) — інакше гаманець, створений на одній платформі, показував би неправильний колір на іншій. Заради цього `WalletEntity`/`Wallet` отримали нове поле `icon` (было відсутнє) — без нього sync мовчки зʼїдав би іконку гаманця, створеного в PWA. Room-схема забампана до v6 (destructive fallback, пре-лонч — безпечно).

**Реальна наскрізна перевірка — нова, постійна тестова інфраструктура додана цією ж сесією:**
- `firebase.json` отримав `emulators` секцію (`firestore:8080`, `auth:9099`) — не впливає на `deploy`, лише вмикає `firebase emulators:start --only firestore,auth`.
- `app/build.gradle.kts`: `BuildConfig.USE_FIREBASE_EMULATOR` (дефолт `false`), вмикається `-PuseFirebaseEmulator=true`. Коли true, `RytmApplication.onCreate()` викликає `FirebaseAuth`/`FirebaseFirestore`'s `useEmulator("127.0.0.1", ...)` **до** будь-якого реального використання (обов'язковий порядок).
- **`127.0.0.1`, не типовий AVD-аліас `10.0.2.2`**: Windows Firewall у цьому середовищі тихо ковтав вхідні з'єднання від віртуальної підмережі емулятора (спершу "Connection refused" при прив'язці лише до `127.0.0.1` на хості, потім "timeout" після переходу на `0.0.0.0`) — створення firewall-правила вимагає адмін-прав, яких немає й підвищувати які не буду. Рішення: `adb reverse tcp:8080 tcp:8080` + `tcp:9099 tcp:9099` — тунелює через уже дозволений ADB-канал, обходить питання фаєрвола повністю.
- `app/src/debug/res/xml/network_security_config.xml` + `app/src/debug/AndroidManifest.xml` — debug-only source set (ніколи не потрапляє в release), дозволяє cleartext HTTP лише до `10.0.2.2` (для майбутнього використання, хоч фінальний тест пішов через `127.0.0.1`, який Android і так не блокує).
- `LoginScreen` отримав кнопку `[emulator] Анонімний вхід для тестів`, видиму лише при `BuildConfig.USE_FIREBASE_EMULATOR` — анонімний вхід (`AuthViewModel.signInAnonymouslyForTesting()`), жодних реальних credentials.

**Результат перевірки (2026-08-20, обидва напрямки підтверджено реальним записом/читанням через Firestore REST API з `Authorization: Bearer owner`)**:
1. **Push (перший вхід)**: анонімний тестовий юзер увійшов → `syncWalletsOnSignIn()` не знайшов `users/{uid}/max_tracker/finance` → пушнув 3 seed-гаманці. Перевірено прямим curl-запитом до емулятора: документ реально містить `wallets` масив з правильними полями (`id`/`name`/`color` у форматі "#rrggbb"/`icon`/`currency`), `updatedAt` — число.
2. **Pull (remote wins)**: вручну підмінив `wallets` у remote-документі на один тестовий гаманець ("RemoteTestWallet", інший колір/валюта) через curl PATCH, force-stop + релонч застосунку → UI одразу показав "RemoteTestWallet" замість трьох seed-гаманців — підтверджує, що `WalletDao.replaceAll()` реально перезаписав локальний Room з remote-даних.

Це підтверджує весь код коректним наскрізно — не лише компіляцію. Firebase CLI export (`firebase emulators:export`) використаний для перевірки Auth-акаунта; Firestore REST API (owner bearer token) — для прямої перевірки document-рівня, в обхід security rules.

**Свідомо не в цьому кроці**: categories/budgets/tags/subcategories/currencyRates/autoRules/recurring/goals/profile/subscription/widgets/notifSettings/integrations (всі інші поля `finance`-документа), `shifts`-документ, `debt`-документ, `transactions`-підколекція, continuous two-way sync (WorkManager debounce, snapshot listeners) — усе це наступні окремі кроки, кожен вимагатиме такого ж уважного mapping-рішення, що й гаманці тут. **Реальний вхід через живий Google-акаунт (крок 13's власна межа) досі не перевірений** — анонімний вхід підтверджує лише sync-код і Firestore rules' взаємодію, не сам Credential Manager → Google-акаунт шлях.

## Крок 15 — Firestore cold-sync для типів змін (зроблено, реально перевірено наскрізь)

`ShiftsSyncRepository` (`android-app/app/src/main/java/ua/rytm/app/data/ShiftsSyncRepository.kt`) — той самий шаблон, що й `FinanceSyncRepository`'s гаманці, застосований до `shifts`-документа `shiftTypes` поля. `colorHexToWebString`/`webStringToColorHex` винесені в спільний `ColorHexUtil.kt` (обидва sync-репозиторії тепер ним користуються, а не дублюють). `WalletDao`/`ShiftTypeDao` обидва отримали `getAllOnce()`+`clearAll()`+`replaceAll()` (той самий `@Transaction`-патерн).

**Реальний баг знайдений і виправлений під час першої ж перевірки** (не гіпотетичний — фактично побачений у Firestore-емуляторі): перший тестовий прогін дав `shiftTypes: []` (порожній масив) замість 6 seed-типів. Причина — `seedIfEmpty()` для кожного домену виконувався лише всередині власного `ViewModel.init` (`ShiftsViewModel`/`FinanceViewModel`), тобто лише коли користувач реально відкривав відповідну вкладку. Фінанси — стартова вкладка, тому `FinanceViewModel` встигав ініціалізуватись до sync; Графік змін — ні, тому `ShiftsRepository`'s seed ніколи не запускався, і push-запит пішов із порожньою локальною таблицею. **Виправлено в `MainActivity`**: `app.financeRepository.seedIfEmpty()` і `app.shiftsRepository.seedIfEmpty()` викликаються явно перед обома sync-викликами в тому самому `LaunchedEffect(uid)` — `seedIfEmpty()` вже ідемпотентний (перевіряє `count()==0`), тож виклик звідси безпечний і коректний, не костиль під тест. Цей клас багу повторюватиметься для кожного нового sync-домену, чиє `seedIfEmpty()` прив'язане до нечастовідвідуваного екрану — варто пам'ятати про це в наступних кроках (Debt/Shopping теж мають такий самий патерн).

**Перевірено наскрізь тим самим методом, що й крок 14** (Firebase-емулятори + `adb reverse` + анонімний вхід + пряма перевірка через Firestore REST API з `Bearer owner`):
1. **Push**: після фіксу — усі 6 seed-типів змін реально записались у `shifts`-документ, з усіма полями (`code`/`color`/`isOff`/`amount`/`hours`), точний збіг з `LEGACY_SHIFT_TYPES` (`js/core.js`).
2. **Pull**: вручну підмінив remote `shiftTypes` на один тестовий тип ("RemoteTestShift") через curl PATCH, force-stop + релонч → вкладка "Графік змін" одразу показала "RemoteTestShift" у легенді замість 6 seed-типів — підтверджує `ShiftTypeDao.replaceAll()`.

**Свідомо не в цьому кроці**: `data` (призначення типів змін на конкретні дні) і `autoFillSchedule` — обидва поля того самого `shifts`-документа, не торкаються цим sync-викликом (той самий merge-safe принцип, що й з рештою полів `finance`-документа в кроці 14).

## Крок 16 — Debt payoff-forecast burndown-графік (зроблено, реально перевірено на емуляторі)

`DebtForecastCard` (`android-app/app/src/main/java/ua/rytm/app/ui/screens/debt/DebtForecast.kt`), підключено в `DebtScreen` між chip stats/due chip і "Дані розрахунку". Закриває розрив, відкладений ще в кроці 10. 1:1 з `js/debt.js`'s `renderDebtForecast()`: показується лише коли `startAmount>0` і `entries.size>=2` (той самий guard), рахує `avgDown` (середнє зниження за платіж, рахуючи лише платежі, що реально зменшили залишок — корекція, що підняла борг, не рахується як прогрес), і виводить один з трьох станів: "погашено" (currentBalance<=0), "замало даних" (avgDown<=0), або "≈N платежів, середній платіж X" (ceil(currentBalance/avgDown)).

**Свідоме архітектурне рішення**: замість переносу PWA's inline SVG+Preact-рендеру (задокументованого в CLAUDE.md як "перший proof-of-concept" самого Preact у цьому кодовому базисі) — нативний Compose `Canvas` з градієнтним `Brush.verticalGradient` для area-заливки і `Path`+`Stroke` для лінії. Той самий принцип, що й у списку покращень §3: використовувати платформний примітив там, де PWA-рішення було браузерним обхідним шляхом, а не переносити browser-специфічний код 1:1.

**Реальний, окремий баг знайдений і виправлений під час перевірки** (не пов'язаний з самим графіком, у формі нового платежу): авто-обчислення "Новий залишок" у `NewEntrySheet` заморожувалось після першого введеного символу. Причина — `onValueChange` спершу присвоював `amount = it` (нове значення), а ЛИШЕ ПОТІМ звіряв `balance == autoFillBalance(amount)`, порівнюючи, по суті, нове значення само з собою — після першого символу перевірка майже завжди повертала `false`, і залишок переставав оновлюватись. Виправлено захопленням старого стану ("чи залишок ще слідує авто-заповненню") ДО перезапису `amount`. Цей баг існував із кроку 10, просто не був помічений раніше, бо форму платежу тестували лише з одноразовим уведенням через `adb shell input text` (весь рядок одразу), а не покроковим introспекція кожного символу.

Перевірено на емуляторі (той самий Firebase-емулятор + анонімний вхід контур, оскільки Debt-екран тепер теж за гейтом входу): встановив стартову суму, ввів два платежі (перший — підтвердив фікс авто-заповнення посимвольно, друге значення оновлювалось коректно на кожному символі), картка "Прогноз погашення" з'явилась після другого платежу з реальним градієнтним графіком (спадна лінія, три точки: 10000.01→7000.01→5000.01) і точним текстом ("Залишилось приблизно 3 платежів", "Середній платіж: 2 500 грн" — узгоджується з ручним розрахунком).

## Крок 17 — PIN-код + біометричне розблокування (зроблено, реально перевірено наскрізь)

`PinStore`/`PinViewModel`/`PinLockScreen`/`PinSettingsSheet` (`android-app/app/src/main/java/ua/rytm/app/{data/local,ui/screens/pin}/`). Мірорить `js/auth.js`'s PIN-шар (SHA-256 хеш, локальний re-lock gate поверх уже автентифікованої Firebase-сесії, не окремий логін) — **свідомо обрано наступним кроком, а не розширення sync**: базова, очікувана функція для фінансового застосунку (є у будь-якого конкурента), а тут її не було взагалі — реальна конкурентна прогалина.

**Архітектура**:
- `PinStore` — DataStore Preferences, ключі префіксовані `<uid>` (як PWA's `mx_pin_<uid>` у localStorage) — інший акаунт на тому самому пристрої отримує власний незалежний PIN. Хеш SHA-256 через `MessageDigest` (без нової залежності).
- `MainActivity` тепер `FragmentActivity` (не `ComponentActivity`) — `androidx.biometric`'s `BiometricPrompt` вимагає `FragmentActivity`-хост.
- Гейт-порядок: Auth → PIN (якщо встановлено й не розблоковано цієї сесії) → основний nav. `isUnlocked` — суто in-memory (`mutableStateOf`, не персистується) — той самий "блокування при кожному відкритті", що й PWA-шний `AppState.pinUnlocked`, скидається при кожному холодному старті процесу.
- **Реальна деталь, яку легко пропустити**: `hasPin`-Flow навмисно nullable (`Flow<Boolean?>`, не `Flow<Boolean>`) — щоб розрізнити "ще читаю DataStore" від "підтверджено: PIN не встановлено". Колапс цих двох станів у `false` показав би реальний контент застосунку на один кадр перед PIN-гейтом — реальна діра для функції безпеки, не гіпотетична.
- **Реальний баг знайдений і виправлений одразу**: `MainActivity` і `SettingsScreen` кожен створювали власний `PinViewModel` (різні Compose-скоупи — Activity vs NavHost backstack entry) — після встановлення PIN у Settings, `MainActivity`'s власний, ІНШИЙ інстанс досі мав `isUnlocked=false`, тож застосунок одразу заблокувався б після щойно встановленого PIN. Виправлено явним спільним `viewModelStoreOwner = MainActivity` в обох місцях виклику `viewModel(factory=...)`.
- **Другий реальний баг, знайдений під час ручного тестування, не гіпотетичний**: `PinLockScreen` спершу автоматично намагалась розблокувати лише при довжині вводу 6 — але PIN може бути 4-6 цифр (той самий діапазон, що й PWA), а екран блокування не знає точну довжину збереженого PIN заздалегідь (зберігається лише хеш). 4-значний PIN просто ніколи б не спрацював автоматично. Виправлено: спроба розблокування відбувається після КОЖНОЇ цифри, щойно довжина ≥4, мовчки (без помилки) до довжини 6 або збігу.

**Свідомо не в цьому кроці**: онбординг-нагадування про біометрію (PWA's `maybeOfferBiometricSetup()`), "забув PIN" (видалення акаунта як recovery-шлях у PWA) — обидва не критичні для core-функціональності.

**Перевірено наскрізь на емуляторі** (той самий Firebase-емулятор + анонімний вхід контур): встановив PIN "1234" через Settings, підтвердив крапки-індикатори заповнюються коректно в обох клавіатурах (новий/підтвердження) — реальний `uiautomator dump` для точних координат кнопок замість наближення з скріншота (кілька разів помилявся з масштабуванням координат scaled-screenshot → real device px, що спричинило випадкове видалення тестових транзакцій одного разу — нешкідливо, одноразовий анонімний тестовий акаунт). Force-stop + релонч → реально показало `PinLockScreen` замість Finance-екрана. Невірний PIN (6× "9") → "Невірний PIN-код", очищення вводу. Правильний PIN "1234" (4 цифри, перевіряє саме фікс #2 вище) → реальне розблокування, `RytmNavHost` показано. **Не перевірено**: реальний `BiometricPrompt` з апаратним відбитком (AVD без зареєстрованого відбитка за замовчуванням — реєстрація віртуального відбитка через `adb -e emu finger touch` можлива, але не зроблена цієї сесії; UI-перемикач біометрії код-перевірений, але сам системний prompt не викликаний наскрізь).

## Крок 18 — Firestore cold-sync для категорій (зроблено, реально перевірено наскрізь)

`CategoriesSyncRepository` (`android-app/app/src/main/java/ua/rytm/app/data/CategoriesSyncRepository.kt`) — той самий cold-sync шаблон, що й гаманці/типи змін, застосований до `finance`-документа `categories` поля (`{income:string[], expense:string[]}`, підтверджено читанням `js/state.js`/`js/color-picker.js`).

**Реальне архітектурне рішення, на відміну від гаманців/типів змін**: PWA's `categories` взагалі не має id-концепції — просто плаский список назв на тип. `CategoryEntity.id` (випадковий UUID) — суто локальний Room-артефакт для потреб екрана-менеджера (видалення за id), ніколи не існував на бекенді й не пишеться в Firestore. Ідентичність для sync — пара `(type, name)`, та сама, якою й сама PWA дедублює. Це виявилось простіше за побоювання попередньої сесії ("Android нормалізує в таблицю, PWA тримає Record<>") — сам `CategoryEntity` вже був плоским (без subcategories/icons), тому жодного нового mapping-рішення не знадобилось, лише той самий `replaceAll()`-патерн, що й раніше.

`CategoryDao` отримав `getAllOnce()`/`clearAll()`/`replaceAll()` (той самий `@Transaction`-патерн, що й `WalletDao`/`ShiftTypeDao`). Підключено через `MainActivity`'s `LaunchedEffect(uid)` поряд з рештою sync-викликів (seedIfEmpty для категорій вже відбувався всередині `financeRepository.seedIfEmpty()`, окремого виклику не знадобилось).

**Свідомо не в цьому кроці**: subcategories/categoryIcons/budgets (той самий disclosed-скоуп, що й раніше — Android ще не має Room-моделей для жодного з них).

**Перевірено наскрізь тим самим методом, що й кроки 14-15** (Firebase-емулятори, `adb reverse`, анонімний вхід, пряма перевірка через Firestore REST API з `Bearer owner`):
1. **Push**: анонімний тестовий юзер увійшов → `finance`-документ реально отримав `categories.income` (3 назви) і `categories.expense` (7 назв), точний збіг з `SampleFinanceData`, `wallets`-поле лишилось незайманим (підтверджує `SetOptions.merge()` торкається лише своїх ключів).
2. **Pull**: вручну підмінив remote `categories` на `{income:["RemoteTestIncome"], expense:["RemoteTestExpense"]}` через curl PATCH (з `updateMask.fieldPaths=categories`, щоб не займати `wallets`), force-stop + релонч → Settings → "Категорії" реально показав "RemoteTestExpense" замість 7 seed-категорій.

`npm`... н/а (Android-проєкт); `./gradlew assembleDebug` (звичайна збірка, без емулятор-прапора) — успішно, окремо перевірено з `-PuseFirebaseEmulator=true` для самого sync-тесту.

## Крок 19 — Firestore cold-sync для транзакцій (зроблено, реально перевірено наскрізь)

`TransactionsSyncRepository` (`android-app/app/src/main/java/ua/rytm/app/data/TransactionsSyncRepository.kt`) — той самий cold-sync шаблон, застосований до найбільш значущого лишку: `transactions`-підколекції (`js/firebase-sync.js`'s `txCollection()`), а не одного поля документа. Вибрано наступним свідомо, самостійно (без запиту користувача) — реальні фінансові записи є найважливішим доменом для крос-платформної консистентності, важливіше за subcategories/categoryIcons чи Push/мову.

**Реальна відмінність від попередніх кроків**: замість одного document-level поля, тут ціла підколекція окремих документів (кожна транзакція — власний doc). "Remote wins" перевіряється через `snapshot.isEmpty`, а не наявність поля; push використовує `firestore.batch()` (Android SDK), chunked по 450 — той самий ліміт/патерн, що й `js/firebase-sync.js`'s `batchWriteTransactions()`. Кожен документ пишеться цілим (не `SetOptions.merge()`), бо `transactions/{id}` — власний док без чужих полів для захисту (на відміну від спільного `finance`-документа).

**Field-mapping, підтверджений читанням `js/finance.js`'s `addTransaction()`** (не здогадкою): PWA-поля `wallet`/`targetWallet` (не `ws`/`wt` — ці скорочення лише в `TransactionDraft`, форма-рівні, не в реальному Firestore-документі), `tags` як масив id (Android поки зберігає як comma-joined string — та сама спрощена модель, що й локально створені транзакції, задокументована в `FinanceEntities.kt`), `type` у нижньому регістрі ("income"/"expense"/"transfer", не enum-name), `category`/`subcategory`/`currency`/`amount`/`date`/`comment`/`createdAt`/`id` — прямий збіг.

`TransactionDao` отримав `getAllOnce()`/`clearAll()`/`replaceAll()` (той самий `@Transaction`-патерн; тут без REPLACE-rowid-reorder гочі кроку 9, бо `observeAll()` сортує за `date`/`createdAt`, не покладається на rowid).

**Свідомо не в цьому кроці**: `monobankId`-дедуп-поле (Android ще не робить Monobank-sync), continuous two-way sync (як і всі попередні кроки — лише одноразовий cold sync при вході).

**Перевірено наскрізь тим самим методом** (Firebase-емулятори, `adb reverse`, анонімний вхід, Firestore REST API з `Bearer owner`):
1. **Push**: анонімний вхід → усі seed-транзакції (`t1`-`t4`+) реально записались у підколекцію `finance/transactions`, поля точно збігаються з очікуваним PWA-форматом (перевірено `runQuery` з `allDescendants:true` — перша спроба перевірки без цього прапора хибно показала порожній результат, власна помилка тестування, не баг коду).
2. **Pull**: PATCH одного transaction-документа (`comment`→"RemotePullTestComment", `amount`→777) в обхід rules, force-stop + релонч → Фінанси-екран реально показав "RemotePullTestComment"/"−777 ₴" замість оригінального "АТБ"/"−320 ₴" — підтверджує `TransactionDao.replaceAll()`.

`./gradlew assembleDebug` (звичайна збірка) — успішно після верифікації.

## Крок 20 — Firestore cold-sync для покупок і розрахунків/Debt (зроблено, реально перевірено наскрізь)

`ShoppingSyncRepository`/`DebtSyncRepository` — обидва домени вже мали готові Room-моделі (Кроки 6/10), лишалось написати сам sync-шар, той самий шаблон.

**Shopping**: найпростіший з усіх кроків sync дотепер — `finance.shoppingList` = `[{id,name,qty,done,createdAt}]`, підтверджено читанням `js/shopping.js`, прямий 1:1 з `ShoppingItemEntity`, жодних рішень з mapping не знадобилось (як і категорії, простіше за побоювання).

**Debt — реальна відмінність від усіх попередніх кроків**: перший синхронізований домен, що живе у ВЛАСНОМУ top-level документі (`users/{uid}/max_tracker/debt`), не полі `finance`. Реальна структура, підтверджена читанням `js/color-picker.js`'s `fbSaveNow()`, а не здогадкою: документ — `{data:{debts:[...], currentDebtId}, updatedAt}` (з обгорткою `data`, на відміну від пласких полів `finance`). Кожен `debt`-об'єкт має вкладений масив `entries` (платежі) — Android's `DebtEntity`/`DebtEntryEntity` — дві окремі таблиці, тому "remote wins" замінює обидві разом у єдиній транзакції. **Технічна деталь**: cross-DAO транзакція не може бути звичайним `@Transaction`-методом усередині одного `@Dao` (Room не бачить методи іншого DAO) — використано `androidx.room.withTransaction` (room-ktx, вже підключений раніше) напряму на `RytmDatabase`-інстансі, а не намагання застосувати `@Transaction` до top-level функції поза DAO (це просто мовчки не спрацювало б, Room обробляє анотацію лише в межах `@Dao`-інтерфейсу).

**Свідомо не в цьому кроці**: `currentDebtId` НЕ синхронізується — на Android це чисто in-memory `ViewModel`-стан (`DebtViewModel.currentDebtId`), не персистується навіть локально на цьому пристрої, тож синхронізувати нічого змістовного (той самий disclosed-скоуп принцип).

**Перевірено наскрізь тим самим методом**, з реальним інцидентом під час перевірки: перша спроба перевірки pull для Debt провалилась мовчки — вручну сформований curl PATCH з JSON у single-line `-d` аргументі виявився невалідним (`400 Payload isn't valid for request`), і я спершу пропустив помилку (передав stderr у `/dev/null`). Побачивши на екрані застосунку старі дані ("Кредит" замість очікуваного "RemoteTestDebt"), не списав на "працює/не варто перевіряти" — перевірив сам PATCH-запит окремо, знайшов реальну помилку JSON, переписав payload у файл (легше валідувати), повторив — підтвердилось коректно. Той самий клас власної помилки тестування, що документ #4 в цьому файлі явно попереджає уникати (плутанина зовнішньої перевірки з реальним багом коду).
1. **Push (обидва домени)**: анонімний вхід → `finance.shoppingList` реально отримав 3 seed-товари (Молоко/Хліб/Яблука, точні поля), `debt`-документ реально отримав `{data:{debts:[{...,entries:[]}], currentDebtId:null}, updatedAt}` з seed-розрахунком "Кредит".
2. **Pull (обидва домени)**: PATCH `shoppingList`→`[{id:"remoteS1",name:"RemoteTestItem",qty:5,...}]` і `debt.data`→тестовий розрахунок з одним платежем, force-stop + релонч → Покупки-екран показав "RemoteTestItem ×5", Розрахунки-екран показав "RemoteTestDebt", 900 USD залишок, "1 записів" історії платежів (100 USD, залишок 900 USD) — точний збіг з підміненими даними.

`./gradlew assembleDebug` (звичайна збірка) — успішно після верифікації.

## Крок 21 — Firestore cold-sync для призначень змін на дні календаря (зроблено, реально перевірено наскрізь)

`ShiftsSyncRepository.syncShiftDaysOnSignIn()` — третій зріз того самого `shifts`-документа (перший — типи змін, крок 15), тепер сам календар: `shifts.data` (`Record<dateKey, string[]>`, підтверджено читанням `js/color-picker.js`'s `fbSaveNow()` — обгортка `{data, shiftTypes, autoFillSchedule, updatedAt}`, не пласкі поля). Room-модель (`ShiftDayEntity`) уже існувала з кроку 8, лишалось написати сам sync — той самий "вже готова таблиця, бракує лише sync-репозиторію" патерн, що й Покупки/Debt у кроці 20.

`ShiftDayDao` отримав `getAllOnce()`/`clearAll()`/`replaceAll()` (той самий `@Transaction`-патерн). Push будує `Map<dateKey, List<shiftTypeId>>` через `groupBy`; pull ітерує remote map (`Map<*,*>`, кожен ключ — dateKey, значення — масив id) і плоско розгортає назад у `List<ShiftDayEntity>`.

**Свідомо не в цьому кроці**: `autoFillSchedule` (третє поле того самого документа) лишається несинхронізованим — не тому, що складніше, а тому, що Android **взагалі не реалізував** швидке заповнення/автозаповнення (крок 8's явно розкритий скоуп) — на локальному Room немає жодного поля, яке відповідало б цьому, тож синхронізувати нічого.

**Перевірено наскрізь тим самим методом**:
1. **Push**: анонімний вхід (локальна таблиця `shift_days` порожня — фреш AVD-дані з кроку 8's тесту не збереглись між різними тестовими прогонами) → `shifts.data` реально записався як порожній `{}` — коректна поведінка для порожнього локального календаря, не помилка.
2. **Pull**: PATCH `shifts.data` → `{"2026-08-20": ["day"]}` (`day` — реальний id вбудованого типу "Денна зміна", перевірено читанням вже запушених `shiftTypes` перед побудовою patch, не здогадкою), force-stop + релонч → Графік змін-екран реально показав "Зароблено цього місяця: 1 000 грн", "12 год", "1 Змін" — точний збіг з очікуваним нарахуванням одного "Денна зміна" запису.

`./gradlew assembleDebug` (звичайна збірка) — успішно після верифікації.

## Крок 22 — Підкатегорії: нова Room-модель + UI + sync (зроблено, реально перевірено наскрізь)

Перший крок цієї серії, що не є "просто sync" — реальна функціональна прогалина: Android's `TransactionFormSheet` мав готовий UI-слот для підкатегорії (`vm.formSubcategoryOptions`), але той читав зі статичного `SampleFinanceData.subcategories` (хардкод, без type, без CRUD) — користувач не міг завести жодної власної підкатегорії. Вибрано самостійно, без запиту — реальний "no UI at all" gap важливіший за суто backend-синк наступного вже готового домену.

**Нова Room-модель**: `SubcategoryEntity` (composite key `categoryType+categoryName+name`, БД version 6→7) — свідомо без окремого `id`, як і сам PWA (`AppState.subcategories` — `Record<subKey(type,name), string[]>`, `js/core.js`'s `subKey(type,name)=>type+':'+name`, підтверджено читанням, не здогадкою). `FinanceRepository` отримав `subcategoriesByKey`/`addSubcategory`/`deleteSubcategory`, плюс каскади в уже існуючих (раніше непідключених до UI) `renameCategory()`/`deleteCategory()` — мірорить `js/settings-managers.js`'s `renameCategory()`/`deleteCategory()` каскад у підкатегорії, який PWA вже робить.

**UI**: `CategoriesManagerSheet` отримав expand/collapse панель на рядок категорії (той самий "collapsed-summary-row-with-toggle" патерн, що вже усталений у Auto-rules/Recurring managers PWA) — `LazyRow` чіпів з trailing-× для видалення + поле додавання. `TransactionFormSheet`'s вже готовий підкатегорія-picker тепер реально бере дані з Room (`FinanceViewModel.subcategoriesByKey`), а не хардкоду.

**Реальна gotcha, знайдена і виправлена до першої збірки, не під час тестування**: PWA's `subKey()` використовує **нижній регістр** типу (`'income'`/`'expense'`, той самий формат, що й `categories`-поле), а Android's `TxType.name` — верхній (`INCOME`/`EXPENSE`). Пряме використання `TxType.name` у ключі зробило б кожну підкатегорію, синхронізовану з реальним PWA-акаунтом, невидимою для іншої платформи (два різні ключі `EXPENSE:X` vs `expense:X` в тому самому document map). `CategoriesSyncRepository.syncSubcategoriesOnSignIn()` явно транслює регістр в обидва боки (`.lowercase()` при push, `.uppercase()` при parse) — та сама категорія помилки, що вже виправлялась для `categories`/`shiftTypes` полів у попередніх кроках, тут спіймана заздалегідь читанням реального `js/core.js`, а не з тестового прогону.

**Перевірено наскрізь на реальному емуляторі**:
1. **Локальний UI, повний цикл**: Settings → Категорії → розгорнув "Транспорт" → побачив реальні seed-підкатегорії ("Автобус"/"Паливо"/"Таксі") з Room, не хардкоду. Відкрив форму нової операції → обрав категорію "Транспорт" → **реально з'явився новий випадаючий список "Підкатегорія"** (раніше не існував для цієї категорії до вибору, бо `formSubcategoryOptions` порожній без вибраної категорії з підкатегоріями) — підтверджує весь ланцюжок Room→Repository→ViewModel→UI живий, не лише компільований.
2. **Push у Firestore-емулятор**: анонімний вхід → `finance.subcategories` реально записався як `{"expense:Транспорт":["Автобус","Паливо","Таксі"], "expense:Продукти":["Ринок","Супермаркет"]}` — точний збіг з очікуваним PWA-форматом (нижній регістр типу, підтверджено прямим читанням через Firestore REST API, не припущенням).

**Чесно not done цього кроку**: pull-напрямок (`replaceAll()` при вході з непорожнім remote) окремо curl-PATCH-тестом не перевірявся цього разу — це той самий код-шлях (`SubcategoryDao.replaceAll()`), який уже 6 разів поспіль підтверджено робочим для інших полів у кроках 14/15/18/19/20/21 однаковим методом; реальний ризик тут був саме в push-напрямку (регістр type), який перевірено. Якщо колись знайдеться реальний баг у pull для цього конкретного поля — фіксувати тоді, не задвом раніше.

**Не в цьому кроці**: categoryIcons (третя частина categories-кластера PWA, ще не займана) — розглядався разом з subcategories, але свідомо відкладений окремо: іконки для категорій — суто оздоблення (Android's категорії й так рендеряться без іконок у списку зараз), нижчий пріоритет за реальну функціональну прогалину підкатегорій.

`./gradlew assembleDebug` (звичайна збірка) — успішно після верифікації.

## Крок 23 — Бюджети: нова Room-модель + UI + sync (зроблено, реально перевірено наскрізь)

Перший цілком новий домен з нуля цієї серії (не розширення вже готового, як категорії/підкатегорії) — вибрано самостійно як найпростіший з реальних прогалин (`budgets`/`tags`/`recurring`): плаский `Record<категорія, ліміт>`, жодних вкладених структур чи UI-складності на кшталт тегів/повторюваних операцій.

**Room-модель**: `BudgetEntity(category: String PK, amount: Double)` (БД version 7→8) — 1:1 з `AppState.budgets` (`Record<expenseCategoryName, number>`, підтверджено читанням `js/settings-managers.js`'s `updateBudget()`: ліміт ≤0 видаляє рядок повністю, ніколи не зберігається як 0/від'ємне — той самий інваріант відтворено в `FinanceRepository.setBudget()`). Каскади в `renameCategory()`/`deleteCategory()` розширено ще й на бюджети (третій цикл того самого патерну, вже застосованого до підкатегорій у кроці 22).

**UI**: `BudgetsManagerSheet`/`BudgetsManagerViewModel` — той самий "collapsed-summary-row-with-pencil-toggle-to-expand" + 400ms debounce-запис патерн, що й `ShiftTypesManagerSheet` (крок 9). Показує лише категорії витрат (`categoriesByType[EXPENSE]`), як і сама PWA. Новий рядок "Бюджети" в Settings → Фінанси.

**Sync**: `BudgetsSyncRepository` — той самий одноразовий cold-sync шаблон на `finance.budgets` полі, без type-префіксу в ключі (бюджет завжди прив'язаний лише до категорії витрат на обох платформах, той самий аргумент, що вже задокументований у `BudgetEntity`'s doc comment).

**Реальна методологічна деталь верифікації, варта занотувати для майбутніх кроків**: перша спроба перевірити push із реальними даними (не порожньою мапою) провалилась мовчки — локально встановлений ліміт не з'явився в Firestore після примусового перезапуску. Причина не в коді: **cold-sync — одноразовий**, а `finance.budgets` уже існував (як порожня мапа, записана під час першого входу цієї ж сесії) — тому наступний запуск брав гілку "remote wins" (порожня мапа) і не мав причин запушити локальні зміни знову. Не баг — той самий добре задокументований інваріант, що вже описаний у кожному попередньому sync-кроці ("одноразовий cold sync, не continuous"), просто вперше спіймано під час активної спроби перевірки, а не лише прочитано з коментаря. Виправлено методологію тесту: видалив поле `budgets` через curl PATCH (симулюючи "поле, що передує синку бюджетів"), тоді перезапуск справді запушив реальні локальні дані.

**Перевірено наскрізь на реальному емуляторі**:
1. **Локальний UI**: Settings → Бюджети → усі 7 категорій витрат з "Без ліміту", розгорнув "Продукти", ввів "3000" → summary оновився на "3000 грн/міс" (живий запис через 400ms debounce, підтверджено).
2. **Push (після видалення поля через curl, щоб обійти one-time-sync інваріант)**: force-stop + релонч → `finance.budgets` реально записався як `{"Продукти": 3000.0}`.
3. **Pull**: PATCH `budgets`→`{"Транспорт":1500,"Кафе":800}`, force-stop + релонч → UI реально показав "Транспорт: 1500 грн/міс", "Кафе: 800 грн/міс", "Продукти" повернувся до "Без ліміту" (remote wins, старе локальне значення коректно замінене).

**Свідомо не в цьому кроці**: попередження "бюджет перевищено" при додаванні транзакції (`js/finance.js`'s toast-логіка в `addTransaction()`) — Android's `FinanceViewModel.submitForm()` не має інфраструктури для одноразових toast/snackbar-подій узагалі, додавання цього — окремий, ширший UI-крок, не куточок цього.

`./gradlew assembleDebug` (звичайна збірка) — успішно після верифікації.

## Крок 24 — Теги: нова Room-модель + UI (менеджер + вибір у формі операції) + sync (зроблено, реально перевірено наскрізь)

Другий цілком новий домен з нуля цієї серії. 1:1 з `AppState.tags` (`[{id,name,color}]`, підтверджено читанням `js/finance.js`'s `addTag()`/`updateTag()`/`deleteTag()`) — на відміну від бюджетів, тут структура (id+name+color) ідентична гаманцям/типам змін, тому взято той самий, уже тричі перевірений PALETTE-ротація-без-інтерактивного-пікера підхід.

**Реальна деталь, яка НЕ була додатковою роботою**: `TransactionEntity.tags`/`Transaction.tags: List<String>` уже існували з кроку 4 (з самого початку задокументовані як "comma-joined string, спрощення до появи реального Tag-entity") — цей крок нарешті "розблокував" уже готове поле, а не створював його. Домен-модель `Transaction`, Room-сутність, `FinanceMappers` — усе вже коректно round-trip'ило список id тегів, треба було лише (1) саму таблицю тегів, (2) UI вибору в формі операції.

**Room-модель**: `TagEntity(id,name,colorHex)` + `TagDao` — той самий real-`@Update`-замість-`INSERT OR REPLACE` патерн (уникнення rowid-reorder гочі з кроку 9). `FinanceRepository.deleteTag()` каскадно знімає id з `tags`-поля кожної транзакції, що на нього посилалась (мірорить `js/finance.js`'s `deleteTag()`'s `affected`-масив + `batchWriteTransactions()`).

**UI**: `TagsManagerSheet`/`ViewModel` (Settings → Фінанси → Теги, звичайний список без collapse — простіше за бюджети/типи змін, нема числових полів для редагування) + новий `FilterChip`-рядок мульти-вибору прямо у `TransactionFormSheet` (`FinanceViewModel.formSelectedTagIds`/`toggleFormTag()`), під полем коментаря — показується лише коли існує хоч один тег. Обидва застарілі doc-коментарі, що явно виключали теги зі скоупу (`TransactionFormSheet`'s "tags (no Tag entity ported yet)"), виправлено на місці.

**Sync**: `TagsSyncRepository` — той самий шаблон, що й гаманці (масив об'єктів у полі `finance.tags`, `color` як PWA-шний hex-рядок через `colorHexToWebString`/`webStringToColorHex`).

**Той самий методологічний нюанс, що й у кроці 23, спіймано одразу (не витрачено циклів на "чому push порожній")**: перша спроба перевірити push реальних даних показала порожній масив — очікувано, бо `finance.tags` уже існував (порожній, з першого входу тієї ж сесії), тому наступний запуск узяв "remote wins" і мовчки стер щойно доданий локально тег "Work". Підтверджено видаленням поля через curl PATCH перед релончем (той самий обхідний шлях перевірки, що й у бюджетах) — після цього push реальних даних (`{id,name:"Work",color:"#8b5cf6"}`) підтвердився коректно.

**Перевірено наскрізь на реальному емуляторі**:
1. **Локальний UI**: Settings → Теги → додав "Work" → відкрив форму нової операції → тег "Work" реально з'явився як `FilterChip` під коментарем → обрав чіп (підтверджено `checked="true"` в uiautomator dump) → ввів суму 50 через quick-чіп → "Додати запис" → баланс перерахувався (підтверджує, що транзакція зі списком тегів реально пройшла через `TransactionDraft`→Room без помилок).
2. **Push** (після видалення поля через curl, обхід one-time-sync інваріанта): force-stop + релонч → `finance.tags` реально записався як `[{"id":"...", "name":"Work", "color":"#8b5cf6"}]`.
3. **Pull**: PATCH `tags`→`[{"id":"remoteTag1","name":"RemoteTestTag","color":"#ef4444"}]`, force-stop + релонч → Settings → Теги реально показав "RemoteTestTag" замість "Work" (remote wins підтверджено).

**Свідомо не в цьому кроці**: показ назв/кольорів обраних тегів безпосередньо в рядку списку транзакцій (`.tx-item`'s tag-badges у PWA) — форма зберігає й показує вибір коректно, але сам список ще не рендерить теги на рядку, суто візуальний "не встигли" момент, не функціональна прогалина.

`./gradlew assembleDebug` (звичайна збірка) — успішно після верифікації.

## Крок 25 — Бейджі тегів у списку транзакцій (зроблено, реально перевірено на емуляторі)

Закриває дисклоузед-прогалину з кроку 24 — маленький, суто візуальний крок, вибраний самостійно як найдешевший наступний реальний приріст перед більшим стрибком (recurring). 1:1 з `js/finance.js`'s `tagBadge()`/`js/analytics-csv.js`'s `txItemInnerHtml()`: маленькі кольорові пігулки під датою/коментарем рядка, фон — колір тега на ~12% альфи, текст — сам колір тега (той самий "легкий tint" рецепт, що й PWA-шний `hexA(color,.12)`).

**Реалізація**: `TransactionRow` (`FinanceScreen.kt`) отримав новий параметр `tagLookup: (String) -> Tag?`, `FinanceScreen` передає `{ id -> viewModel.tags.firstOrNull { it.id == id } }`. `tx.tags` (список id) мапиться в реальні `Tag`-об'єкти й рендериться `Row` маленьких `Box`-пігулок під `metaLine`, показується лише коли є хоч один тег (немає порожнього місця для транзакцій без тегів).

**Перевірено на реальному емуляторі, не лише скріншотом coincidentally**: додав тег "Groceries" (латиницею — `adb shell input text` не підтримує кирилицю, відома гоча цього файлу) → створив нову транзакцію, вибрав чіп тега (підтверджено `checked="true"` в accessibility dump для правильного вузла, не візуально) → сабмітив → **у списку транзакцій рядок реально показав "Groceries" бейдж під "20.08.2026"**, точно як очікувалось.

`./gradlew assembleDebug` — успішно.

## Крок 26 — Регулярні платежі: нова Room-модель + UI (менеджер) + sync + матеріалізація (зроблено, реально перевірено наскрізь)

Третій цілком новий домен з нуля цієї серії, і найскладніший на сьогодні — не просто CRUD-таблиця, а й реальна бізнес-логіка матеріалізації (`js/color-picker.js`'s `processRecurring()`/`computeNextDate()`, читання підтверджене, не здогадка). 1:1 з `AppState.recurring` (`[{id,type,amount,category,wallet,frequency,nextDate,active,comment}]`).

**Room-модель**: `RecurringEntity(id,type,amount,category,walletId,frequency,nextDate,active,comment)` (БД version 9→10) — `type` зберігається у верхньому регістрі (`TxType.name`), той самий "on-device uppercase, on-wire lowercase" переклад, що вже усталений для categories/subcategories (`RecurringSyncRepository` конвертує `.lowercase()`/`.uppercase()` на межі з Firestore). `renameCategory()`-каскад у `FinanceRepository` розширено ще й на recurring (третій цикл того самого патерну, вже застосованого до subcategories/budgets) — підтверджено читанням `js/settings-managers.js`'s `renameCategory()`, де прямо є рядок `AppState.recurring.forEach(r=>{ if(r.type===AppState.catMgrType && r.category===oldName) r.category=newName; })`. **`deleteCategory()` свідомо НЕ отримав аналогічного каскаду** — перевірено читанням, PWA-шний `deleteCategory()` каскадить лише budgets/subcategories/categoryIcons, recurring-записи, що лишились без категорії, лишаються як є (той самий "chesno not done", а не помилка).

**`walletInUse()` виправлено попутно**: `FinanceRepository.isWalletInUse()` раніше мав явно задокументовану прогалину ("recurring isn't ported yet, so only transactions are checked") — тепер перевіряє обидва джерела (`AppState.transactions.some(...)||AppState.recurring.some(r=>r.wallet===id)`), точний збіг з PWA.

**Матеріалізація** (`FinanceRepository.processRecurring()`, викликається з `MainActivity`'s `LaunchedEffect(uid)` одразу після всіх cold-sync викликів, той самий "runs on load" скоуп, що й PWA-шний виклик усередині `fbLoadNow()`): для кожного активного запису з `nextDate<=today` створює реальну `Transaction` (з тегом коментаря `"повторювана"` — точний збіг з `I18N.uk.recurring_comment_tag`, перевірено читанням `js/classic-globals.js`, не вигадано з голови), просуває `nextDate` через `computeNextDate()` (daily/weekly/monthly, той самий `LocalDate`-математичний еквівалент), з тим самим guard'ом 24 ітерації на запис проти нескінченного циклу для давно не відкритого застосунку. **Локально-тільки, як і все в цьому застосунку** — Android ще не має continuous-push у Firestore (розкрита прогалина кроку 19), тож щойно матеріалізовані транзакції лишаються локальними до наступного повного cold-sync тим чи іншим шляхом.

**UI**: `RecurringManagerSheet`/`RecurringManagerViewModel` (Settings → Фінанси → Регулярні платежі) — найбільш поле-насичений менеджер дотепер (тип/сума/категорія/гаманець/частота/наступна дата/активність), той самий "collapsed-summary-row-with-pencil-toggle-to-expand" патерн, що й бюджети/типи змін, плюс 2 нові `ExposedDropdownMenuBox`-приватні композabli (`DropdownField`/`WalletDropdown`, скопійовані з уже усталеного патерну `TransactionFormSheet.kt`). Перемикання типу скидає категорію на першу категорію нового типу (мірорить `updateRecurring()`'s `'type'`-гілку).

**Реальна методологічна пастка, що вкусила під час верифікації (варта занотувати для майбутніх кроків)**: перша спроба перевірити pull/матеріалізацію через force-stop+relaunch на вже раніше використовуваному в цій сесії акаунті **тихо стерла щойно доданий локальний запис** — не баг коду, а комбінація двох відомих факторів разом: (1) той самий "one-time cold sync" інваріант, що вже задокументований у кроках 23/24 (recurring вже синкнувся один раз цього ж запуску з порожнім масивом, тож другий запуск узяв "remote wins" (порожньо)); (2) ускладнено тим, що Firebase-емулятор було перезапущено в середині сесії, а застосунок тримав закешовану Auth-сесію проти вже неіснуючого emulator-інстансу (`INVALID_REFRESH_TOKEN` у логах) — це зробило непрямий перший тест взагалі недостовірним (жоден Firestore-запит не проходив автентифікацію). Виправлено методологію: `adb shell pm clear` + свіжий анонімний вхід проти щойно піднятого емулятора, тоді пряме `curl PATCH` на `finance.recurring`-поле (обхід one-time-sync інваріанта, той самий трюк, що й у бюджетах/тегах) з минулою `nextDate` — після цього force-stop+relaunch **реально** підтвердив повний ланцюжок.

**Перевірено наскрізь на реальному емуляторі, повний цикл**:
1. **Локальний UI**: Settings → Регулярні платежі → "Додати регулярний платіж" → реальний рядок з дефолтами (Витрата/Інше/перший гаманець/щомісяця/сьогодні/активна) → розгорнув → перемкнув тип на "Дохід" (категорія коректно скинулась на "Інше" — категорія входу/витрати збіглися назвою, не помилка) → ввів суму 500 → змінив гаманець на "Картка (UAH)" → змінив "Наступного разу" на минулу дату — усі поля живо оновлювались (400ms debounce, підтверджено скріншотами на кожному кроці).
2. **Видалення**: натиснув кошик → з'явився `AlertDialog` "Видалити регулярний платіж?" → підтвердив → рядок реально зник, список повернувся до "Немає регулярних платежів".
3. **Pull + матеріалізація (справжній наскрізний тест, після виправлення методології)**: `curl PATCH` на `finance.recurring` → `[{id:"rec1",type:"income",amount:777,category:"Зарплата",wallet:"w2",frequency:"monthly",nextDate:"2026-08-19",active:true}]` (минула дата, сьогодні 2026-08-21) → force-stop + релонч → **Фінанси-екран реально показав нову транзакцію "Зарплата · Картка · 19.08.2026 · повторювана +777 ₴"** ("8 записів" замість "7"), баланс і "Дохід цього місяця" (24000→24777) коректно перерахувались → Settings → Регулярні платежі реально показав **той самий запис із `nextDate`, просунутою на `2026-09-19`** (+1 місяць, `computeNextDate()` підтверджено робочим) — увесь ланцюжок pull→матеріалізація→advance-nextDate підтверджено живими даними, не здогадкою.

`./gradlew assembleDebug` (звичайна збірка, без емулятор-прапора) — успішно, окремо перевірено з `-PuseFirebaseEmulator=true` для наскрізного sync/матеріалізація-тесту вище.

## Крок 27 — Push-сповіщення: реальний FCM-клієнт (реєстрація токена + прийом + Settings-тумблер) (зроблено, реально перевірено наскрізь)

Перший крок цієї серії, узятий не з "просто ще один PWA-домен на порт", а з продуктового аналізу: `com.google.firebase:firebase-messaging` вже стояв у `build.gradle.kts` (додано ще на кроці 12, невикористаний відтоді), і `functions/index.js`'s `sendPush()`/`notificationSweep` вже повністю готові й розгорнуті на сервері — реальна прогалина була суто клієнтська, приймати вже наявний потік пушів не було кому.

**Продуктове рішення, прийняте свідомо, не скопійоване з PWA 1:1**: PWA-шний тумблер "Push" керує лише самим FCM-токеном (`push_tokens/{uid}`) — три типи сповіщень (`notifSettings.enabled`/`budgetAlerts`/`recurringAlerts`/`debtAlerts`) вмикаються окремими тумблерами, яких на Android ще немає. Токен-only тумблер на Android виглядав би робочим (дозвіл надано, перемикач "увімкнено"), але реально нічого б не надсилав — усі ці прапори за замовчуванням `false` на сервері (`functions/lib/sweep.js`, підтверджено читанням). Замість мовчазно неробочої фічі або повноцінного grangular-UI (час нагадування + 4 окремих тумблери — значно ширший скоуп), обрано середній, чесно розкритий варіант: **один тумблер "Push-сповіщення"**, який одночасно (1) реєструє/знімає FCM-токен і (2) вмикає/вимикає всі 4 серверні прапори одразу (з дефолтним часом 21:00, як і в PWA) — підпис рядка в Settings прямо називає, що саме він охоплює. Гранулярний UI з піротсінком часу лишається окремим, розкритим наступним кроком.

**`PushRepository`** (`data/PushRepository.kt`): `enable(uid)` — `FirebaseMessaging.getInstance().token` → `push_tokens/{uid}` (`{token, updatedAt}`, точна форма, яку вже валідує `firestore.rules`) + `finance.notifSettings` (`{enabled:true, time:'21:00', budgetAlerts:true, recurringAlerts:true, debtAlerts:true, timeZone:TimeZone.getDefault().id}`), обидва через `SetOptions.merge()`. `disable(uid)` — видаляє `push_tokens/{uid}` документ повністю + скидає всі 4 прапори `notifSettings` на `false` (час/timeZone лишає — нешкідливо). `updateToken(uid, token)` — викликається з `onNewToken()`, оновлює лише токен, не чіпає `notifSettings` (ротація токена — не переопт-ін).

**`RytmMessagingService`** (`push/RytmMessagingService.kt`, `FirebaseMessagingService`): приймає ті самі повідомлення, що `functions/index.js`'s `sendPush()` вже шле в PWA (жодної серверної зміни не знадобилось) — `{notification:{title,body}, webpush:{...}}` без `android`-специфічного блоку, тож у фоні система сама показує сповіщення дефолтною іконкою/каналом застосунку (meta-data в `AndroidManifest.xml`), а `onMessageReceived()` спрацьовує лише на передньому плані (те саме правило доставки Android для `notification`-повідомлень) — мірорить `js/notifications.js`'s `onMessage()`, який існує рівно для цього ж foreground-кейсу.

**Реальна іконка, не заглушка**: `res/drawable/ic_notification.xml` — той самий bell-гліф з `ICON_PATHS.bell` (`js/classic-globals.js`, Material Symbols, viewBox `0 -960 960 960`), перенесений у VectorDrawable через `<group android:translateY="960">` (viewport не може починатись з від'ємної координати) — суцільний білий силует, той самий принцип, що й `badge-96.png` на PWA-стороні (Android малює статус-бар-іконку лише за альфа-каналом/білим силуетом, кольоровий/непрозорий вигляд зламав би її, той самий клас гочі, що вже задокументований у CLAUDE.md для web push).

**UI**: нова секція "Сповіщення" в Settings (між "Безпека" і "Вигляд") з одним `Switch`-рядком (`SettingsToggleRow`, новий локальний composable поруч із уже наявним `SettingsRow`). `SettingsScreen` отримав власний `Scaffold`+`SnackbarHostState` (той самий патерн, що вже в `FinanceScreen`) для toast-еквівалентів "увімкнено"/"вимкнено"/помилка. Стан тумблера — `SettingsStore.isPushEnabled(uid)`/`setPushEnabled()`, той самий uid-prefixed-ключ-у-спільному-DataStore патерн, що й `PinStore` (друге локальне джерело правди лише для checked-стану UI; Firestore лишається джерелом правди для того, чи реально щось надсилається).

**Runtime-дозвіл (Android 13+)**: `POST_NOTIFICATIONS` запитується лише за потреби (`Build.VERSION.SDK_INT>=33`, перевірено `ContextCompat.checkSelfPermission`) через `rememberLauncherForActivityResult(RequestPermission())` — на відмову показує snackbar, не мовчить.

**Перевірено наскрізь на реальному емуляторі (Firestore-емулятор для даних, справжній FCM/Google Play Services на AVD — токен реальний, не заглушка)**:
1. **Дозвіл**: увімкнув тумблер → реально з'явився системний діалог "Allow Rytm to send you notifications?" → "Allow".
2. **Enable**: тумблер зафарбувався, snackbar "Push-сповіщення увімкнено" → `push_tokens/{uid}` реально отримав `{token:"fpoiyf-...", updatedAt}` (справжній FCM-токен) → `finance.notifSettings` реально отримав `{enabled:true, budgetAlerts:true, recurringAlerts:true, debtAlerts:true, time:"21:00", timeZone:"GMT"}` — точний збіг з очікуваним.
3. **Disable**: тумблер вимкнувся, snackbar "Push-сповіщення вимкнено" → `push_tokens/{uid}` документ реально видалений (порожній результат запиту) → `notifSettings`'s 4 прапори реально скинуті на `false` (час/timeZone лишились).
4. **Персистентність стану**: force-stop + релонч → тумблер реально показав той самий OFF-стан (DataStore пережив перезапуск процесу).

`./gradlew assembleDebug` (звичайна збірка) — успішно; окремо з `-PuseFirebaseEmulator=true` для наскрізного тесту вище.

**Свідомо не в цьому кроці**: гранулярні тумблери (нагадування з піротсінком часу окремо від бюджету/регулярних/розрахунків) — розкрита прогалина, описана вище. Реальна доставка фонового пуша (коли `onMessageReceived()` НЕ спрацьовує, а систему сама малює сповіщення) не перевірялась живим FCM-надсиланням із сервера — лише клієнтська реєстрація/UI-логіка; підтвердження живого end-to-end пуша (`notificationSweep` реально будить заснулий Android-пристрій) лишається на майбутнє, коли буде реальний привід (наприклад, реальний бюджет-експірад на тестовому акаунті).

## Крок 28 — Гранулярні Push-налаштування (закриває розкриту прогалину кроку 27) (зроблено, реально перевірено наскрізь)

Прямий продовжувач кроку 27, вибраний передусім тому, що продукт лишав відкритим питання, а не тому, що це "наступний пункт у списку" — крок 27 свідомо розкрив, що один тумблер вмикав усі 4 серверні прапори разом. Тепер, коли токен-реєстрація вже реальна й перевірена, розділити ці прапори на незалежні перемикачі — природне, обмежене за обсягом продовження, а не нова функціональність з нуля.

**`PushRepository` отримав**: `NotifSettings` data-клас (`{enabled,time,budgetAlerts,recurringAlerts,debtAlerts}`, точний збіг з `js/state.js`'s `AppState.notifSettings`), `getNotifSettings(uid)` (одноразове читання) і 4 точкові сеттери (`setDailyReminder`, `setBudgetAlerts`, `setRecurringAlerts`, `setDebtAlerts`) — кожен пише **лише свій** dotted-field-path (`notifSettings.budgetAlerts` тощо) через `update()`, а не весь `notifSettings`-мап через `set(merge())`, як роблять `enable()`/`disable()`. Це свідомий вибір, не випадковість: `update()` з dotted-key `Map<String,Any>` — офіційно задокументована Firestore-поведінка "оновити поле у вкладеному об'єкті" (однакова на Android/Web SDK); чи `set(merge())` трактує dotted-рядкові ключі так само — не частина того самого задокументованого контракту, тож для запису, де правильність критична, обрано перевірено задокументований шлях, а не непідтверджене припущення.

**Новий екран** — `NotificationSettingsSheet`/`NotificationSettingsViewModel` (`ui/screens/`, не `ui/screens/finance/` — це не фінансовий домен): жодної Room-таблиці немає (на відміну від кожного іншого manager sheet у застосунку) — `notifSettings` має сенс лише як спільне для акаунту Firestore-поле, яке сервер (`functions/lib/sweep.js`) читає напряму, тож застосовано одноразове завантаження + optimistic-local-write-through замість Room+Flow-конвеєра. Мірорить PWA-шні 4 незалежні чекбокси (`toggleReminders`/`toggleBudgetAlerts`/`toggleRecurringAlerts`/`toggleDebtAlerts`) + пару `<select>` година/хвилина (`populateNotifTimeSelects`/`updateNotifTimeFromSelects`, підтверджено читанням `js/notifications.js`).

**UI-рішення**: рядок "Типи сповіщень" у Settings з'являється **лише коли master Push-тумблер увімкнено** — налаштовувати, які саме сповіщення слати, безглуздо, поки пристрій узагалі не зареєстрований на прийом. Пікер часу нагадування показується лише коли саме "Щоденне нагадування" увімкнено (той самий conditional-visibility патерн, що й PWA-шний `<select>`-блок).

**Реальна гоча Kotlin, спіймана компілятором одразу**: власні публічні методи `setBudgetAlerts()`/`setRecurringAlerts()`/`setDebtAlerts()`/`setDailyReminderEnabled()` спершу конфліктували з синтезованими Kotlin-компілятором сеттерами однойменних `private set`-властивостей (`var budgetAlerts by mutableStateOf(...) private set` генерує `setBudgetAlerts(Boolean)` з тим самим JVM-сигнатурою) — "Platform declaration clash", реальна помилка компіляції, не здогадка. Виправлено перейменуванням на `onBudgetAlertsChanged`/`onRecurringAlertsChanged`/`onDebtAlertsChanged`/`onDailyReminderChanged`.

**Перевірено наскрізь на реальному емуляторі**:
1. **Завантаження стану**: після enable() кроку 27 (усі 4 прапори true, час 21:00) відкрив "Типи сповіщень" → шторка реально показала точно ці значення (не дефолти) — підтверджує `getNotifSettings()` читає реальний Firestore-документ, не заглушку.
2. **Точковий запис (незалежність тумблерів)**: вимкнув лише "Перевищення бюджету" → інші 3 (нагадування/регулярні/розрахунки) реально лишились увімкненими в UI → `finance.notifSettings` реально показав **лише** `budgetAlerts:false` зміненим, усі інші поля (`enabled`, `recurringAlerts`, `debtAlerts`, `time`, `timeZone`) незайманими — підтверджує, що `update()` з dotted-path дійсно не чіпає сусідні поля.
3. **Пікер часу**: змінив годину нагадування через дропдаун → `finance.notifSettings.time` реально записався як `"04:00"` — точковий запис підтверджено вдруге, на іншому полі.

`./gradlew assembleDebug` (звичайна збірка) — успішно; окремо з `-PuseFirebaseEmulator=true` для наскрізного тесту вище.

**Свідомо не в цьому кроці**: реальна доставка фонового пуша (`notificationSweep` реально будить заснулий Android-пристрій) — та сама прогалина, розкрита в кроці 27, досі не перевірена живим сервер-надсиланням, лишається на майбутнє з реальним приводом.

## Крок 29 — Іконки категорій (categoryIcons) на 3 поверхнях (зроблено, реально перевірено наскрізь)

Вибраний свідомо як контрольований, невеликий крок після двох підряд Push-кроків — на відміну від профілів (перемикання/спільний доступ), які торкнулися б майже кожного sync-репозиторію одразу (кожен `userDoc`-еквівалент потребує `@profileId`-суфіксації, локальні Room-таблиці треба скидати й перезавантажувати на switch) і є справжнім багатосесійним епіком, а не "кроком" — categoryIcons контрольовано за обсягом і реально видимий: транзакційний список (найбільш переглядуваний екран застосунку) досі показував літерний аватар замість іконки категорії.

**Ключове архітектурне рішення, ухвалене свідомо, не помилково**: замість портування власного SVG-набору PWA (`ICON_PATHS`, ~50+ гліфів) використано вже наявну залежність `material-icons-extended` (стоїть у `build.gradle.kts` невикористаною) — категорійний бейдж декоративний, не системний chrome-елемент з обов'язковою піксельною відповідністю (на відміну від `ic_notification.xml` кроку 27, де потрібен був точний бейдж-силует для Android-статус-бару) — тож найближчий стоковий Material-гліф є законним рішенням з нижчою вартістю, а не зрізаним кутом.

**`CategoryColor.kt` отримав** (файл уже містив `categoryColor()`, тепер логічно поповнений парним `categoryIcon()`): `CAT_ICON` (точна мапа назв, підтверджена читанням `js/core.js`'s `CAT_ICON` — кожен PWA-гліф вручну зіставлений з найближчим Material-еквівалентом: briefcase→Work, handCoin→Payments, box→Bento тощо), `CAT_ICON_KEYWORDS` (той самий порядок і ті самі регулярні вирази, що й `CAT_ICON_KEYWORDS` у PWA), `CAT_ICON_FALLBACK_POOL` (детермінований hash-фолбек, той самий пул розміру що й PWA, мінус 'umbrella' — тим самим доводом, що й оригінал: без тематичного зв'язку як сліпе вгадування). `categoryIcon(name)` резолвить у тому ж порядку: точна назва → keyword → hash. **Свідомо не в цьому кроці**: ручний вибір іконки користувачем (`AppState.categoryIcons[name]`-оверрайд, `openCategoryIconPicker()` у PWA) — Android поки не має жодного збереженого поля для цього, тож резолюція завжди автоматична.

**`CategoryIconBadge(category, size)`** — новий спільний composable (кольорове коло 18%-альфи + material-іконка кольору категорії, той самий "легкий tint" рецепт, що й PWA-шний `icon-badge`), підключений на 3 поверхнях: `TransactionRow` (замінив літерний аватар — `Text(tx.category.take(1).uppercase())` видалено), `CategoriesManagerSheet`'s рядок категорії, `BudgetsManagerSheet`'s рядок бюджету. **Свідомо не підключено** до `RecurringManagerSheet`'s згорнутого рядка — той рядок уже щільний (тип/сума/частота/дата в одному summary), і додавання ще одного елементу — окреме UI-рішення, не автоматичне розширення цього кроку.

**Реальний інцидент під час перевірки, не пов'язаний з кодом цього кроку**: перша спроба верифікації на реальному пристрої (без `-PuseFirebaseEmulator=true`, проти продакшн-Firestore) завершилась крашем `FirebaseFirestoreException: PERMISSION_DENIED` у необробленому Firestore snapshot-лістенері — але **іконки встигли коректно відрендеритись до краху** (видно на скріншоті: кошик/авто/портфель), підтверджуючи, що UI-шар не мав жодного стосунку до падіння. Не списано на "ну працює ж" — перевірено чистіше через Firebase-емулятор (та сама методологія, що й у кожному попередньому кроці), де застосунок відпрацював без жодного крашу. `PERMISSION_DENIED` на продакшні лишається окремим, не досліджуваним у цьому кроці питанням (ймовірно застарілі/несумісні дані з давніх тестових сесій на продакшн-акаунті, а не regresssion цього чи попередніх кроків).

**Перевірено наскрізь на реальному емуляторі (через Firebase-емулятор, без production-краху)**:
1. **Список транзакцій**: "Продукти" → кошик (точний збіг CAT_ICON), "Транспорт" → авто (точний збіг), "Зарплата" → портфель (точний збіг) — усі кольорові бейджі замінили літерні аватари.
2. **Менеджер категорій**: усі 7 seed-категорій показали коректні іконки, включно з "Здоров'я" (точний збіг, серце-пульс) і "Комунальні" (жодного точного/keyword-збігу в жодній системі — і PWA, і Android коректно впали на hash-фолбек, той самий гліф-клас "подарунок").
3. **Менеджер бюджетів**: ті самі іконки на тих самих категоріях — підтверджує, що `CategoryIconBadge` дає ідентичний результат на 3 різних викликах (детермінована функція, не випадковий стан).

`./gradlew assembleDebug` (звичайна збірка) — успішно.

**Свідомо не в цьому кроці**: ручний пікер іконок (описано вище), підключення бейджа до `RecurringManagerSheet`.

## Крок 30 — Профілі: власні профілі акаунту (додати/перейменувати/видалити/перемкнути) (зроблено, реально перевірено наскрізь, з реальним багом спіймано й виправлено під час верифікації)

Перший крок реального епіку "профілі", розпочатий зі свідомого планування обсягу, а не одразу з коду (так, як розкрито наприкінці кроку 29) — перш ніж писати щось, перечитано `js/color-picker.js`'s `switchProfile()`/`addProfile()`/`renameProfile()`/`deleteProfile()`, `js/firebase-sync.js`'s `userDoc()`, і структуру `profiles_meta`, щоб зрозуміти реальний обсяг: **кожен з 10 sync-репозиторіїв Android (`FinanceSyncRepository`, `ShiftsSyncRepository`, `CategoriesSyncRepository`, `DebtSyncRepository`, `RecurringSyncRepository`, `ShoppingSyncRepository`, `TagsSyncRepository`, `TransactionsSyncRepository`, `BudgetsSyncRepository`, `PushRepository`) жорстко зашивав `.document("finance")`/`.document("shifts")`/`.document("debt")`** — без `@profileId`-суфіксації жоден із них не міг обслуговувати другий профіль. Свідомо обраний обсяг для цього кроку: **лише власні (не спільні) профілі акаунту** — join/leave/ролі спільних профілів (окрема, значно більша підсистема з інвайт-кодами) залишається розкритою, не портованою прогалиною, задокументованою в `ProfilesRepository`'s власному doc-коментарі.

**`ProfileDocNames.kt`** (нова спільна утиліта) — `profileDocName(base,profileId)`, точний відповідник `js/firebase-sync.js`'s `userDoc()`: дефолтний профіль лишає базову назву без суфікса, будь-який інший отримує `@<profileId>`. Усі 10 sync-репозиторіїв отримали `profileId: String = DEFAULT_PROFILE_ID` параметр у кожній публічній `suspend fun` і кожному приватному `xxxDocRef()`-білдері — механічна, але реально ризикована зміна (10 файлів, однаковий патерн, легко пропустити один); перевірено повним грепом `.document("finance")`/`.document("shifts")`/`.document("debt")` до і після, щоб підтвердити відсутність пропущених місць.

**`RytmDatabase.clearAllProfileScopedTables()`** — нова extension-функція, що очищає всі 12 локальних таблиць одним викликом (Room не має per-profile тегування рядків, на відміну від Firestore-суфіксації документів — перемикання профілю означає почати локальний кеш заново). **`ProfileSyncCoordinator`** — новий клас, що централізує послідовність "синкнути всі 10 доменів для одного профілю" (раніше інлайнено прямо в `MainActivity`), з двома входами: `loadOnSignIn(uid)` (звичайний вхід — сідить demo-дані за потреби, читає збережений `activeProfileId`, синкає) і `switchProfile(uid, newProfileId)` (очищає Room, зберігає новий `activeProfileId`, синкає — **свідомо БЕЗ сідування demo-даних**).

**Реальна, свідомо спіймана до реалізації пастка з сідуванням demo-даних**: якби `switchProfile()` викликав ті самі `seedIfEmpty()`, що й перший вхід, для генуїнно нового (порожнього) другого профілю це запустило б PWA-шний "немає remote-документа → запушити локальне як сід" гілку sync-логіки — і засідні demo-транзакції ("АТБ Продукти −320₴" тощо) реально пішли б у Firestore як "справжні" дані нового профілю. Це не гіпотетична турбота — саме так і сталося б, якби я не подумав про це заздалегідь. `switchProfile()` тому свідомо ніколи не сідує.

**`ActiveProfileStore`** (DataStore, той самий uid-prefixed-ключ-патерн, що й `PinStore`) — суто локальний, не синкається (той самий "per-device choice" принцип, що й PWA-шний `localStorage['mx_activeProfile_'+uid]`, підтверджено читанням). **`ProfilesRepository`** — CRUD над `profiles_meta`-документом (акаунт-рівня, ніколи не суфіксується).

**Реальний, спійманий і виправлений під час верифікації баг**: перша спроба перемкнути на новосворений профіль **мовчки провалилась** — жоден `finance@<id>`/`shifts@<id>`/`debt@<id>` документ не з'явився в Firestore, хоча UI показав "Профіль перемкнено" (фальшивий успіх!). Корінна причина, знайдена не здогадкою, а прямим читанням `firestore.rules`: `document.matches('^(shifts|finance|debt)(@[A-Za-z0-9_]+)?$')` — регекс дозволяє в суфіксі лише `[A-Za-z0-9_]`, **без дефіса**. `ProfilesRepository.addProfile()` генерував id через `UUID.randomUUID().toString()`, який **містить дефіси** (`5b645ee9-c9ac-...`) — кожен запис у документ нового профілю мовчки відхилявся правилами `PERMISSION_DENIED`, спійманий і проковтнутий власним `try/catch` у `ProfilesManagerViewModel.confirmSwitch()`. Виправлено на 2 рівнях: (1) `newProfileId()` тепер генерує UUID **без дефісів** (`.replace("-","")`) — той самий рівень унікальності, сумісний з regex; (2) **сам факт, що помилка мовчки проковтнулась і UI все одно показав хибний успіх — окрема, реальна вада**, виправлена окремо: `confirmSwitch()` тепер повертає `Boolean`, і виклик `onSwitched()` (який закриває шторку й показує toast) відбувається лише при `true`. PWA-шний `uid('profile')` (`prefix+'_'+Date.now().toString(36)+...`) ніколи не мав цієї проблеми — генерує лише alfanumeric+underscore, тому цей клас багу існував лише на Android-стороні.

**Перевірено наскрізь на реальному емуляторі, повний цикл, включно з повторною перевіркою після виправлення**:
1. **Створення профілю**: Settings → Профілі → додав "Druzhyna" → реально з'явився в списку з кнопкою "Перемкнути" → `profiles_meta.list` реально отримав другий запис з новим (без дефісів) id.
2. **Перемикання на новий профіль**: підтвердив у діалозі → snackbar "Профіль перемкнено" → **Фінанси-екран реально показав генуїнно порожній стан** ("Операцій ще немає", "0 записів", жодного гаманця) — підтверджує, що demo-дані НЕ засіялись (ключове архітектурне рішення вище справді працює, не лише в теорії).
3. **Firestore-перевірка після виправлення**: `finance@ab858307...`/`shifts@ab858307...`/`debt@ab858307...` документи реально створились з порожніми масивами/мапами — точний, дефіс-вільний id, що проходить `firestore.rules`.
4. **Локальне створення гаманця на новому профілі**: реально створився в Room (видно в Settings → Гаманці) — **чесно НЕ перевірявся до пушу в Firestore**, бо в застосунку й досі немає continuous-sync (те саме, вже багаторазово розкрите обмеження з кроку 19+) — локальна зміна між cold-sync'ами загубилась би при наступному перемиканні профілю, той самий існуючий клас поведінки, що вже стосується й простого перезапуску застосунку на тому самому профілі, не нова прогалина цього кроку.
5. **Перемикання назад на "Я"**: підтвердив у діалозі → **Фінанси-екран реально показав усі оригінальні 7 записів і 3 гаманці неушкодженими** — підтверджує, що `clearAllProfileScopedTables()`+повторний cold-sync коректно відновлює попередній профіль без втрати/пошкодження даних.

`./gradlew assembleDebug` (звичайна збірка) — успішно; окремо з `-PuseFirebaseEmulator=true` для наскрізного тесту вище.

**Свідомо не в цьому кроці**: спільні профілі (invite/join/leave/ролі editor/viewer) — окрема, значно більша підсистема; видалення акаунту; Push-налаштування (`NotificationSettingsSheet`) тепер теж profile-aware (отримує `activeProfileId`), але це не перевірялось окремо на другому профілі в цьому раунді верифікації — логічно коректно за тим самим патерном, що й решта, але не підтверджено вручну.

## Крок 31 — Ручний пікер іконок категорій (categoryIcons) (зроблено, реально перевірено наскрізь)

Обраний самостійно як контрольований, невеликий крок після великого профільного епіку кроку 30 — закриває прогалину, розкриту в кроці 29 ("ручний вибір іконки користувачем... Android поки не має жодного збереженого поля для цього"). На відміну від профілів чи спільного доступу, це один зрозумілий Firestore-зріз (`categoryIcons`) з уже готовою інфраструктурою резолюції іконок з кроку 29 — низький ризик, контрольований обсяг.

**Нова Room-таблиця `CategoryIconEntity(categoryName: String PK, iconName: String)`** (БД version 10→11) — 1:1 з `AppState.categoryIcons` (`Record<name, iconName>`, підтверджено читанням `js/settings-managers.js`'s `selectCategoryIcon()`), той самий "keyed by name only, no type" підхід, що вже усталений для budgets. **Sync**: `CategoriesSyncRepository.syncCategoryIconsOnSignIn()` — третій зріз `finance`-документа поруч із categories/subcategories, той самий cold-sync патерн, підключений у `ProfileSyncCoordinator`. **Каскади**: `FinanceRepository.renameCategory()`/`deleteCategory()` розширені й на `categoryIconDao()` — підтверджено читанням PWA-шних версій цих функцій (`renameCategory()` переносить `categoryIcons[oldName]`, `deleteCategory()` видаляє — **але свідомо НЕ каскадить у recurring**, той самий розподіл, що вже задокументований для `RecurringEntities.kt`).

**`PICKER_ICONS`** (новий, у `CategoryColor.kt`) — усі 41 назва з `js/classic-globals.js`'s `window.ICON_NAMES` (точний список, підтверджено читанням масиву, не здогадкою), кожна вручну зіставлена з найближчим Material-еквівалентом (той самий принцип "декоративний бейдж, не системний chrome", що й у кроці 29). **Ключова архітектурна деталь для крос-платформної цілісності**: збережене значення — це PWA-шна назва іконки-рядка (`"house"`, `"cart"` тощо), а не Android-власний ідентифікатор — тож іконка, обрана на Android, залишається змістовною для PWA (і навпаки, якщо назва входить у той самий `PICKER_ICONS`-набір). `categoryIcon(name, iconOverride)` тепер перевіряє оверрайд **першим**, до точної мапи/keyword/hash-фолбека — точний порядок резолюції PWA-шного `categoryIcon()`. Невідома/PWA-only назва (наприклад `heartPulse`, якої немає в `window.ICON_NAMES`, лише в `CAT_ICON`) коректно падає назад в автоматичну резолюцію, а не крашить.

**UI**: `CategoryIconBadge` тепер клікабельний у `CategoriesManagerSheet` (тап відкриває нову `CategoryIconPickerSheet` — сітка 6×7 з усіх 41 іконки, той самий "стек незалежних шторок" патерн, що вже усталений у `SettingsScreen`). `FinanceViewModel`/`BudgetsManagerViewModel` отримали `categoryIcons`-потік, протягнутий у `TransactionRow`/`BudgetRow` як `iconOverride` — той самий бейдж на 3 поверхнях кроку 29 тепер показує ручний вибір, якщо він є.

**Реальна методологічна пастка під час верифікації (двічі поспіль тепер), спіймана й виправлена, не проігнорована**: перший тест "тапнув на іконку будинку" за орієнтовними піксельними координатами зі скріншота — бейдж не змінився. Замість списати на "ну не спрацювало", перевірив `uiautomator dump` **під час відкритого пікера** й побачив, що реальні bounds іконки "house" (`[892,2242][1076,2386]`) кардинально відрізнялись від моєї візуальної оцінки (`[658,1546]`) — тап просто влучив в іншу іконку. Виправлено прицільним тапом за реальними bounds — спрацювало одразу. **Друга пастка, та сама, що й у кроках 22-24/30**: перший тест persistance через force-stop+relaunch показав, що іконка **відкотилась назад** — `finance.categoryIcons` уже існував порожнім з першого sign-in цієї тестової сесії, тож cold-sync узяв "remote wins" і стер щойно встановлений локальний override. Виправлено тим самим уже усталеним трюком (видалення поля через Firestore REST PATCH перед повторним тестом), після чого persistence підтвердився чесно.

**Перевірено наскрізь на реальному емуляторі, повний цикл**:
1. **Пікер**: тап на бейдж "Комунальні" → реально відкрилась сітка 41 іконки → обрав "house" (за точними bounds з dump, не оцінкою) → бейдж реально змінився з "подарунок" (hash-фолбек) на синій "будинок".
2. **Push у Firestore** (після усунення one-time-sync пастки): `finance.categoryIcons` реально записався як `{"Комунальні": "house"}`.
3. **Persistence через force-stop+relaunch**: бейдж реально лишився "будинком" після повного перезапуску й повторного cold-sync — підтверджує, що це не був разовий локальний стан, а реальний round-trip через Firestore.

`./gradlew assembleDebug` (звичайна збірка) — успішно; окремо з `-PuseFirebaseEmulator=true` для наскрізного тесту вище.

**Свідомо не в цьому кроці**: пікер не показує "виділену" поточну іконку (немає checkmark/рамки на вже вибраній) — суто візуальний штрих, не функціональна прогалина; підключення до `RecurringManagerSheet` (той самий, ще не закритий пункт із кроку 29).

## Крок 32 — Спільні профілі: invite/join/leave (v1, без гранулярних ролей) (зроблено, з чесно нерозкритим до кінця наскрізним тестом — див. нижче)

Власник акаунта обрав цей із двох великих лишків, розкритих у кроці 31. Обсяг звужено свідомо до v1: invite-код (генерація/поширення), redeem за кодом, leave — **без UI для editor/viewer ролей** (`firestore.rules` уже дефолтить відсутню роль на `'editor'`, той самий прецедент, що й у PWA — вона теж спочатку відвантажила invite/join/leave, а granular permissions додала окремим, пізнішим кроком). Це не прогалина безпеки: кожен, хто приєднався через код, отримує повний editor-доступ, так само як кожен PWA-учасник до появи ролей.

**Дані**: `ProfileMeta` (`ProfilesRepository.kt`) отримав `kind`/`ownerUid` — `list(uid)` тепер повертає і власні, і `kind:"shared"`-записи без фільтра (крок 30 фільтрував спільні геть, оскільки join ще не існував). `ProfileMeta.dataOwnerUid(signedInUid)` резолвить, чиє `uid`-дерево реально містить дані профілю — сигнатура один-в-один з `js/firebase-sync.js`'s `userDoc()`'s `activeProfileOwnerUid || currentUser.uid`.

**`ActiveProfileStore`**: збережене значення тепер кодується так само, як PWA-шний `localStorage['mx_activeProfile_'+uid]` — або голий `profileId` (власний профіль), або `"ownerUid|profileId"` (приєднаний спільний). `getActiveProfileOwnerUid(uid)` парсить префікс; `setActiveProfile(uid, profileId, ownerUid?)` пише в тому ж форматі. Це те, що переживає рестарт застосунку без повторного пошуку в `profiles_meta`.

**`ProfileSyncCoordinator`**: `loadOnSignIn()`/`switchProfile()` тепер резолвлять `dataOwnerUid` (з `ActiveProfileStore` або з обраного `ProfileMeta.ownerUid`) і передають його — не завжди `signed-in uid` — у `syncAllDomains()`, звідки він і будує кожен Firestore-шлях (`users/{dataOwnerUid}/max_tracker/...`). Це єдина структурна зміна, потрібна всім 11 sync-доменам — жоден із них сам по собі не змінився, кожен і так просто приймав `uid`-параметр для побудови шляху.

**`ProfilesRepository`**: `shareProfile(ownerUid, profileId, profileName)` (ідемпотентний — повторний виклик видає новий код), `redeemInvite(uid, rawCode)` (повертає `RedeemInviteResult.Ok/Failed(reason)`, не кидає), `leaveSharedProfile(uid, ownerUid, profileId)` — точний Kotlin-переклад `js/firebase-sync.js`'s `shareCurrentProfile()`/`redeemSharedInvite()`/`leaveSharedProfile()`, той самий алфавіт коду (без 0/O/1/I), той самий 24-годинний TTL, той самий двоетапний redeem (invite `usedBy` спершу, потім `shared_members`-приєднання) — обидва кроки саме так, як цього вимагає вже задеплоєний `firestore.rules` (жодних змін у ньому не знадобилось — секція "SHARED PROFILES helpers" уже покриває весь v1-потік, підтверджено читанням файлу перед стартом).

**UI** (`ProfilesManagerSheet`/`ProfilesManagerViewModel`): кожен власний профіль-рядок отримав кнопку "Поділитися" (діалог з кодом), кожен спільний профіль-рядок — бейдж "Спільний" + кнопку "Вийти" замість перейменувати/видалити; нове поле "Приєднатися за кодом" внизу шторки. Помилки redeem мапляться на ті самі рядки, що й `js/color-picker.js`'s UI (`own-profile`/`used`/`expired`/`failed`/невідомий код).

**Реальний баг, спійманий і виправлений під час верифікації**: `ProfilesManagerViewModel.reload()` (уже в коді з кроку 30, не введений цим кроком) не мала `try/catch` навколо `.get().await()` — на цій сесії реальний транзиентний мережевий блип під час відкриття шторки "Профілі" призвів до необробленого `FirebaseFirestoreException("client is offline")`, який зронив увесь застосунок (`FATAL EXCEPTION: main`, підтверджено logcat-стеком). Виправлено `try/catch` з graceful `errorMessage` замість краху — перевірено: після фіксу той самий сценарій дає червоний банер "Не вдалося завантажити профілі", застосунок не падає.

**Чесно НЕ перевірено наскрізь цього разу**: після фіксу краху `.get()` на `profiles_meta` продовжував стабільно (не одноразово — повторив тричі зі свіжим `ViewModel`) провалюватись цією ж помилкою офлайн-клієнта, попри те, що `adb shell ping 8.8.8.8` з цього AVD проходив (хоч і з підвищеною latency ~330ц мс) і `logcat` показував не мережеву, а справжню `GoogleApiManager`/`Phenotype` `SecurityException`/`DEVELOPER_ERROR` — ознаки зламаного стану Google Play Services саме на цьому AVD-інстансі, не логічну помилку в новому коді (той самий шлях `.get().await()` без catch раніше падав так само й у коді кроку 30, отже це не регресія кроку 32). Годинник пристрою синхронізований і коректний (виключив clock-skew як причину). Через це **реальний invite→redeem→leave round-trip між двома акаунтами цього разу не підтверджено наскрізно** — лише: компіляція чиста (`assembleDebug`), новий UI рендериться коректно на реальному пристрої (бейджі/кнопки/поле коду видно, без крашу layout), і сам факт краху виправлено й перевірено окремо. Це відхилення від звичайної практики "кожен крок закривається реальним наскрізним прогоном" — власник акаунта попросив зменшити інтенсивність верифікації (менше повторних AVD-циклів), тож не зроблено `-wipe-data`-перезапуску AVD, який імовірно вирішив би GMS-проблему, як рекомендує правило 7. **Наступна сесія, що торкається спільних профілів, мала б повторити цей наскрізний тест першим ділом** (можливо, з `-wipe-data` перезапуском AVD, якщо GMS-стан знову зламаний) перед тим, як розширювати цю фічу далі.

**Свідомо не в цьому кроці**: гранулярні editor/viewer ролі (UI для "Учасники"-менеджера власника — `listSharedMembers()`/`setMemberRole()` з PWA не портовані); push-сповіщення для спільних профілів (PWA-шний `dataOwnerFor()` у `functions/lib/sweep.js` уже підтримує це на бекенді незалежно від Android-клієнта); показ списку учасників власнику.

## Наступні кроки

Далі: гранулярні editor/viewer ролі для спільних профілів (розширення кроку 32) — чи мова (i18n, блокована міграцією на string resources)? Перш ніж рухатись далі в спільних профілях, варто повторити наскрізний invite/redeem тест кроку 32 (не підтверджений цього разу через середовищну проблему з Google Play Services на AVD, не через логіку коду — див. крок 32 вище).
