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

// Fake db: a Map of path -> doc data, plus an optional per-path artificial
// delay (ms) so the parallel-fetch tests can prove two reads actually
// overlap instead of running one after another.
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
  const result = await sweepProfile(null, sendPushFn, uid, 'default', 'tok1', {}, NOW, snap(financeData));
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
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', { sentDaily: '2026-07-15' }, NOW, snap(financeData));
  assert.equal(result.updates, null);
});

await test('sweepProfile: does not send the daily reminder if a transaction was already logged today', async () => {
  const financeData = {
    notifSettings: { enabled: true, time: '18:00', timeZone: 'UTC' },
    data: [{ date: '2026-07-15', type: 'expense', amount: 5, currency: 'UAH' }],
    wallets: [], budgets: {}, categories: {}, recurring: [], currencyRates: {},
  };
  const sendPushFn = async () => { throw new Error('should not be called'); };
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData));
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
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData));
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
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', { sentBudget: { '2026-07_Кава': true } }, NOW, snap(financeData));
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
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData));
  assert.equal(sent.length, 1);
  assert.equal(sent[0].title, 'Наближається платіж');
  assert.deepEqual(result.updates, { sentRecurring: { 'r1_2026-07-16': true } });
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
  const result = await sweepProfile(null, sendPushFn, 'u1', 'default', 'tok1', {}, NOW, snap(financeData));
  assert.equal(calls, 1); // daily reminder attempted, found invalid, budget check never ran
  assert.equal(result.tokenInvalid, true);
});

await test('sweepProfile: a missing finance doc for a profile is skipped (returns null, no throw)', async () => {
  const result = await sweepProfile(null, async () => { throw new Error('no'); }, 'u1', 'default', 'tok1', {}, NOW, snap(null));
  assert.equal(result, null);
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
