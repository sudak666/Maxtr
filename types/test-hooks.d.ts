// Split out of types/globals.d.ts on purpose (see CLAUDE.md's TypeScript
// adoption "strict-mode pass" note): this file's `typeof import('../js/X.js')`
// type references cause `checkJs:true` to transitively type-check every
// file reachable through them, even ones never named in a narrower
// tsconfig's own `include` list. Keeping this declaration in its own file
// means tsconfig.strict.json can include types/globals.d.ts (needed by
// small, already-strict-clean files like js/state.js) without accidentally
// pulling the entire js/*.js module graph into the strict check.
declare interface Window {
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
