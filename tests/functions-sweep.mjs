// Unit tests for functions/lib/sweep.js (the notificationSweep core logic),
// using a fake in-memory db and a fake sendPush — no Firebase/network/
// firebase-admin credentials needed. Plain node:
//
//   node tests/functions-sweep.mjs
//
// Split out of functions/index.js into lib/sweep.js specifically so these
// are testable this way (see that file's header comment) — mirrors
// tests/unit.mjs's approach for functions/lib/pure.js.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { sweepProfile, sweepToken } = require('../functions/lib/sweep.js');

let passed = 0;
async function test(name, fn) {
  await fn();
  passed++;
  console.log(`[ok] ${name}`);
}

// Minimal fake Firestore doc snapshot.
function snap(data) {
  return { exists: data != null, data: () => data };
}

// Minimal fake Firestore QuerySnapshot, for the transactions subcollection.
function qsnap(items) {
  return { empty: items.length === 0, docs: items.map((d) => ({ data: () => d })) };
}

// Fake db: a Map of path -> doc data, plus an optional per-path artificial
// delay (ms) so the parallel-fetch tests can prove two reads actually
// overlap instead of running one after another. `.collection(path).get()`
// reuses the same Map, keyed by the transactions subcollection's own path,
// holding a plain array of transaction objects (or nothing, for tests that
// predate the transactions-subcollection migration and only ever set
// finance.data) — see functions/lib/sweep.js's sweepProfile()/sweepToken()
// for how a missing/empty collection falls back to finance.data.
function fakeDb(docsByPath, delaysByPath = {}) {
  const getCalls = [];
  return {
    getCalls,
    doc(path) {
      return {
        ref: {
          async delete() { docsByPath.delete(path); },
          async set(data, opts) {
            const prev = (opts && opts.merge && docsByPath.get(path)) || {};
            docsByPath.set(path, { ...prev, ...data });
          },
        },
        async get() {
          const startedAt = Date.now();
          const delay = delaysByPath[path] || 0;
          if (delay) await new Promise((r) => setTimeout(r, delay));
          getCalls.push({ path, startedAt, finishedAt: Date.now() });
          return snap(docsByPath.get(path));
        },
      };
    },
    collection(path) {
      return {
        async get() {
          const startedAt = Date.now();
          const delay = delaysByPath[path] || 0;
          if (delay) await new Promise((r) => setTimeout(r, delay));
          getCalls.push({ path, startedAt, finishedAt: Date.now() });
          const arr = docsByPath.get(path) || [];
          return { empty: arr.length === 0, docs: arr.map((d) => ({ data: () => d })) };
        },
      };
    },
  };
}

function fakeTokenDoc(uid, data) {
  const store = new Map([[`push_tokens/${uid}`, data]]);
  return {
    id: uid,
    data: () => data,
    ref: {
      async delete() { store.set(`push_tokens/${uid}`, undefined); this._deleted = true; },
      async set(newData, opts) {
        const prev = (opts && opts.merge && store.get(`push_tokens/${uid}`)) || {};
        store.set(`push_tokens/${uid}`, { ...prev, ...newData });
        this._written = { ...prev, ...newData };
      },
    },
  };
}

const NOW = new Date('2026-07-15T20:00:00Z'); // 20:00 UTC = 20:00 Europe/Kyiv-ish for a UTC test profile

