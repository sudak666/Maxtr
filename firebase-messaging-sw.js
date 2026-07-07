// firebase-messaging-sw.js — handles push notifications while the app is
// closed/backgrounded. Separate from sw.js (which only does PWA asset
// caching) because Firebase Cloud Messaging needs its own service worker
// registered at the page's default scope; this file must stay at the site
// root (not under a subfolder) for that scope to cover the whole app.
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: "AIzaSyBjtcCiXKKZ9TH3Ubrn65IX59kyCe9C-H4",
  authDomain: "maxtr-c238f.firebaseapp.com",
  projectId: "maxtr-c238f",
  storageBucket: "maxtr-c238f.firebasestorage.app",
  messagingSenderId: "311094677098",
  appId: "1:311094677098:web:7a3797c99fad2874340413"
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  const { title, body } = payload.notification || {};
  self.registration.showNotification(title || 'Zminka', {
    body: body || '',
    icon: 'icon-192.png',
    badge: 'icon-192.png',
  });
});
