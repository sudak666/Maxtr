// Runs after `vite build` (see package.json's "build" script) to turn
// dist/ from "just the bundled JS" into a complete, deployable copy of the
// site — everything Firebase Hosting's `public` dir needs, with two
// surgical patches: index.html's one <script type="module"> tag is
// rewritten to point at the built bundle, and sw.js's STATIC_ASSETS list
// (which currently enumerates all 20 module-graph files by hand) is
// regenerated to reference that one bundle file instead. This is the whole
// point of Phase 2: sw.js's precache list can no longer drift out of sync
// with js/*.js, because it's generated from the actual build output, not
// hand-maintained.
//
// Everything else in dist/ is a byte-identical verbatim copy — same
// static-file set firebase.json's hosting.ignore list already scopes to
// (mirrored here in EXCLUDE_TOP/EXCLUDE_MD), plus this repo's own
// dev-only tooling (package.json, vite.config.js, this script's own
// scripts/ dir, node_modules, .git*, dist itself) and the 20 original
// js/*.js module-graph files, which are superseded by the bundle vite
// build already placed at dist/js/app-<hash>.js.
import { readFileSync, writeFileSync, readdirSync, mkdirSync, copyFileSync } from 'node:fs';
import { relative, sep, join } from 'node:path';
import { createHash } from 'node:crypto';

const ROOT = new URL('..', import.meta.url).pathname;
const DIST = join(ROOT, 'dist');

const EXCLUDE_TOP = new Set([
  'firebase.json', 'firestore.rules', 'node_modules', 'functions',
  'docs', 'scripts', 'tests', '_config.yml', 'dist',
  'package.json', 'package-lock.json', 'vite.config.js',
  '.git', '.github', '.gitignore', '.claude',
]);
const EXCLUDE_MD = /\.md$/i;

// The 20 files under sw.js's "module graph" STATIC_ASSETS comment — see
// CLAUDE.md's "js/ module layout". Kept as a literal list (not derived)
// because sw.js's own module-graph-vs-classic-scripts split is a manual,
// documented distinction (js/theme-preinit.js etc. are deliberately NOT in
// this set) that shouldn't silently change just because someone adds an
// unrelated file to js/.
const MODULE_GRAPH_FILES = new Set([
  'app.js', 'state.js', 'core.js', 'firebase-sync.js', 'color-picker.js',
  'auth.js', 'app-init.js', 'ui-widgets.js', 'calendar.js',
  'settings-managers.js', 'goals-profile.js', 'notifications.js',
  'finance.js', 'tx-validation.js', 'receipt-ocr.js', 'analytics-csv.js',
  'debt.js', 'shopping.js', 'privacy-cache.js', 'monobank.js',
]);

// Vendored dependencies reached via a real *static* import (unlike
// js/vendor/tesseract/, which js/receipt-ocr.js only ever reaches via a
// /* @vite-ignore */'d dynamic import() and so must stay a standalone
// file even in dist/) end up fully inlined into the bundle by Vite. A
// standalone copy of these in dist/ would be genuinely unused dead
// weight — never fetched by anything — and worse, listing it in sw.js's
// STATIC_ASSETS would make the dist/-served service worker's
// cache.addAll() 404 during install, since the file doesn't actually
// exist there. Excluded from the copy below and stripped from
// STATIC_ASSETS further down.
const ALWAYS_BUNDLED_VENDOR_DIRS = new Set(['preact']);

// A plain recursive walk (rather than fs.cpSync(ROOT, DIST, {filter})) —
// cpSync refuses outright to copy a directory into its own subdirectory
// ("Cannot copy X to a subdirectory of self"), even when a filter would
// exclude that subdirectory, since DIST lives inside ROOT (dist/ at the
// repo root).
function shouldCopy(rel) {
  if (rel === '') return true;
  const parts = rel.split(sep);
  if (parts[0] === '.well-known') return true; // explicit un-ignore, matches firebase.json
  if (EXCLUDE_TOP.has(parts[0])) return false;
  if (parts[0].startsWith('.')) return false; // other dotfiles/dirs
  if (EXCLUDE_MD.test(rel)) return false;
  if (parts[0] === 'js' && parts.length === 2 && MODULE_GRAPH_FILES.has(parts[1])) return false;
  if (parts[0] === 'js' && parts[1] === 'vendor' && ALWAYS_BUNDLED_VENDOR_DIRS.has(parts[2])) return false;
  return true;
}