await test('sweepProfile: sends the daily reminder once past the set hour with no transaction logged today', async () => {
  const uid = 'u1';
  const financeData = {
    notifSettings: { enabled: true, time: '18:00', timeZone: 'UTC' },
    data: [{ date: '2026-07-10', type: 'expense', amount: 10, currency: 'UAH' }],
    wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const sent = [];
  const sendPushFn = async (token, title, body) => { sent.push({ token, title, body }); return { ok: true, invalid: false }; };
  const result = await sweepProfile(null, sendPushFn, uid, 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(sent.length, 1);
  assert.equal(sent[0].title, 'Zminka');
  assert.deepEqual(result.updates, { sentDaily: '2026-07-15' });
  assert.equal(result.tokenInvalid, false);
});

await test('sweepProfile: does not re-send the daily reminder once already sent today (dedup)', async () => {
  const financeData = {
    notifSettings: { enabled: true, time: '18:00', timeZone: 'UTC' },
    data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const sendPushFn = async () => { throw new Error('should not be called'); };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', { sentDaily: '2026-07-15' }, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(result.updates, null);
});

await test('sweepProfile: does not send the daily reminder if a transaction was already logged today', async () => {
  const financeData = {
    notifSettings: { enabled: true, time: '18:00', timeZone: 'UTC' },
    data: [{ date: '2026-07-15', type: 'expense', amount: 5, currency: 'UAH' }],
    wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const sendPushFn = async () => { throw new Error('should not be called'); };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(result.updates, null);
});

await test('sweepProfile: sends a budget-exceeded push once per category per month', async () => {
  const financeData = {
    notifSettings: { enabled: false, budgetAlerts: true, timeZone: 'UTC' },
    data: [
      { date: '2026-07-01', type: 'expense', category: 'Кава', amount: 600, currency: 'UAH' },
      { date: '2026-07-10', type: 'expense', category: 'Кава', amount: 500, currency: 'UAH' },
    ],
    wallets: [], budgets: { Кава: 1000 }, categories: { expense: ['Кава'] }, recurring: [], currencyRates: {},
  };
  const sent = [];
  const sendPushFn = async (token, title, body) => { sent.push({ title, body }); return { ok: true, invalid: false }; };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(sent.length, 1);
  assert.equal(sent[0].title, 'Бюджет перевищено');
  assert.deepEqual(result.updates, { sentBudget: { '2026-07_Кава': true } });
});

await test('sweepProfile: skips a budget category already flagged this month (dedup)', async () => {
  const financeData = {
    notifSettings: { budgetAlerts: true, timeZone: 'UTC' },
    data: [{ date: '2026-07-01', type: 'expense', category: 'Кава', amount: 2000, currency: 'UAH' }],
    wallets: [], budgets: { Кава: 1000 }, categories: { expense: ['Кава'] }, recurring: [], currencyRates: {},
  };
  const sendPushFn = async () => { throw new Error('should not be called'); };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', { sentBudget: { '2026-07_Кава': true } }, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(result.updates, null);
});

await test('sweepProfile: sends an upcoming-recurring-payment push exactly one day ahead', async () => {
  const financeData = {
    notifSettings: { recurringAlerts: true, timeZone: 'UTC' },
    data: [], wallets: [{ id: 'w1', currency: 'UAH' }], budgets: {}, categories: {},
    recurring: [{ id: 'r1', active: true, nextDate: '2026-07-16', amount: 500, wallet: 'w1', category: 'Оренда' }],
    currencyRates: {},
  };
  const sent = [];
  const sendPushFn = async (token, title, body) => { sent.push({ title, body }); return { ok: true, invalid: false }; };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(sent.length, 1);
  assert.equal(sent[0].title, 'Наближається платіж');
  assert.deepEqual(result.updates, { sentRecurring: { 'r1_2026-07-16': true } });
});

await test('sweepProfile: sends an upcoming-debt-due-date push exactly one day ahead', async () => {
  const financeData = {
    notifSettings: { debtAlerts: true, timeZone: 'UTC' },
    data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const debtData = { debts: [{ id: 'd1', name: 'Позика в банку', dueDate: '2026-07-16' }] };
  const sent = [];
  const sendPushFn = async (token, title, body) => { sent.push({ title, body }); return { ok: true, invalid: false }; };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap(debtData));
  assert.equal(sent.length, 1);
  assert.equal(sent[0].title, 'Наближається термін боргу');
  assert.match(sent[0].body, /Позика в банку/);
  assert.deepEqual(result.updates, { sentDebt: { 'd1_2026-07-16': true } });
});

await test('sweepProfile: skips a debt already flagged for its due date (dedup)', async () => {
  const financeData = {
    notifSettings: { debtAlerts: true, timeZone: 'UTC' },
    data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const debtData = { debts: [{ id: 'd1', name: 'Позика', dueDate: '2026-07-16' }] };
  const sendPushFn = async () => { throw new Error('should not be called'); };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', { sentDebt: { 'd1_2026-07-16': true } }, NOW, snap(financeData), snap(debtData));
  assert.equal(result.updates, null);
});

await test('sweepProfile: does not send a debt-due push when dueDate is not exactly tomorrow, or debtAlerts is off', async () => {
  const debtData = { debts: [{ id: 'd1', name: 'Позика', dueDate: '2026-07-20' }] };
  const sendPushFn = async () => { throw new Error('should not be called'); };
  const alertsOn = { notifSettings: { debtAlerts: true, timeZone: 'UTC' }, data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {} };
  let result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(alertsOn), snap(debtData));
  assert.equal(result.updates, null);

  const alertsOff = { notifSettings: { debtAlerts: false, timeZone: 'UTC' }, data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {} };
  const debtDueTomorrow = { debts: [{ id: 'd2', name: 'Позика', dueDate: '2026-07-16' }] };
  result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(alertsOff), snap(debtDueTomorrow));
  assert.equal(result.updates, null);
});

await test('sweepProfile: a missing debt doc for a profile is treated as no debts (no throw)', async () => {
  const financeData = {
    notifSettings: { debtAlerts: true, timeZone: 'UTC' },
    data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const sendPushFn = async () => { throw new Error('should not be called'); };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap(null));
  assert.equal(result.updates, null);
});

await test('sweepProfile: a permanently-invalid token stops later checks in the same profile', async () => {
  const financeData = {
    notifSettings: { enabled: true, time: '00:00', timeZone: 'UTC', budgetAlerts: true },
    data: [], wallets: [], budgets: { Кава: 1 },
    categories: { expense: ['Кава'] },
    recurring: [], currencyRates: {},
  };
  // Force spend over budget so both the daily reminder AND the budget
  // check would fire if allowed to run.
  financeData.data.push({ date: '2026-07-01', type: 'expense', category: 'Кава', amount: 999, currency: 'UAH' });
  let calls = 0;
  const sendPushFn = async () => { calls++; return { ok: false, invalid: true }; };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(calls, 1); // daily reminder attempted, found invalid, budget check never ran
  assert.equal(result.tokenInvalid, true);
});

await test('sweepProfile: a missing finance doc for a profile is skipped (returns null, no throw)', async () => {
  const result = await sweepProfile(null, async () => { throw new Error('no'); }, 'u1', 'default', 'tok1', {}, NOW, snap(null));
  assert.equal(result, null);
});

// ── sweepProfile: reads from the transactions subcollection (post-migration
// accounts) instead of finance.data — see MIGRATION_PLAN_transactions.md.

await test('sweepProfile: prefers the transactions subcollection over finance.data when both are present', async () => {
  const financeData = {
    notifSettings: { enabled: true, time: '18:00', timeZone: 'UTC' },
    // Stale legacy array a migrated account's finance doc might still carry
    // (or might not, since fbSaveNow() stops writing it post-cutover) — must
    // be ignored once a non-empty transactions subcollection exists.
    data: [{ date: '2020-01-01', type: 'expense', amount: 1, currency: 'UAH' }],
    wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const sendPushFn = async () => { throw new Error('should not be called'); };
  const result = await sweepProfile(
    null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }),
    qsnap([{ date: '2026-07-15', type: 'expense', amount: 5, currency: 'UAH' }]), // logged today -> no reminder
  );
  assert.equal(result.updates, null);
});

await test('sweepProfile: budget check sums amounts from the transactions subcollection, not finance.data', async () => {
  const financeData = {
    notifSettings: { budgetAlerts: true, timeZone: 'UTC' },
    data: [], // empty legacy array — a fully-migrated account
    wallets: [], budgets: { Кава: 1000 }, categories: { expense: ['Кава'] }, recurring: [], currencyRates: {},
  };
  const sent = [];
  const sendPushFn = async (token, title, body) => { sent.push({ title, body }); return { ok: true, invalid: false }; };
  const result = await sweepProfile(
    null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }),
    qsnap([
      { date: '2026-07-01', type: 'expense', category: 'Кава', amount: 600, currency: 'UAH' },
      { date: '2026-07-10', type: 'expense', category: 'Кава', amount: 500, currency: 'UAH' },
    ]),
  );
  assert.equal(sent.length, 1);
  assert.equal(sent[0].title, 'Бюджет перевищено');
  assert.deepEqual(result.updates, { sentBudget: { '2026-07_Кава': true } });
});

