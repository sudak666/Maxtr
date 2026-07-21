# Material Symbols (vendored reference, not shipped to the app)

A local copy of Google's **Material Symbols** icon set (Outlined style, regular weight 400), vendored purely as a reference library for drawing new `js/classic-globals.js` `ICON_PATHS` entries — **not loaded, imported, or shipped by the app itself**. `docs/` is already excluded from both the Vite `dist/` build (`scripts/build-site.mjs`'s `EXCLUDE_TOP`) and Firebase Hosting (`firebase.json`), so nothing here ever reaches a browser.

Why this exists: this app's icons are a hand-maintained set of small monochrome SVG path strings (see CLAUDE.md's "UI conventions" section — no icon fonts, no npm icon packages at runtime). Repeated attempts to hand-draw a clearer "repeat" icon (PRs #289/#291/#293) kept falling short at the real 17px/34px production size; the account owner pointed at Google Fonts' Material Symbols "Cycle" icon as a concrete example of one that reads clearly at that size, and its real path data turned out to work far better than anything hand-drawn. This directory makes it possible to reuse that same source for *any* future icon, offline, without re-fetching from Google Fonts each time (and without risking a hallucinated/approximated path from a web-fetch summarization pass — see PR that replaced the first `repeat` attempt with the verified path from this exact package).

## Source

Downloaded via `npm pack @material-symbols/svg-400` (Apache-2.0, published by the `marella/material-symbols` project, mirroring Google's own Material Symbols icon set — same npm registry path already used for this repo's other vendored dependencies, see CLAUDE.md's Tesseract/Preact vendoring notes). Only the `outlined/` style is kept here (matches the default style shown in Google Fonts' icon picker); `rounded/` and `sharp/` variants exist in the same package if ever needed — re-run the command below and copy the relevant folder.

To refresh or add another style:
```
npm pack @material-symbols/svg-400
tar xzf material-symbols-svg-400-*.tgz
cp -r package/outlined docs/material-symbols/outlined   # or package/rounded, package/sharp
```

## How to use one of these icons in `ICON_PATHS`

1. Find the icon: `docs/material-symbols/outlined/<name>.svg` (e.g. `cycle.svg`). A `-fill.svg` suffix is the filled/solid variant of the same glyph.
2. Each file is a single `<path d="...">` inside `viewBox="0 -960 960 960"` — a different coordinate space than this app's uniform `viewBox="0 0 24 24"`.
3. Wrap the path in a `<g transform="translate(0,24) scale(0.025)">` to remap it into the 24×24 box (0.025 = 24/960; the `translate(0,24)` compensates for the source's `-960..0` y-range). This is the same technique already used for the `pharmacy` capsule glyph's `<g transform="rotate(45 12 12)">` and for `repeat`'s Material Symbols "Cycle" glyph — see either in `js/classic-globals.js` for a worked example.
4. Since these are solid filled shapes (not stroked outlines like most of this app's hand-drawn icons), add `fill="currentColor" stroke="none"` on the `<path>` itself — same precedent as the existing filled `bolt` icon.
5. Verify at real production size (17px icon inside a 34px `.icon-badge`/`.icon-badge` circle) before committing — a shape that looks fine at 64px can still read poorly at 17px. A throwaway local HTML file loading `js/classic-globals.js` and rendering the candidate icon via `window.Icon(name)` at both sizes (screenshotted with Playwright) is the fastest way to check; don't commit that scratch file.
