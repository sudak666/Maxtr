// sw.js — Rytm PWA Service Worker v4.0
// Handles BOTH plain asset caching AND Firebase Cloud Messaging background
// push. These used to be two separate service worker files (sw.js +
// firebase-messaging-sw.js) both registered at the site's root scope —
// since a single scope can only ever have one active worker, registering
// firebase-messaging-sw.js after sw.js silently replaced sw.js at that
// scope (it has no 'fetch' handler), which disabled asset caching/offline
// support the moment a user enabled push notifications. Merged into one
// file so there is exactly one registration, one scope, no conflict.
//
// The importScripts() calls below fetch Firebase's compat SDK from gstatic
// at SW-script-evaluation time — if that fetch fails (offline first
// install, gstatic unreachable, ad blocker, sandboxed test), an uncaught
// error there would fail the *entire* script evaluation, taking the
// install/fetch handlers below down with it and losing asset caching too,
// not just push. Wrapped in try/catch so a Firebase/network hiccup only
// costs background push, never the core offline-caching behavior.
let messaging = null;
try {
  importScripts('https://www.gstatic.com/firebasejs/12.11.0/firebase-app-compat.js');
  importScripts('https://www.gstatic.com/firebasejs/12.11.0/firebase-messaging-compat.js');

  firebase.initializeApp({
    apiKey: "AIzaSyBjtcCiXKKZ9TH3Ubrn65IX59kyCe9C-H4",
    authDomain: "maxtr-c238f.web.app",
    projectId: "maxtr-c238f",
    storageBucket: "maxtr-c238f.firebasestorage.app",
    messagingSenderId: "311094677098",
    appId: "1:311094677098:web:7a3797c99fad2874340413"
  });

  messaging = firebase.messaging();
  messaging.onBackgroundMessage((payload) => {
    const { title, body, icon } = payload.notification || {};
    // icon is the per-notification-type themed PNG (see functions/index.js's
    // NOTIF_ICONS) sent via webpush.notification.icon — falls back to the
    // generic app icon for anything that didn't set one (e.g. a manually
    // sent test push with no icon field).
    self.registration.showNotification(title || 'Rytm', {
      body: body || '',
      icon: icon || 'icon-192.png',
      badge: 'icon-192.png',
    });
  });
} catch (err) {
  console.warn('sw.js: Firebase Messaging setup failed, push notifications unavailable this session', err);
}

const CACHE_NAME = 'rytm-v64';
const STATIC_ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './icon-192-rounded.png',
  './icon-512-rounded.png',
  // Themed per-notification-type push icons (functions/index.js's
  // NOTIF_ICONS / sw.js's onBackgroundMessage handler above) — same-origin,
  // so precached here alongside every other same-origin asset for the same
  // cold-start-offline reasoning as everything else in this list.
  './notif-icon-daily.png',
  './notif-icon-budget.png',
  './notif-icon-recurring.png',
  './notif-icon-debt.png',
  // Same reasoning as FIREBASE_SDK_ASSETS below: index.html loads app
  // logic via <script type="module" src="./js/app.js">, which statically
  // imports these same-origin files — a cold start with no network (first
  // install offline, or a cache that hasn't picked these up yet via the
  // fetch handler's opportunistic caching) would otherwise fail the whole
  // module graph. Keep this list in sync with js/'s actual file list.
  './js/app.js',
  './js/state.js',
  './js/core.js',
  './js/firebase-sync.js',
  './js/color-picker.js',
  './js/auth.js',
  './js/app-init.js',
  './js/ui-widgets.js',
  './js/calendar.js',
  './js/settings-managers.js',
  './js/goals-profile.js',
  './js/notifications.js',
  './js/finance.js',
  './js/tx-validation.js',
  './js/analytics-csv.js',
  './js/debt.js',
  './js/shopping.js',
  './js/privacy-cache.js',
  // Classic (non-module) scripts index.html now loads via <script src=""> —
  // externalized from inline <script> blocks as part of the CSP hardening
  // pass (see CLAUDE.md) so script-src doesn't need 'unsafe-inline'/hashes.
  './js/theme-preinit.js',
  './js/touch-active-fix.js',
  './js/sw-register.js',
  './js/classic-globals.js',
];