await test('sweepProfile: an empty transactions subcollection falls back to finance.data (pre-migration account)', async () => {
  const financeData = {
    notifSettings: { enabled: true, time: '18:00', timeZone: 'UTC' },
    data: [{ date: '2026-07-15', type: 'expense', amount: 5, currency: 'UAH' }], // logged today -> no reminder
    wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const sendPushFn = async () => { throw new Error('should not be called'); };
  const result = await sweepProfile(
    null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }), qsnap([]),
  );
  assert.equal(result.updates, null);
});

// ── sweepProfile: passes a per-notification-type 'type' string as
// sendPushFn's 4th argument, so index.js's real sendPush() can look up a
// themed icon (see functions/index.js's NOTIF_ICONS) instead of every push
// showing the same generic app icon.

await test("sweepProfile: passes type='daily' for the daily reminder", async () => {
  const financeData = {
    notifSettings: { enabled: true, time: '18:00', timeZone: 'UTC' },
    data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const calls = [];
  const sendPushFn = async (...args) => { calls.push(args); return { ok: true, invalid: false }; };
  await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(calls.length, 1);
  assert.equal(calls[0][3], 'daily');
});

await test("sweepProfile: passes type='budget' for the budget-exceeded push", async () => {
  const financeData = {
    notifSettings: { budgetAlerts: true, timeZone: 'UTC' },
    data: [{ date: '2026-07-01', type: 'expense', category: 'Кава', amount: 2000, currency: 'UAH' }],
    wallets: [], budgets: { Кава: 1000 }, categories: { expense: ['Кава'] }, recurring: [], currencyRates: {},
  };
  const calls = [];
  const sendPushFn = async (...args) => { calls.push(args); return { ok: true, invalid: false }; };
  await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(calls.length, 1);
  assert.equal(calls[0][3], 'budget');
});

await test("sweepProfile: passes type='recurring' for the upcoming-recurring-payment push", async () => {
  const financeData = {
    notifSettings: { recurringAlerts: true, timeZone: 'UTC' },
    data: [], wallets: [{ id: 'w1', currency: 'UAH' }], budgets: {}, categories: {},
    recurring: [{ id: 'r1', active: true, nextDate: '2026-07-16', amount: 500, wallet: 'w1', category: 'Оренда' }],
    currencyRates: {},
  };
  const calls = [];
  const sendPushFn = async (...args) => { calls.push(args); return { ok: true, invalid: false }; };
  await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap({ debts: [] }));
  assert.equal(calls.length, 1);
  assert.equal(calls[0][3], 'recurring');
});

