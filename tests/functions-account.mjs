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

await deleteAccountData(db, uid);

const left = [...db.docs.keys()];
assert.ok(!left.some((p) => p.startsWith(`users/${uid}/`)), 'every doc under the account is gone (incl. 700 tx, other profiles, backups)');
assert.ok(!db.docs.has(`push_tokens/${uid}`) && !db.docs.has(`error_reports/${uid}`), 'push token and error log deleted');
assert.ok(!db.docs.has('profile_invites/ABC') && db.docs.has('profile_invites/KEEP'), 'only own invites deleted');
assert.deepEqual(db.docs.get('users/o1/max_tracker/shared_members@sh').members, ['o1'], 'removed from joined shared profile');
assert.deepEqual(db.docs.get('users/o1/max_tracker/shared_members@sh').roles, {}, 'role entry removed');
assert.ok(db.docs.has('users/o1/max_tracker/finance@sh') && db.docs.has('users/u2/max_tracker/finance'), "other accounts' data untouched");
console.log('functions-account: OK');
