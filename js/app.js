// Entry point — loaded by index.html via <script type="module" src="./js/app.js">
import './state.js';
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
import { __init_finance__ } from './finance.js';
import { __init_analytics_csv__ } from './analytics-csv.js';
import { __init_debt__ } from './debt.js';
import { __init_shopping__ } from './shopping.js';

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
