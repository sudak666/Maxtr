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
  // (see index.html's script-load order) -- only ever 'uk' or 'en' at
  // runtime (classic-globals.js's own setLang() and localStorage-restore
  // path both gate on that exact check before assigning), narrowed here
  // rather than left a plain string so js/state.js's LANG_CALENDAR[...]
  // index and js/monobank.js's ternary both type-check honestly.
  currentLang: 'uk' | 'en';
  // js/core.js's one hook back to the classic (non-module) inline script's
  // setLang() — see CLAUDE.md's "index.html script structure" for why this
  // exists as a real window.* property rather than an ES module export.
  // Narrowed to 'uk'|'en' for the same reason as currentLang above — the
  // only two values setLang() ever calls it with.
  __applyLangDynamic: (lang: 'uk' | 'en') => void;
}
