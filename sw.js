// sw.js — Zminka PWA Service Worker v4.0
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
    authDomain: "maxtr-c238f.firebaseapp.com",
    projectId: "maxtr-c238f",
    storageBucket: "maxtr-c238f.firebasestorage.app",
    messagingSenderId: "311094677098",
    appId: "1:311094677098:web:7a3797c99fad2874340413"
  });

  messaging = firebase.messaging();
  messaging.onBackgroundMessage((payload) => {
    const { title, body } = payload.notification || {};
    self.registration.showNotification(title || 'Zminka', {
      body: body || '',
      icon: 'icon-192.png',
      badge: 'icon-192.png',
    });
  });
} catch (err) {
  console.warn('sw.js: Firebase Messaging setup failed, push notifications unavailable this session', err);
}

const CACHE_NAME = 'zminka-v4';
const STATIC_ASSETS = [
  './',
  './index.html',
  './manifest.json',
];

// Встановлення — кешуємо статику
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(STATIC_ASSETS))
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

  // Сторонні (крос-origin) запити — Firebase, reCAPTCHA, шрифти тощо —
  // не чіпаємо взагалі й лишаємо браузеру, ніби сервіс-воркера немає.
  // Раніше тут був respondWith(fetch(...)) навіть для чужих запитів, і це
  // ламало деякі no-cors/redirect-запити (напр. внутрішній CSP-звіт
  // reCAPTCHA), викликаючи "TypeError: Failed to fetch" в консолі.
  if (url.origin !== self.location.origin) return;

  // HTML файли — Network First (завжди свіжий код)
  if (event.request.destination === 'document' || url.pathname.endsWith('.html')) {
    event.respondWith(
      fetch(event.request)
        .then(response => {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
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
