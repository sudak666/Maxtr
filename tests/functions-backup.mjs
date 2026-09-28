// Unit tests for functions/lib/backup.js (cloud backups) against an
// in-memory fake Firestore — no credentials/network. Plain node:
//
//   node tests/functions-backup.mjs
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const backup = require('../functions/lib/backup.js');

let passed = 0;
async function test(name, fn) {
  await fn();
  passed++;
  console.log(`[ok] ${name}`);
}

// Fake db over a flat Map<path, data>. collection(path) lists direct
// children only (path + '/' + id with no further '/').
function fakeDb(seed = {}) {
  const store = new Map(Object.entries(seed));
  const docRef = (path) => ({
    path,
    async get() {
      const d = store.get(path);
      return { exists: d !== undefined, id: path.split('/').pop(), data: () => structuredClone(d) };
    },
    async set(data) { store.set(path, structuredClone(data)); },
    async delete() { store.delete(path); },
  });
  return {
    store,
    doc: docRef,
    collection(path) {
      return {
        async get() {
          const docs = [];
          for (const [p, d] of store) {
            if (p.startsWith(path + '/') && !p.slice(path.length + 1).includes('/')) {
              docs.push({ id: p.slice(path.length + 1), data: () => structuredClone(d), ref: docRef(p) });
            }
          }
          return { empty: docs.length === 0, docs };
        },
      };
    },
    batch() {
      const ops = [];
      return {
        set(ref, data) { ops.push(() => store.set(ref.path, structuredClone(data))); },
        delete(ref) { ops.push(() => store.delete(ref.path)); },
        async commit() { ops.forEach((f) => f()); },
      };
    },
  };
}

const U = 'u1';
const base = (profileId = 'default') => {
  const s = (n) => (profileId === 'default' ? n : `${n}@${profileId}`);
  return {
    [`users/${U}/max_tracker/${s('shifts')}`]: { data: { '2026-09-01': ['day'] }, updatedAt: 1 },
    [`users/${U}/max_tracker/${s('finance')}`]: { wallets: [{ id: 'w1', name: 'Картка' }], updatedAt: 1 },
    [`users/${U}/max_tracker/${s('finance')}/transactions/1`]: { id: 1, amount: 100, comment: 'кава', date: '2026-09-01' },
    [`users/${U}/max_tracker/${s('finance')}/transactions/2`]: { id: 2, amount: 50, date: '2026-09-02' },
  };
};
const backupIds = (db) => [...db.store.keys()].filter((p) => /^users\/u1\/backups\/[^/]+$/.test(p)).map((p) => p.split('/').pop());
const fromMillis = (ms) => ({ restoredTs: ms });

await test('createBackup writes metadata + chunks, dedups identical content', async () => {
  const db = fakeDb(base());
  const r1 = await backup.createBackup(db, U, 'default', 'manual', 1000);
  assert.equal(r1.status, 'created');
  const meta = db.store.get(`users/${U}/backups/${r1.id}`);
  assert.equal(meta.txCount, 2);
  assert.equal(meta.profileId, 'default');
  assert.equal(meta.chunks, 1);
  assert.ok(db.store.get(`users/${U}/backups/${r1.id}/chunks/0`).data.length > 0);
  // Only updatedAt changed -> still "unchanged".
  db.store.get(`users/${U}/max_tracker/finance`).updatedAt = 99;
  const r2 = await backup.createBackup(db, U, 'default', 'daily', 2000);
  assert.deepEqual(r2, { status: 'unchanged', id: r1.id });
});

await test('createBackup skips an empty profile', async () => {
  const db = fakeDb({});
  assert.equal((await backup.createBackup(db, U, 'default', 'daily', 1)).status, 'empty');
  assert.equal(backupIds(db).length, 0);
});

await test('restoreBackup brings back deleted/edited data and takes a pre_restore snapshot', async () => {
  const db = fakeDb(base());
  const { id } = await backup.createBackup(db, U, 'default', 'manual', 1000);
  // Simulate an accidental reset + a new transaction.
  db.store.delete(`users/${U}/max_tracker/shifts`);
  db.store.delete(`users/${U}/max_tracker/finance/transactions/1`);
  db.store.set(`users/${U}/max_tracker/finance/transactions/3`, { id: 3, amount: 7 });
  db.store.get(`users/${U}/max_tracker/finance`).wallets = [];
  const r = await backup.restoreBackup(db, U, id, 5000, fromMillis);
  assert.equal(r.txCount, 2);
  assert.ok(r.safetyBackupId);
  assert.deepEqual(db.store.get(`users/${U}/max_tracker/shifts`).data, { '2026-09-01': ['day'] });
  assert.equal(db.store.get(`users/${U}/max_tracker/finance`).wallets[0].name, 'Картка');
  assert.equal(db.store.get(`users/${U}/max_tracker/finance`).updatedAt, 5000);
  assert.equal(db.store.get(`users/${U}/max_tracker/finance/transactions/1`).comment, 'кава');
  assert.equal(db.store.has(`users/${U}/max_tracker/finance/transactions/3`), false);
  assert.equal(db.store.get(`users/${U}/backups/${r.safetyBackupId}`).reason, 'pre_restore');
  // And the restore is itself undoable: restoring the safety backup brings tx 3 back.
  await backup.restoreBackup(db, U, r.safetyBackupId, 6000, fromMillis);
  assert.equal(db.store.get(`users/${U}/max_tracker/finance/transactions/3`).amount, 7);
});

