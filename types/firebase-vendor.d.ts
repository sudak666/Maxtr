// Ambient module declarations mapping the exact pinned Firebase gstatic CDN
// URLs js/core.js (and sw.js) statically import to the real `firebase` npm
// package's own type declarations — installed as a type-only devDependency
// (never imported by bare specifier in real runtime source; the app always
// loads the actual SDK from the CDN URL, not from node_modules) purely so
// `tsc` has something to resolve these URL specifiers against. Unlike the
// vendored-Preact case (types/preact-vendor.d.ts), there is no local .js
// file competing for resolution here, so a plain re-export wildcard mapping
// is sufficient on its own. Keep the version pinned here in sync with the
// literal URL js/core.js/sw.js import — see SETUP.md's note on the pinned
// 12.11.0 SDK version.
declare module 'https://www.gstatic.com/firebasejs/12.11.0/firebase-app.js' {
  export * from 'firebase/app';
}
declare module 'https://www.gstatic.com/firebasejs/12.11.0/firebase-app-check.js' {
  export * from 'firebase/app-check';
}
declare module 'https://www.gstatic.com/firebasejs/12.11.0/firebase-firestore.js' {
  export * from 'firebase/firestore';
}
declare module 'https://www.gstatic.com/firebasejs/12.11.0/firebase-auth.js' {
  export * from 'firebase/auth';
}
declare module 'https://www.gstatic.com/firebasejs/12.11.0/firebase-messaging.js' {
  export * from 'firebase/messaging';
}
