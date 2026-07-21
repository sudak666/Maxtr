# Lucide (vendored reference, not shipped to the app)

A local copy of [Lucide](https://lucide.dev)'s icon set (ISC license, a community-run fork of Feather Icons), vendored as a second offline reference for drawing new `js/classic-globals.js` `ICON_PATHS` entries — same purpose and same exclusion from the build as `docs/material-symbols/` (see that directory's own README for the general rationale). **Not loaded, imported, or shipped by the app.**

## Why a second icon library, alongside Material Symbols

Lucide's SVGs are already in this app's own native format: `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`, round caps/joins — a stroke-based line-icon style, not Material Symbols' solid filled shapes. Concretely:

- **Material Symbols** (`docs/material-symbols/`) tends to win when a bold, solid glyph reads more clearly at this app's small production size (17px icon / 34px badge) than a thin stroke outline can — that's why `car`/`pharmacy`/`flame`/`handCoin`/`diffArrows`/`repeat` all ended up sourced from there.
- **Lucide** is the better source when the goal is a new icon that matches the *rest* of this app's hand-drawn stroke icons stylistically (most of `ICON_PATHS` — `calendar`/`wallet`/`house`/`bag`/etc. — is stroke-based, not filled), since it needs **no coordinate transform at all** (see below) — just drop its `<path>` elements straight into `ICON_PATHS`, no `<g transform>` remapping required.

## Source

Downloaded via `npm pack lucide-static` (ISC, registry.npmjs.org — same fetch method as `docs/material-symbols/`). Only `icons/*.svg` (1997 files, individual icons) is kept; the package's `dist/`, `font/`, and sprite-sheet variants were skipped as redundant for this purpose.

To refresh: `npm pack lucide-static`, `tar xzf lucide-static-*.tgz`, `cp -r package/icons docs/lucide/icons`.

## How to use one of these icons in `ICON_PATHS`

1. Find the icon: `docs/lucide/icons/<name>.svg` (e.g. `repeat.svg`).
2. Each file is already `viewBox="0 0 24 24"` with one or more `<path>` elements, `fill="none" stroke="currentColor"` — this app's own native icon format. Just copy the `<path .../>` element(s) (drop the wrapping `<svg>` tag and its attributes) straight into a new `ICON_PATHS` entry, no `<g transform>` needed.
3. Lucide's default `stroke-width` is `2`; this app's `window.Icon()` wrapper already sets `stroke-width="1.8"` on the outer `<svg>`, so leave the copied `<path>` elements without their own `stroke-width` attribute — they'll inherit `1.8` automatically, consistent with every other hand-drawn icon.
4. Verify at real production size (17px icon inside a 34px badge circle) before committing, same as any other icon change — see `docs/material-symbols/README.md`'s step 5 for the recipe (a throwaway local HTML file + Playwright screenshot, not committed).
