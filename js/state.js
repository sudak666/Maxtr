// @ts-check
// Shared mutable app state — every field here used to be its own
// top-level `let` binding in the original single-file index.html. Unified
// into one object (rather than individual `export let x`) because ES
// modules only allow a module to reassign bindings it declared itself;
// many of these fields are reassigned from several different files
// (e.g. fbLoadNow() in firebase-sync.js resets almost all of them), which
// plain `export let x` cannot support. A plain property write
// (`AppState.x = value`) has no such restriction.
//
// This file intentionally has no imports of its own (see the
// COLOCATE_CONST_NAMES note in the generator script this was produced by)
// — every other split file transitively depends on state.js, so if IT had
// a circular dependency back on any of them, AppState's top-level
// initialization could run into a temporal-dead-zone ReferenceError
// depending on module evaluation order. Keeping this file dependency-free
// avoids that class of bug entirely rather than relying on getting the
// evaluation order right.
// rates/converter/analytics moved into #tools-modal (see CLAUDE.md's
// Finance-tab-widgets section) and are no longer part of the
// show/hide+reorder widget system. dailyTip/cryptoTop (js/dashboard-widgets.js)
// added alongside goals so the Widgets manager has more than one item.
/** @typedef {{id: string, name: string, qty: number, done: boolean, createdAt: number}} ShoppingItem */
/** @typedef {{id: string, name: string, color: string, icon: string, currency?: string}} Wallet */
/** @typedef {{id: string, name: string, short: string, code?: string, color: string, amount: number, hours: number, isOff?: boolean}} ShiftType */
/** @typedef {{id: string, name: string, avatar?: string, createdAt?: number, kind?: 'shared', ownerUid?: string}} ProfileMeta */
/** @typedef {{id: string, name: string, color: string}} Tag */

export const WIDGET_ORDER_DEFAULT = ['goals', 'dailyTip', 'cryptoTop'];
export const LANG_CALENDAR = {
  uk:{
    months:['Січень','Лютий','Березень','Квітень','Травень','Червень','Липень','Серпень','Вересень','Жовтень','Листопад','Грудень'],
    monthsShort:['Січ','Лют','Бер','Кві','Тра','Чер','Лип','Сер','Вер','Жов','Лис','Гру'],
    weekdays:['Пн','Вт','Ср','Чт','Пт','Сб','Нд'],
  },
  en:{
    months:['January','February','March','April','May','June','July','August','September','October','November','December'],
    monthsShort:['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'],
    weekdays:['Mo','Tu','We','Th','Fr','Sa','Su'],
  }
};

