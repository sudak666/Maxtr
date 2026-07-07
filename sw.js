// sw.js — Zminka PWA Service Worker v3.0
const CACHE_NAME = 'zminka-v3';
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