await test("sweepProfile: passes type='debt' for the upcoming-debt-due-date push", async () => {
  const financeData = {
    notifSettings: { debtAlerts: true, timeZone: 'UTC' },
    data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const debtData = { debts: [{ id: 'd1', name: 'Позика', dueDate: '2026-07-16' }] };
  const calls = [];
  const sendPushFn = async (...args) => { calls.push(args); return { ok: true, invalid: false }; };
  await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData), snap(debtData));
  assert.equal(calls.length, 1);
  assert.equal(calls[0][3], 'debt');
});

// ── sweepToken: parallel-fetch + dedup + multi-profile behavior ──

await test('sweepToken: fetches profiles_meta and the default profile\'s finance doc concurrently, not sequentially', async () => {
  const uid = 'u2';
  const docs = new Map([
    [`push_tokens/${uid}`, undefined],
    [`users/${uid}/max_tracker/profiles_meta`, undefined], // no extra profiles -> just 'default'
    [`users/${uid}/max_tracker/finance`, { notifSettings: {}, data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {} }],
  ]);
  // Both reads take 100ms; if they ran sequentially this test would take
  // ~200ms+, if concurrent it should take close to ~100ms.
  const delays = {
    [`users/${uid}/max_tracker/profiles_meta`]: 100,
    [`users/${uid}/max_tracker/finance`]: 100,
  };
  const db = fakeDb(docs, delays);
  const tokenDoc = fakeTokenDoc(uid, { token: 'tok2' });
  const start = Date.now();
  await sweepToken(db, async () => ({ ok: true, invalid: false }), tokenDoc, NOW);
  const elapsed = Date.now() - start;
  assert.ok(elapsed < 180, `expected concurrent reads to finish in well under 200ms, took ${elapsed}ms`);
});

await test('sweepToken: single-profile account (no profiles_meta) sweeps the default profile only', async () => {
  const uid = 'u3';
  const docs = new Map([
    [`push_tokens/${uid}`, undefined],
    [`users/${uid}/max_tracker/profiles_meta`, undefined],
    [`users/${uid}/max_tracker/finance`, {
      notifSettings: { enabled: true, time: '00:00', timeZone: 'UTC' },
      data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
    }],
  ]);
  const db = fakeDb(docs);
  const tokenDoc = fakeTokenDoc(uid, { token: 'tok3' });
  let sendCount = 0;
  await sweepToken(db, async () => { sendCount++; return { ok: true, invalid: false }; }, tokenDoc, NOW);
  assert.equal(sendCount, 1); // daily reminder fires for the default profile
  assert.equal(tokenDoc.ref._written.profileState.default.sentDaily, '2026-07-15');
});