// index.html's <script type="module"> statically imports these four SDK
// files directly from gstatic — a static ES module import is all-or-nothing,
// so on a cold start with no network (offline first launch, or a cache that
// never got these yet) the whole module fails to evaluate and the entire
// app (not just Firebase-dependent features) goes dead, even though the
// index.html shell itself rendered fine from cache. Must match the exact
// URLs/version index.html imports — bump both together if the SDK version
// ever changes (see SETUP.md's note on the pinned 12.11.0 version).
const FIREBASE_SDK_ASSETS = [
  'https://www.gstatic.com/firebasejs/12.11.0/firebase-app.js',
  'https://www.gstatic.com/firebasejs/12.11.0/firebase-app-check.js',
  'https://www.gstatic.com/firebasejs/12.11.0/firebase-firestore.js',
  'https://www.gstatic.com/firebasejs/12.11.0/firebase-auth.js',
  'https://www.gstatic.com/firebasejs/12.11.0/firebase-messaging.js',
];

// Встановлення — кешуємо статику
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => Promise.all([
      cache.addAll(STATIC_ASSETS),
      // Best-effort: a fresh install with no network yet (or gstatic
      // blocked) shouldn't fail the whole install and lose basic asset
      // caching just because the SDK precache didn't work this time — it'll
      // get cached opportunistically by the fetch handler below on the next
      // successful online load instead.
      cache.addAll(FIREBASE_SDK_ASSETS).catch(err => console.warn('sw.js: Firebase SDK precache failed, will retry on next online fetch', err)),
    ]))
  );
  self.skipWaiting();
});

// Активація — видаляємо старі кеші
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Клік по нагадуванню — фокус/відкриття застосунку
self.addEventListener('notificationclick', event => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window' }).then(list => {
      for (const c of list) if ('focus' in c) return c.focus();
      return self.clients.openWindow('./');
    })
  );
});

// Fetch — Network First для HTML, Cache First для решти
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);

  // The four Firebase SDK module scripts specifically (see
  // FIREBASE_SDK_ASSETS above) get cache-first treatment despite being
  // cross-origin — this is a narrow, exact-URL allowlist, not a general
  // cross-origin fetch override, so it doesn't reintroduce the reCAPTCHA
  // no-cors/redirect bug the blanket bail-out below was added to fix.
  if (FIREBASE_SDK_ASSETS.includes(event.request.url)) {
    event.respondWith(
      caches.match(event.request).then(cached => cached || fetch(event.request).then(response => {
        if (response.status === 200) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      }))
    );
    return;
  }

  // Сторонні (крос-origin) запити — Firebase, reCAPTCHA, шрифти тощо —
  // не чіпаємо взагалі й лишаємо браузеру, ніби сервіс-воркера немає.
  // Раніше тут був respondWith(fetch(...)) навіть для чужих запитів, і це
  // ламало деякі no-cors/redirect-запити (напр. внутрішній CSP-звіт
  // reCAPTCHA), викликаючи "TypeError: Failed to fetch" в консолі.
  if (url.origin !== self.location.origin) return;

  // Same-origin dynamic API endpoints (Hosting rewrites to a Cloud
  // Function, e.g. /api/privat-rates) must always hit the network —
  // caching them with the generic same-origin rule below would serve
  // stale exchange rates forever after the first successful fetch.
  if (url.pathname.startsWith('/api/')) return;

  // HTML + same-origin JS — Network First (always the freshest code), with
  // the SW cache as an offline-only fallback. JS was previously cache-first
  // like every other static asset, which meant a long-lived PWA/TWA could
  // keep serving a stale copy of the app's modules from the SW cache for a
  // whole cache-version lifetime even while online — so a shipped-and-
  // deployed fix looked "not applied" on the device (reported repeatedly by
  // the account owner). Network-first for .js kills that entire class of
  // "stale JS delivery" bug: whenever the device is online, every module is
  // fetched fresh from the network (which the no-cache header on /js/**
  // keeps un-stale), and the cache only ever serves JS when truly offline.
  // Icons/fonts/etc. stay cache-first below — they rarely change and don't
  // carry app logic.
  if (event.request.destination === 'document' || url.pathname.endsWith('.html') || url.pathname.endsWith('.js')) {
    event.respondWith(
      fetch(event.request)
        .then(response => {
          if (response && response.status === 200) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
          }
          return response;
        })
        .catch(() => caches.match(event.request))
    );
    return;
  }

  // Решта — Cache First з fallback на мережу
  event.respondWith(
    caches.match(event.request).then(cached => {
      if (cached) return cached;
      return fetch(event.request).then(response => {
        if (response.status === 200) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      });
    })
  );
});
