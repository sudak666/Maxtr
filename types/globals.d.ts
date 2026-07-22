// Ambient declarations for the classic (non-module) globals set up by
// js/classic-globals.js and called bare (not imported) from js/*.js ES
// modules — see CLAUDE.md's "index.html script structure" section for why
// these are real window.* properties rather than real ES module exports.
// Any js/*.js file opting into `// @ts-check` that references one of these
// bare needs this file (picked up automatically via tsconfig.json's
// `include`, no explicit import needed for ambient .d.ts declarations) or
// tsc reports "Cannot find name".
declare function tr(key: string): string;
declare function Icon(name: string): string;
declare function setIcon(id: string, name: string): void;
declare function translateStaticDOM(): void;
declare function setLang(lang: string): void;
declare function setTheme(theme: string): void;

// Unlike the bare-callable functions above, this one is only ever accessed
// via an explicit `window.` prefix in js/*.js call sites, so it needs a
// Window interface augmentation rather than a bare `declare function`.
declare interface Window {
  ICON_NAMES: string[];
  // Set by js/classic-globals.js's init before the module graph evaluates
  // (see index.html's script-load order) — 'uk' or 'en', read here as a
  // plain string since js/state.js indexes LANG_CALENDAR with it directly.
  currentLang: string;
  // js/core.js's one hook back to the classic (non-module) inline script's
  // setLang() — see CLAUDE.md's "index.html script structure" for why this
  // exists as a real window.* property rather than an ES module export.
  __applyLangDynamic: (lang: string) => void;
  // js/app.js's test-only hook — see that file's own comment for why this
  // exists as one shared window.* object rather than per-test dynamic
  // import()s of individual js/*.js files.
  __RYTM_TEST_HOOKS__: {
    AppState: typeof import('../js/state.js').AppState;
    addTransaction: typeof import('../js/finance.js').addTransaction;
    scanReceiptImage: typeof import('../js/receipt-ocr.js').scanReceiptImage;
    setMonobankSyncGapMsForTesting: typeof import('../js/monobank.js').setMonobankSyncGapMsForTesting;
    maybeRefreshCryptoTop: typeof import('../js/dashboard-widgets.js').maybeRefreshCryptoTop;
  };
}