function copyTree(srcDir) {
  mkdirSync(join(DIST, relative(ROOT, srcDir)), { recursive: true });
  for (const entry of readdirSync(srcDir, { withFileTypes: true })) {
    const srcPath = join(srcDir, entry.name);
    const rel = relative(ROOT, srcPath);
    if (!shouldCopy(rel)) continue;
    if (entry.isDirectory()) {
      copyTree(srcPath);
    } else if (entry.isFile()) {
      copyFileSync(srcPath, join(DIST, rel));
    }
  }
}
copyTree(ROOT);

// Find the bundle vite build already emitted (dist/js/app-<hash>.js).
const bundleFile = readdirSync(join(DIST, 'js')).find(f => /^app-.*\.js$/.test(f));
if (!bundleFile) throw new Error('build-site.mjs: no dist/js/app-*.js bundle found — did `vite build` run first?');

// Patch index.html: point the one module script tag at the real bundle.
const indexPath = join(DIST, 'index.html');
let html = readFileSync(indexPath, 'utf8');
const originalTag = '<script type="module" src="./js/app.js">';
if (!html.includes(originalTag)) throw new Error(`build-site.mjs: expected to find ${JSON.stringify(originalTag)} in index.html`);
html = html.replace(originalTag, `<script type="module" src="./js/${bundleFile}">`);
writeFileSync(indexPath, html);

// Patch sw.js: replace the hand-maintained 20-file module-graph block in
// STATIC_ASSETS with the one real bundle file, and give this dist-only
// variant its own content-derived CACHE_NAME (distinct from the source
// tree's manually-bumped rytm-vNN scheme, which still governs whatever
// GitHub Pages serves from source unchanged).
const swPath = join(DIST, 'sw.js');
let sw = readFileSync(swPath, 'utf8');
const literalBlock = "  './js/app.js',\n" + [...MODULE_GRAPH_FILES].filter(f => f !== 'app.js').map(f => `  './js/${f}',`).join('\n');
if (!sw.includes(literalBlock)) throw new Error('build-site.mjs: expected sw.js\'s STATIC_ASSETS module-graph block to match the known 20-file list — sw.js may have changed shape upstream, update this script');
sw = sw.replace(literalBlock, `  './js/${bundleFile}',`);

// Strip any always-bundled vendor dependency's own STATIC_ASSETS line
// (see ALWAYS_BUNDLED_VENDOR_DIRS above) — it's inlined into bundleFile
// already, and the standalone file it would otherwise point at doesn't
// exist in dist/.
for (const dir of ALWAYS_BUNDLED_VENDOR_DIRS) {
  sw = sw.replace(new RegExp(`^ {2}'\\./js/vendor/${dir}/[^']+',\\n`, 'm'), '');
}

// CACHE_NAME must change whenever *any* precached file's bytes change, not
// just the JS bundle — the browser detects a new service worker purely by
// byte-comparing sw.js's own text at the registered scope, so if only
// e.g. manifest.json or an icon changes (none of those are content-hashed
// by Vite, they're copied verbatim at fixed filenames) while the bundle
// hash stays the same, sw.js's text would stay byte-identical across two
// otherwise-different deploys — the browser would never notice an update,
// and an already-installed service worker would keep serving the stale
// asset from its cache indefinitely. Hashing every locally-precached
// file's actual content (not just the bundle's own hashed filename) means
// any such change always produces a different CACHE_NAME, and therefore
// different sw.js text, and therefore a real update. FIREBASE_SDK_ASSETS
// doesn't need the same treatment — those are pinned external gstatic
// URLs embedded directly in sw.js's own text, so bumping that pinned
// version already changes sw.js's bytes on its own.
const staticAssetsMatch = sw.match(/const STATIC_ASSETS = \[([\s\S]*?)\n\];/);
if (!staticAssetsMatch) throw new Error('build-site.mjs: could not find STATIC_ASSETS array in sw.js');
const precacheHash = createHash('sha256');
for (const m of staticAssetsMatch[1].matchAll(/'(\.\/[^']+)'/g)) {
  precacheHash.update(readFileSync(join(DIST, m[1].replace(/^\.\//, ''))));
}
const cacheName = `rytm-dist-${precacheHash.digest('hex').slice(0, 12)}`;
sw = sw.replace(/const CACHE_NAME = '[^']*';/, `const CACHE_NAME = '${cacheName}';`);
writeFileSync(swPath, sw);

console.log(`build-site.mjs: dist/ ready — bundle ${bundleFile}, sw.js CACHE_NAME ${cacheName}`);
