# Налаштування Firebase (обов'язково перед публікацією)

Код тепер використовує справжню автентифікацію (Firebase Auth) замість
захардкодженого PIN, і дані кожного користувача зберігаються окремо в
`users/{uid}/max_tracker/...`. Але це вимагає ручного налаштування в
Firebase Console — без цього реєстрація/вхід не запрацюють.

## 1. Увімкнути способи входу
Firebase Console → проєкт `maxtr-c238f` → **Authentication → Sign-in method**:
- Увімкнути **Email/Password**
- Увімкнути **Google** (вкажи publicly-visible name та support email)

## 2. Додати дозволені домени
**Authentication → Settings → Authorized domains** — додай домен(и), з яких
буде відкриватись застосунок (наприклад `твій-домен.web.app`, кастомний
домен, і `localhost` для тестів). Без цього `signInWithPopup` для Google
видаватиме помилку `auth/unauthorized-domain`.

**Важливо для цього репо:** `*.web.app`/`*.firebaseapp.com` додаються
автоматично, але **GitHub Pages-дзеркало (`sudak666.github.io`) — ні**.
Якщо вхід за номером телефону (або Google popup) не працює саме на
`https://sudak666.github.io/Maxtr/`, але працює на `https://maxtr-c238f.web.app`
— це воно: додай `sudak666.github.io` в Authorized domains вручну. Судячи з
того, що invisible reCAPTCHA на телефоні "блимає і зникає" одразу з
помилкою входу, це схоже саме на цей випадок — Firebase відхиляє домен
ще до того, як капча встигає щось показати.

## 3. Задеплоїти правила безпеки Firestore
У репозиторії є `firestore.rules` — вони обмежують доступ так, що
користувач бачить і редагує **тільки свої** дані:
```
firebase deploy --only firestore:rules
```
(потрібен `firebase-tools`, `firebase login`, `firebase use maxtr-c238f`).

**Це критично.** Поки правила не задеплоєні (або поки в консолі стоїть
"test mode" `allow read, write: if true`), дані будь-якого користувача
може прочитати/змінити хто завгодно — незалежно від того, що написано в
коді застосунку.

## 4. Стара спільна колекція
До реєстрації всі дані (зміни, фінанси, борги) лежали в одному документі
`max_tracker/*` без власника. Цей шлях уже перенесено в основний акаунт і
повністю закрито в правилах (`allow read, write: if false`) — окремих дій
тут більше не потрібно.

## 5. Вхід за номером телефону (опційно)
Firebase Console → **Authentication → Sign-in method** → увімкнути **Phone**.
Потрібен тариф **Blaze** (pay-as-you-go) — безкоштовний Spark не підтримує
відправку SMS взагалі, незалежно від коду застосунку.

**Якщо код SMS не надсилається взагалі, для будь-якого номера** —
консоль браузера показує `POST .../accounts:sendVerificationCode 503
(Service Unavailable)` і `FirebaseError: Firebase: Error
(auth/error-code:-39)` одразу на кожній спробі (не тільки на повторних
для одного номера) — це не квота одного номера, а ознака того, що Google
вимагає для цього проєкту **reCAPTCHA Enterprise**, а інтеграція не
налаштована (немає site key, прив'язаного до проєкту/доменів). Перевір:
**Authentication → Settings → reCAPTCHA Enterprise** — чи проєкт
позначено як такий, що вимагає Enterprise; якщо так, потрібно створити
ключ у **Google Cloud Console → Security → reCAPTCHA Enterprise**, додати
до нього домени застосунку (`maxtr-c238f.web.app`, `sudak666.github.io`,
`localhost` для тестів) і прив'язати цей ключ до проєкту в Firebase
Console. Це разова консольна дія — з боку коду `index.html` нічого
міняти не треба (`RecaptchaVerifier` сам підхоплює конфігурацію
Enterprise/v2 з проєкту).

## 6. Push-сповіщення (опційно, потребує коду з `functions/`)
Сповіщення (нагадування про облік, перевищення бюджету, наближення
повторюваного платежу) працюють локально й без цього кроку — але тільки
поки застосунок відкритий у браузері. Щоб вони доходили й коли застосунок
закрито, потрібен Firebase Cloud Messaging + одна Cloud Function:

1. **VAPID-ключ**: Firebase Console → **Project settings → Cloud Messaging →
   Web configuration → Web Push certificates → Generate key pair**. Встав
   отриманий ключ в `index.html` замість `PASTE_YOUR_VAPID_KEY_HERE`
   (пошук по файлу — `const VAPID_KEY`).
2. **Задеплоїти функцію та оновлені правила Firestore**:
   ```
   cd functions && npm install && cd ..
   firebase deploy --only functions,firestore:rules
   ```
   Потрібен тариф **Blaze** (Cloud Functions на безкоштовному Spark не
   працюють).
3. Готово — кнопка "Push-сповіщення" в Налаштуваннях → Сповіщення тепер
   реєструє токен пристрою, а функція `notificationSweep`
   (`functions/index.js`) щогодини перевіряє всі три умови для кожного
   користувача й надсилає push через FCM.
