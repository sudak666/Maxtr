// Service worker registration + reliable auto-update.
//
// Why this is more than a one-line register(): the app is a long-lived
// PWA/TWA that users keep open for a long time without ever navigating. The
// service worker already uses skipWaiting()+clients.claim() (see sw.js), so
// a freshly-deployed worker activates and takes control immediately — but
// the ALREADY-OPEN page keeps running whatever JS it loaded into memory at
// first paint until it actually reloads. Without the controllerchange
// reload below, a user on an open session never picks up a new deploy's JS
// (their bug reports kept describing already-fixed behavior because their
// tab was still executing the previous version's modules). This makes an
// open page reload itself exactly once when a new worker takes over, so
// deploys land without a manual hard-refresh.
if ('serviceWorker' in navigator) {
  // Only treat a controller change as "an update landed" when the page was
  // already controlled by a worker at load time — the very first visit's
  // initial clients.claim() also fires controllerchange, and reloading then
  // would be a pointless first-load flash (the JS is already fresh from the
  // network on a first, uncontrolled load). Guarded against reload loops.
  const hadController = !!navigator.serviceWorker.controller;
  let reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (reloading || !hadController) return;
    reloading = true;
    window.location.reload();
  });

  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').then((reg) => {
      // Proactively poll for a new sw.js on load and whenever the tab
      // regains focus — a PWA/TWA session that never navigates would
      // otherwise only auto-check roughly once a day, so a same-day deploy
      // could go unseen for hours. update() is a cheap conditional request
      // (a 304 when nothing changed) thanks to the no-cache header on sw.js.
      reg.update().catch(() => {});
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') reg.update().catch(() => {});
      });
    }).catch(() => {});
  });
}
