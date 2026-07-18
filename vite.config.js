import { cpSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

// Vite bundles ONLY the ES module graph rooted at js/app.js (loaded via
// <script type="module" src="./js/app.js"> in index.html) — nothing else in
// the repo. The entry is js/app.js directly, not index.html, specifically
// so Vite never crawls index.html's other tags: its default HTML-asset
// pipeline would otherwise fingerprint/rewrite <link rel="manifest">,
// <link rel="apple-touch-icon">, etc. (confirmed empirically — an
// index.html-entry build hashed manifest.json and icon-192.png and rewrote
// their hrefs to root-absolute paths, which would break the GitHub Pages
// mirror served under /Maxtr/). Deciding how the *rest* of the static
// asset tree should be built/deployed is Phase 2+ work (see CLAUDE.md /
// the session's plan) — this config's only job is proving the JS module
// graph bundles correctly.
//
// The 4 classic non-module scripts (theme-preinit/touch-active-fix/
// sw-register/classic-globals) and js/vendor/tesseract/** are left
// completely untouched for the same reason — see CLAUDE.md's "js/ module
// layout" and "Receipt scanning (OCR)" sections for the strict
// ordering/relative-path constraints a bundler could otherwise break.
//
// js/receipt-ocr.js does a dynamic import() of the vendored Tesseract
// library using a path *relative to its own module URL*
// (`./vendor/tesseract/tesseract.esm.min.js`). That import is marked
// /* @vite-ignore */ in the source so Vite never tries to bundle the vendor
// library itself — but the relative specifier still has to resolve
// correctly at runtime, which only works if the bundled chunk containing
// receipt-ocr.js's code is emitted at the same directory depth as the
// source js/ folder. Hence entryFileNames/chunkFileNames below both force
// output under dist/js/ instead of Vite's default dist/assets/.
export default {
  build: {
    outDir: 'dist',
    rollupOptions: {
      input: resolve(import.meta.dirname, 'js/app.js'),
      output: {
        entryFileNames: 'js/[name]-[hash].js',
        chunkFileNames: 'js/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
  plugins: [
    {
      name: 'copy-tesseract-vendor',
      closeBundle() {
        const src = resolve(import.meta.dirname, 'js/vendor/tesseract');
        const dest = resolve(import.meta.dirname, 'dist/js/vendor/tesseract');
        if (existsSync(src)) cpSync(src, dest, { recursive: true });
      },
    },
  ],
};