await test('Firestore Timestamps survive a round-trip', async () => {
  const ts = { toMillis: () => 123456 };
  const db = fakeDb({ [`users/${U}/max_tracker/debt`]: { debts: [], createdAt: 0 } });
  // structuredClone drops methods, so seed the Timestamp-like via get() override.
  const orig = db.doc;
  db.doc = (p) => {
    const r = orig(p);
    if (p.endsWith('/max_tracker/debt')) {
      const g = r.get;
      r.get = async () => {
        const s = await g();
        return { ...s, data: () => { const d = s.data(); if (d) d.createdAt = ts; return d; } };
      };
    }
    return r;
  };
  const { id } = await backup.createBackup(db, U, 'default', 'manual', 10);
  await backup.restoreBackup(db, U, id, 20, fromMillis);
  assert.deepEqual(db.store.get(`users/${U}/max_tracker/debt`).createdAt, { restoredTs: 123456 });
});

await test('restore rejects unknown/foreign ids and deleted profiles', async () => {
  const db = fakeDb(base('p_x'));
  await assert.rejects(backup.restoreBackup(db, U, '../evil', 1, fromMillis), { code: 'invalid-argument' });
  await assert.rejects(backup.restoreBackup(db, U, 'nope_1', 1, fromMillis), { code: 'not-found' });
  // p_x isn't in profiles_meta -> can't restore into it.
  const { id } = await backup.createBackup(db, U, 'p_x', 'manual', 1);
  await assert.rejects(backup.restoreBackup(db, U, id, 2, fromMillis), { code: 'failed-precondition' });
});

await test('restore refuses a tampered/incomplete backup', async () => {
  const db = fakeDb(base());
  const { id } = await backup.createBackup(db, U, 'default', 'manual', 1);
  db.store.get(`users/${U}/backups/${id}`).hash = 'x';
  await assert.rejects(backup.restoreBackup(db, U, id, 2, fromMillis), { code: 'data-loss' });
  db.store.delete(`users/${U}/backups/${id}/chunks/0`);
  await assert.rejects(backup.restoreBackup(db, U, id, 2, fromMillis), { code: 'data-loss' });
});

await test('idsToPrune keeps 7 newest + newest per week for 4 older weeks', () => {
  const DAY = 24 * 3600 * 1000;
  const now = 100 * DAY;
  const entries = [];
  for (let d = 0; d < 60; d++) entries.push({ id: `b${d}`, meta: { createdAt: now - d * DAY } });
  const pruned = new Set(backup.idsToPrune(entries, now));
  const kept = entries.filter((e) => !pruned.has(e.id));
  assert.equal(kept.length, backup.KEEP_RECENT + backup.KEEP_WEEKLY);
  for (let d = 0; d < 7; d++) assert.ok(!pruned.has(`b${d}`));
  assert.ok(kept.every((e) => now - e.meta.createdAt <= 35 * DAY));
});

await test('backupAccount covers own profiles, skips shared refs, drops old orphans', async () => {
  const DAY = 24 * 3600 * 1000;
  const db = fakeDb({
    ...base(),
    ...base('p_2'),
    [`users/${U}/max_tracker/profiles_meta`]: { list: [{ id: 'default' }, { id: 'p_2' }, { id: 'p_sh', kind: 'shared', ownerUid: 'other' }] },
    [`users/${U}/backups/gone_1`]: { profileId: 'gone', createdAt: 1, reason: 'daily', chunks: 0 },
  });
  const r = await backup.backupAccount(db, U, 40 * DAY);
  assert.deepEqual(r, { default: 'created', p_2: 'created' });
  assert.equal(db.store.has(`users/${U}/backups/gone_1`), false);
  assert.ok(!Object.keys(r).includes('p_sh'));
});

console.log(`\n${passed} backup tests passed`);
