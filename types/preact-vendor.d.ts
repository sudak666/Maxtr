// Ambient module declaration mapping the vendored Preact ES module (loaded
// at runtime via a real relative import, e.g. './vendor/preact/preact.module.js'
// — see CLAUDE.md's "Preact adoption" section for why it's vendored rather
// than a bare npm import: GitHub Pages serves js/*.js unbundled, where a
// bare `import ... from 'preact'` specifier isn't a valid URL) onto the
// real `preact` package's own type declarations, installed as a
// devDependency purely for this — `preact` is never imported by specifier
// anywhere in the actual source, so it contributes nothing to the runtime
// bundle; this is a type-checking-only convenience so `h()`/`render()`/
// `Fragment` calls against the vendored file get real signatures instead of
// implicit `any`. Matches on any relative import path ending in this
// suffix, so it covers every js/*.js file's own './vendor/...' or
// '../vendor/...' specifier without needing one entry per depth.
declare module '*vendor/preact/preact.module.js' {
  export * from 'preact';
}
