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
// show/hide+reorder widget system — only chart/goals remain toggleable.
export const WIDGET_ORDER_DEFAULT = ['chart','goals'];
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
  currentUser: null,
  activeProfileId: 'default',
  profilesMeta: {list:[{id:'default', name:''}], updatedAt:0},
  shifts: {},
  transactions: [],
  recurring: [],
  shoppingList: [],
  currentFinanceType: 'income',
  editingTxId: null,
  selectedDateKey: null,
  financeChartSeries: 'net',
  txFilter: 'all',
  fbTimer: null,
  shiftTypes: [],
  autoFillSchedule: {enabled:false, typeId:'', pattern:'every', anchorDate:''},
  wallets: [],
  categories: {income:[], expense:[]},
  budgets: {},
  subcategories: {},
  currencyRates: {},
  tags: [],
  selectedTagIds: [],
  profile: {nickname:'', avatar:''},
  subscription: {plan:'free', expiresAt:null},
  widgets: {rates:true, converter:true, analytics:true, chart:true, goals:true},
  widgetOrder: WIDGET_ORDER_DEFAULT.slice(),
  notifSettings: {enabled:false, time:'21:00', budgetAlerts:false, recurringAlerts:false, timeZone:'UTC'},
  txCategoryFilter: null,
  autoRules: [],
  goals: [],
  catBackfillDone: false,
  catLegacyMerged: false,
  debts: [],
  currentDebtId: null,
  MONTHS: LANG_CALENDAR[window.currentLang].months,
  MONTHS_SHORT: LANG_CALENDAR[window.currentLang].monthsShort,
  WEEKDAYS: LANG_CALENDAR[window.currentLang].weekdays,
  colorPickTarget: null,
  avatarPickTargetProfileId: null,
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
  __dlgResolve: null,
  openModalStack: [],
  expandedShiftTypeId: null,
  ratesSource: 'nbu',
  catMgrType: 'expense',
  expandedCatIdx: null,
  catActionIdx: null,
  expandedGoalId: null,
  showNewGoalForm: false,
  messagingInstance: null,
  analyticsPeriod: 'month',
  debtEntryEditId: null,
};