export const AppState = {
  /** @type {import('firebase/auth').User | null} */
  currentUser: null,
  activeProfileId: 'default',
  // Set only while viewing a *shared* profile (see js/firebase-sync.js's
  // "SHARED PROFILES" section) — the uid of the account that actually owns
  // the data at users/{activeProfileOwnerUid}/max_tracker/{doc}@{activeProfileId}.
  // null for the current account's own profiles (local or 'default'),
  // which is the overwhelmingly common case and always was, pre-sharing.
  /** @type {string | null} */
  activeProfileOwnerUid: null,
  // The current account's permission level on the active profile —
  // 'editor' (full read/write, the default/only behavior before granular
  // permissions existed) or 'viewer' (read-only). Always 'editor' for your
  // own profiles (including 'default'); only ever 'viewer' while
  // activeProfileOwnerUid is set AND the owner explicitly downgraded you.
  // Loaded from shared_members@{profileId}'s roles map on switchProfile()
  // — see js/firebase-sync.js's loadActiveProfileRole().
  activeProfileRole: 'editor',
  /** @type {{list: ProfileMeta[], updatedAt: number}} */
  profilesMeta: {list:[{id:'default', name:''}], updatedAt:0},
  /** @type {Record<string, string[]>} */
  shifts: {},
  /** @type {any[]} */
  transactions: [],
  /** @type {any[]} */
  recurring: [],
  /** @type {ShoppingItem[]} */
  shoppingList: [],
  currentFinanceType: 'income',
  editingTxId: null,
  /** @type {string | null} */
  selectedDateKey: null,
  financeChartSeries: 'net',
  txFilter: 'all',
  txSearch: '',
  /** @type {ReturnType<typeof setTimeout> | null} */
  fbTimer: null,
  /** @type {ShiftType[]} */
  shiftTypes: [],
  autoFillSchedule: {enabled:false, typeId:'', pattern:'every', anchorDate:''},
  /** @type {Wallet[]} */
  wallets: [],
  /** @type {Record<string, string[]>} */
  categories: {income:[], expense:[]},
  /** @type {Record<string, number>} */
  budgets: {},
  /** @type {Record<string, string[]>} */
  subcategories: {},
  // Manual per-category icon override, keyed by category name (same
  // name-only keying categoryColor/categoryIcon already use — see
  // categoryIcon() in core.js, which checks this before its own
  // exact-name/keyword/hash fallbacks). Set via the icon picker in
  // settings-managers.js.
  /** @type {Record<string, string>} */
  categoryIcons: {},
  /** @type {Record<string, number>} */
  currencyRates: {},
  /** @type {Tag[]} */
  tags: [],
  /** @type {string[]} */
  selectedTagIds: [],
  profile: {nickname:'', avatar:''},
  subscription: {plan:'free', expiresAt:null},
  widgets: {rates:true, converter:true, analytics:true, chart:true, goals:true, dailyTip:true, cryptoTop:true},
  widgetOrder: WIDGET_ORDER_DEFAULT.slice(),
  notifSettings: {enabled:false, time:'21:00', budgetAlerts:false, recurringAlerts:false, debtAlerts:false, timeZone:'UTC'},
  txCategoryFilter: null,
  autoRules: [],
  goals: [],
  catBackfillDone: false,
  catLegacyMerged: false,
  // One-time flag: has the finance doc's legacy `data` array already been
  // copied into the `transactions` Firestore subcollection? See
  // MIGRATION_PLAN_transactions.md / CLAUDE.md's Firebase data model
  // section. Same pattern as catBackfillDone/catLegacyMerged above — persisted
  // in the finance doc so this only ever runs once per account/profile, and
  // (critically) so an account that migrates and then deletes every
  // transaction doesn't get its stale legacy `data` array re-migrated back
  // in on a later load.
  txMigrated: false,
  /** @type {any[]} */
  debts: [],
  currentDebtId: null,
  // Bank-account integrations, keyed by provider — currently only
  // 'monobank'. null until the user connects one; see js/monobank.js.
  // Shape once connected: {token, clientName, accounts:[{id, kind:'account'|'jar',
  // label, currencyAlpha}], mapping:{monobankAccountId: walletId}, lastSyncAt}.
  // Synced in the finance doc (js/color-picker.js's fbSaveNow/seedConfigFromDocs)
  // like every other per-profile setting — the token itself is only ever
  // sent to this app's own monobankProxy Cloud Function, never anywhere else.
  integrations: {monobank: null},
  MONTHS: LANG_CALENDAR[window.currentLang].months,
  MONTHS_SHORT: LANG_CALENDAR[window.currentLang].monthsShort,
  WEEKDAYS: LANG_CALENDAR[window.currentLang].weekdays,
  /** @type {{kind: string, id: string} | null} */
  colorPickTarget: null,
  /** @type {string | null} */
  avatarPickTargetProfileId: null,
  /** @type {Record<string, number>} */
  lastKnownUpdatedAt: {shifts:0, finance:0, debt:0},
  authMode: 'login',
  recaptchaVerifier: null,
  phoneConfirmationResult: null,
  linkRecaptchaVerifier: null,
  linkPhoneConfirmationResult: null,
  pinUnlocked: false,
  onboardIndex: 0,
  initialized: false,
  activeSettingsGroup: 'all',
  selectsEnhanced: false,
  /** @type {((value: string | boolean | null) => void) | null} */
  __dlgResolve: null,
  openModalStack: [],
  expandedShiftTypeId: null,
  ratesSource: 'nbu',
  catMgrType: 'expense',
  expandedCatIdx: null,
  catActionIdx: null,
  expandedRuleId: null,
  expandedRecurringId: null,
  expandedBudgetCat: null,
  expandedGoalId: null,
  showNewGoalForm: false,
  messagingInstance: null,
  analyticsPeriod: 'month',
  debtEntryEditId: null,
};
