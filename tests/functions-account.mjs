// Plain-node test for functions/lib/account.js's deleteAccountData() against
// a tiny in-memory Firestore fake (paths → data). Run: node tests/functions-account.mjs
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const { deleteAccountData } = require('../functions/lib/account.js');

function fakeDb(seed) {
  const docs = new Map(Object.entries(seed));
  const snap = (path) => ({
    exists: docs.has(path),
    get: (k) => docs.get(path)?.[k],
    ref: docRef(path),
  });
  function docRef(path) {
    return {
      path,
      get: async () => snap(path),
      set: async (data, opts) => { docs.set(path, opts?.merge ? { ...(docs.get(path) || {}), ...data } : data); },
      delete: async () => { docs.delete(path); },
    };
  }
  return {
    docs,
    doc: docRef,
    collection: (col) => ({
      path: col,
      limit: () => ({ get: async () => ({ empty: ![...docs.keys()].some((p) => p.startsWith(col + '/')) }) }),
      where: (field, _op, value) => ({
        get: async () => ({
          docs: [...docs.keys()]
            .filter((p) => p.startsWith(col + '/') && p.split('/').length === 2 && docs.get(p)[field] === value)
            .map(snap),
        }),
      }),
    }),
    recursiveDelete: async (ref) => {
      for (const p of [...docs.keys()]) if (p === ref.path || p.startsWith(ref.path + '/')) docs.delete(p);
    },
  };
}

const uid = 'u1';
const db = fakeDb({
  [`users/${uid}/max_tracker/profiles_meta`]: { list: [{ id: 'default' }, { id: 'p2' }, { id: 'sh', kind: 'shared', ownerUid: 'o1' }] },
  [`users/${uid}/max_tracker/finance`]: { a: 1 },
  [`users/${uid}/max_tracker/finance@p2`]: { a: 1 },
  ...Object.fromEntries(Array.from({ length: 700 }, (_, i) => [`users/${uid}/max_tracker/finance/transactions/t${i}`, { id: i }])),
  [`users/${uid}/backups/default_1`]: { x: 1 },
  [`push_tokens/${uid}`]: { token: 't' },
  [`error_reports/${uid}`]: { entries: [] },
  'profile_invites/ABC': { ownerUid: uid },
  'profile_invites/KEEP': { ownerUid: 'other' },
  'users/o1/max_tracker/shared_members@sh': { members: ['o1', uid], roles: { [uid]: 'viewer' } },
  'users/o1/max_tracker/finance@sh': { keep: true },
  'users/u2/max_tracker/finance': { keep: true },
});

const res1 = await deleteAccountData(db, uid);
assert.equal(res1.keepAuth, false, 'no other app data -> Auth user may go');

const left = [...db.docs.keys()];
assert.ok(!left.some((p) => p.startsWith(`users/${uid}/`)), 'every doc under the account is gone (incl. 700 tx, other profiles, backups)');
assert.ok(!db.docs.has(`push_tokens/${uid}`) && !db.docs.has(`error_reports/${uid}`), 'push token and error log deleted');
assert.ok(!db.docs.has('profile_invites/ABC') && db.docs.has('profile_invites/KEEP'), 'only own invites deleted');
assert.deepEqual(db.docs.get('users/o1/max_tracker/shared_members@sh').members, ['o1'], 'removed from joined shared profile');
assert.deepEqual(db.docs.get('users/o1/max_tracker/shared_members@sh').roles, {}, 'role entry removed');
assert.ok(db.docs.has('users/o1/max_tracker/finance@sh') && db.docs.has('users/u2/max_tracker/finance'), "other accounts' data untouched");

// AUDIT_2026-10 P2-03: another app of this project (DoRytm, Vchasno) shares the sign-in.
const db2 = fakeDb({
  'users/u3/max_tracker/finance': { a: 1 },
  'users/u3/max_tracker/finance/transactions/t1': { id: 1 },
  'users/u3/backups/default_1': { x: 1 },
  'users/u3/habits/h1': { name: 'run' },
  'users/u3/tasks/k1': { name: 'buy' },
});
const res2 = await deleteAccountData(db2, 'u3');
assert.equal(res2.keepAuth, true, 'other app data -> keep the shared Auth user');
assert.ok(!db2.docs.has('users/u3/max_tracker/finance') && !db2.docs.has('users/u3/max_tracker/finance/transactions/t1') && !db2.docs.has('users/u3/backups/default_1'), 'Rytm data gone');
assert.ok(db2.docs.has('users/u3/habits/h1') && db2.docs.has('users/u3/tasks/k1'), 'DoRytm habits/tasks untouched');

const db3 = fakeDb({ 'users/u4/max_tracker/finance': { a: 1 }, 'vchasnoUsers/u4': { data: '{}' } });
assert.equal((await deleteAccountData(db3, 'u4')).keepAuth, true, 'Vchasno backup -> keep Auth');
assert.ok(db3.docs.has('vchasnoUsers/u4') && !db3.docs.has('users/u4/max_tracker/finance'), 'Vchasno data untouched, Rytm data gone');
console.log('functions-account: OK');
