// @ts-check
// Entry point — loaded by index.html via <script type="module" src="./js/app.js">
import { AppState } from './state.js';
import './firebase-sync.js';
import { __init_core__ } from './core.js';
import { __init_color_picker__ } from './color-picker.js';
import { __init_auth__ } from './auth.js';
import { __init_app_init__ } from './app-init.js';
import { __init_ui_widgets__ } from './ui-widgets.js';
import { __init_calendar__ } from './calendar.js';
import { __init_settings_managers__ } from './settings-managers.js';
import { __init_goals_profile__ } from './goals-profile.js';
import { __init_notifications__ } from './notifications.js';
import { __init_finance__, addTransaction } from './finance.js';
import { __init_analytics_csv__ } from './analytics-csv.js';
import { __init_debt__ } from './debt.js';
import { __init_shopping__ } from './shopping.js';
import { __init_monobank__, setMonobankSyncGapMsForTesting } from './monobank.js';
import { scanReceiptImage } from './receipt-ocr.js';
import { maybeRefreshCryptoTop } from './dashboard-widgets.js';

// Each chunk's top-level "do something now" statements were deferred into
// an __init_X__() function (see assemble.mjs's deferActionStatements) so
// none of them can run into a temporal-dead-zone reference to another
// chunk's not-yet-initialized export. Calling them here, after every
// static import above has resolved, guarantees the entire module graph
// has finished evaluating first. Order matches the original single-file
// index.html's top-to-bottom execution order (same order as the section
// banners it was split from - see CLAUDE.md).
__init_core__();
__init_color_picker__();
__init_auth__();
__init_app_init__();
__init_ui_widgets__();
__init_calendar__();
__init_settings_managers__();
__init_goals_profile__();
__init_notifications__();
__init_finance__();
__init_analytics_csv__();
__init_debt__();
__init_shopping__();
__init_monobank__();

// Test-only hook, unconditionally attached — not read by any production
// code path. Exists purely so Playwright tests can reach a handful of
// module-scoped internals (AppState directly, plus a few functions each
// kept around specifically for a test to call: addTransaction() to
// simulate a bypassed-UI write attempt, scanReceiptImage()'s optional
// timeoutMs override, setMonobankSyncGapMsForTesting(),
// maybeRefreshCryptoTop() so tests/dashboard-widgets.mjs can trigger a
// second refresh attempt directly to verify its 30-minute rate-limit gate
// dedups correctly, without a full page reload) without depending on
// js/*.js being served as individually fetchable files — a dynamic
// `import('./js/state.js')` from page context worked when this app had no
// bundler, but breaks once those files are inlined into one Vite bundle
// (see CHANGELOG.md's Vite bundler Phase 2/3 entries). This one shared
// hook object works identically whichever way the app was built, so tests
// no longer need to care.
window.__RYTM_TEST_HOOKS__ = { AppState, addTransaction, scanReceiptImage, setMonobankSyncGapMsForTesting, maybeRefreshCryptoTop };