await test('sweepToken: multi-profile account sweeps every profile independently', async () => {
  const uid = 'u4';
  const docs = new Map([
    [`push_tokens/${uid}`, undefined],
    [`users/${uid}/max_tracker/profiles_meta`, { list: [{ id: 'default' }, { id: 'p2' }] }],
    [`users/${uid}/max_tracker/finance`, {
      notifSettings: { enabled: true, time: '00:00', timeZone: 'UTC' },
      data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
    }],
    [`users/${uid}/max_tracker/finance@p2`, {
      notifSettings: { enabled: true, time: '00:00', timeZone: 'UTC' },
      data: [{ date: '2026-07-15', type: 'expense', amount: 1, currency: 'UAH' }], // already logged today -> no reminder
      wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
    }],
  ]);
  const db = fakeDb(docs);
  const tokenDoc = fakeTokenDoc(uid, { token: 'tok4' });
  let sendCount = 0;
  await sweepToken(db, async () => { sendCount++; return { ok: true, invalid: false }; }, tokenDoc, NOW);
  assert.equal(sendCount, 1); // only 'default' fires; 'p2' already has a transaction logged today
  assert.equal(tokenDoc.ref._written.profileState.default.sentDaily, '2026-07-15');
  assert.equal(tokenDoc.ref._written.profileState.p2, undefined);
});

await test('sweepToken: fetches the transactions subcollection alongside the finance doc and uses it for the daily check', async () => {
  const uid = 'u4c';
  const docs = new Map([
    [`push_tokens/${uid}`, undefined],
    [`users/${uid}/max_tracker/profiles_meta`, undefined],
    [`users/${uid}/max_tracker/finance`, {
      notifSettings: { enabled: true, time: '00:00', timeZone: 'UTC' },
      data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
    }],
    // Subcollection path — an array of tx objects, not a doc.
    [`users/${uid}/max_tracker/finance/transactions`, [{ date: '2026-07-15', type: 'expense', amount: 1, currency: 'UAH' }]],
  ]);
  const db = fakeDb(docs);
  const tokenDoc = fakeTokenDoc(uid, { token: 'tok4c' });
  let sendCount = 0;
  await sweepToken(db, async () => { sendCount++; return { ok: true, invalid: false }; }, tokenDoc, NOW);
  // Already logged today per the subcollection -> no reminder, even though
  // finance.data (the legacy array) is empty and would have fired one.
  assert.equal(sendCount, 0);
  assert.ok(db.getCalls.some((c) => c.path === `users/${uid}/max_tracker/finance/transactions`));
});

await test('sweepToken: fetches the debt doc alongside the finance doc and fires an upcoming-due-date push', async () => {
  const uid = 'u4b';
  const docs = new Map([
    [`push_tokens/${uid}`, undefined],
    [`users/${uid}/max_tracker/profiles_meta`, undefined],
    [`users/${uid}/max_tracker/finance`, {
      notifSettings: { debtAlerts: true, timeZone: 'UTC' },
      data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
    }],
    [`users/${uid}/max_tracker/debt`, { debts: [{ id: 'd1', name: 'Позика', dueDate: '2026-07-16' }] }],
  ]);
  const db = fakeDb(docs);
  const tokenDoc = fakeTokenDoc(uid, { token: 'tok4b' });
  const sent = [];
  await sweepToken(db, async (token, title, body) => { sent.push({ title, body }); return { ok: true, invalid: false }; }, tokenDoc, NOW);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].title, 'Наближається термін боргу');
  assert.deepEqual(tokenDoc.ref._written.profileState.default.sentDebt, { 'd1_2026-07-16': true });
});

await test('sweepToken: deletes the push_tokens doc when the token turns out to be permanently invalid', async () => {
  const uid = 'u5';
  const docs = new Map([
    [`push_tokens/${uid}`, undefined],
    [`users/${uid}/max_tracker/profiles_meta`, undefined],
    [`users/${uid}/max_tracker/finance`, {
      notifSettings: { enabled: true, time: '00:00', timeZone: 'UTC' },
      data: [], wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
    }],
  ]);
  const db = fakeDb(docs);
  const tokenDoc = fakeTokenDoc(uid, { token: 'tok5' });
  let deleted = false;
  tokenDoc.ref.delete = async () => { deleted = true; };
  await sweepToken(db, async () => ({ ok: false, invalid: true }), tokenDoc, NOW);
  assert.equal(deleted, true);
});

await test('sweepToken: a token doc with no token field is a no-op', async () => {
  const uid = 'u6';
  const db = fakeDb(new Map());
  const tokenDoc = fakeTokenDoc(uid, {});
  // Should return without touching db.doc() at all.
  await sweepToken(db, async () => { throw new Error('should not send'); }, tokenDoc, NOW);
  assert.equal(db.getCalls.length, 0);
});

console.log(`\n${passed} functions-sweep test(s) passed`);
