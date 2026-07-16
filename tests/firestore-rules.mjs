// Firestore security rules test — verifies firestore.rules against a real
// local Firestore emulator (not a static read of the rules file). Not part
// of tests/smoke.mjs or CI: it needs a JDK + the emulator jar (downloaded
// on first run) plus two npm packages this repo intentionally doesn't
// vendor (no root package.json — see CLAUDE.md). Run manually before any
// firestore.rules change:
//
//   mkdir -p /tmp/rytm-rules-test && cd /tmp/rytm-rules-test
//   npm init -y >/dev/null && npm install --no-audit --no-fund @firebase/rules-unit-testing firebase
//   npx --yes firebase-tools emulators:start --only firestore --project rytm-rules-test &
//   node /path/to/this/repo/tests/firestore-rules.mjs
//
// This caught a real bug during development: firestore.rules had two
// `allow write` blocks for push_tokens (a broad one plus a stricter
// type-checked one) — Firestore ORs multiple `allow` statements for the
// same operation, so the broad one silently made the strict one a no-op.
// A rules change that looks correct on read-through can still be wrong;
// prefer running this over eyeballing the rules file.
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { setDoc, deleteDoc, doc, getDoc } from 'firebase/firestore';

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const rules = fs.readFileSync(path.join(ROOT, 'firestore.rules'), 'utf8');

const testEnv = await initializeTestEnvironment({
  projectId: 'rytm-rules-test',
  firestore: { rules, host: '127.0.0.1', port: 8080 },
});

let passed = 0, failed = 0;
async function check(name, promise, expect) {
  try {
    if (expect === 'allow') await assertSucceeds(promise);
    else await assertFails(promise);
    passed++;
    console.log(`[ok] ${name}`);
  } catch (err) {
    failed++;
    console.log(`[FAIL] ${name}: ${err.message}`);
  }
}

const uidA = 'userA';
const uidB = 'userB';
const asA = testEnv.authenticatedContext(uidA).firestore();
const asB = testEnv.authenticatedContext(uidB).firestore();
const anon = testEnv.unauthenticatedContext().firestore();

// 1. Legit finance doc write by owner
await check(
  'owner can write a realistic finance doc',
  setDoc(doc(asA, `users/${uidA}/max_tracker/finance`), {
    data: [], wallets: [], categories: { expense: [], income: [] }, budgets: {}, subcategories: {},
    currencyRates: {}, tags: [], autoRules: [], recurring: [], shoppingList: [], goals: [],
    profile: { nickname: 'Test', avatar: '' }, subscription: { plan: 'free', expiresAt: null },
    widgets: { rates: true, converter: true, analytics: true, chart: true, goals: true },
    widgetOrder: ['rates', 'converter'], notifSettings: { enabled: true, time: '21:00', timeZone: 'Europe/Kyiv' },
    catBackfillDone: true, catLegacyMerged: true, updatedAt: Date.now(),
  }),
  'allow'
);

// 2. Legit shifts / debt / backup_v2 / profiles_meta writes
await check('owner can write shifts doc', setDoc(doc(asA, `users/${uidA}/max_tracker/shifts`), {
  data: { '2026-07-10': ['a', 'b'] }, shiftTypes: [], autoFillSchedule: {}, updatedAt: Date.now(),
}), 'allow');
await check('owner can write debt doc', setDoc(doc(asA, `users/${uidA}/max_tracker/debt`), {
  data: { debts: [], currentDebtId: null }, updatedAt: Date.now(),
}), 'allow');
await check('owner can write backup_v2 doc', setDoc(doc(asA, `users/${uidA}/max_tracker/backup_v2`), {
  shifts: null, finance: null, debt: null, at: Date.now(),
}), 'allow');
await check('owner can write profiles_meta doc', setDoc(doc(asA, `users/${uidA}/max_tracker/profiles_meta`), {
  list: [{ id: 'default', name: 'Я', createdAt: Date.now() }], updatedAt: Date.now(),
}), 'allow');

// 3. Suffixed profile doc name (finance@profile_xyz)
await check('owner can write a suffixed profile finance doc', setDoc(doc(asA, `users/${uidA}/max_tracker/finance@profile_1a2b3c4`), {
  data: [], updatedAt: Date.now(),
}), 'allow');

// 3b. Transactions subcollection under a finance doc (see
// MIGRATION_PLAN_transactions.md / CLAUDE.md's Firebase data model section
// — this replaced the old finance.data array).
await check('owner can write a valid transaction doc', setDoc(doc(asA, `users/${uidA}/max_tracker/finance/transactions/12345`), {
  id: 12345, type: 'expense', amount: 100, currency: 'UAH', category: 'Кава', date: '2026-07-15',
}), 'allow');
await check('owner can write a transaction doc under a suffixed profile finance doc', setDoc(doc(asA, `users/${uidA}/max_tracker/finance@profile_1a2b3c4/transactions/999`), {
  id: 999, type: 'income', amount: 50, currency: 'UAH', date: '2026-07-15',
}), 'allow');
await check('other user cannot write a transaction under uidA finance doc', setDoc(doc(asB, `users/${uidA}/max_tracker/finance/transactions/12345`), {
  id: 12345, type: 'expense', amount: 100, currency: 'UAH', date: '2026-07-15',
}), 'deny');
await check('unauthenticated cannot write a transaction doc', setDoc(doc(anon, `users/${uidA}/max_tracker/finance/transactions/12345`), {
  id: 12345, type: 'expense', amount: 100, currency: 'UAH', date: '2026-07-15',
}), 'deny');
await check('transaction doc missing required amount field rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/finance/transactions/222`), {
  id: 222, type: 'expense', date: '2026-07-15',
}), 'deny');
await check('transaction doc with non-number amount rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/finance/transactions/223`), {
  id: 223, type: 'expense', amount: '100', date: '2026-07-15',
}), 'deny');
await check('transaction doc with unsupported type rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/finance/transactions/224`), {
  id: 224, type: 'refund', amount: 100, currency: 'UAH', date: '2026-07-15',
}), 'deny');
await check('transaction doc with non-positive amount rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/finance/transactions/225`), {
  id: 225, type: 'expense', amount: 0, currency: 'UAH', date: '2026-07-15',
}), 'deny');
await check('transaction doc with malformed date rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/finance/transactions/226`), {
  id: 226, type: 'expense', amount: 100, currency: 'UAH', date: '15.07.2026',
}), 'deny');
await check('transfer transaction missing target fields rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/finance/transactions/227`), {
  id: 227, type: 'transfer', amount: 100, currency: 'UAH', date: '2026-07-15', wallet: 'w1',
}), 'deny');
await check('owner can write a valid transfer transaction doc', setDoc(doc(asA, `users/${uidA}/max_tracker/finance/transactions/tx_abc-123`), {
  id: 'tx_abc-123', type: 'transfer', amount: 100, currency: 'UAH', date: '2026-07-15', wallet: 'w1', targetWallet: 'w2', targetAmount: 2.5, targetCurrency: 'USD', createdAt: Date.now(),
}), 'allow');
await check('transactions subcollection under a non-finance doc rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/shifts/transactions/1`), {
  id: 1, type: 'expense', amount: 1, date: '2026-07-15',
}), 'deny');
await check('owner can delete a transaction doc', deleteDoc(doc(asA, `users/${uidA}/max_tracker/finance/transactions/12345`)), 'allow');
await check('other user cannot delete a transaction under uidA finance doc', deleteDoc(doc(asB, `users/${uidA}/max_tracker/finance/transactions/12345`)), 'deny');

// 4. Cross-user access denied
await check('other user cannot read uidA finance doc', getDoc(doc(asB, `users/${uidA}/max_tracker/finance`)), 'deny');
await check('other user cannot write uidA finance doc', setDoc(doc(asB, `users/${uidA}/max_tracker/finance`), { updatedAt: Date.now() }), 'deny');
await check('unauthenticated cannot write finance doc', setDoc(doc(anon, `users/${uidA}/max_tracker/finance`), { updatedAt: Date.now() }), 'deny');

// 5. Bogus document name rejected
await check('bogus top-level doc name rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/totally_made_up`), { updatedAt: Date.now() }), 'deny');
await check('nested subpath under a doc rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/finance/sub/evil`), { x: 1 }), 'deny');

// 6. Malformed updatedAt rejected
await check('string updatedAt rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/finance`), { updatedAt: 'not-a-number' }), 'deny');

// 7. Oversized key count rejected
const bloated = {};
for (let i = 0; i < 100; i++) bloated[`k${i}`] = i;
await check('doc with 100 top-level keys rejected', setDoc(doc(asA, `users/${uidA}/max_tracker/finance`), bloated), 'deny');

// 8. push_tokens: valid write, invalid token, cross-user
await check('owner can write a valid push token', setDoc(doc(asA, `push_tokens/${uidA}`), { token: 'a'.repeat(150), updatedAt: Date.now() }, { merge: true }), 'allow');
await check('empty token string rejected', setDoc(doc(asA, `push_tokens/${uidA}`), { token: '', updatedAt: Date.now() }, { merge: true }), 'deny');
await check('oversized token string rejected', setDoc(doc(asA, `push_tokens/${uidA}`), { token: 'x'.repeat(5000), updatedAt: Date.now() }, { merge: true }), 'deny');
await check('non-string token rejected', setDoc(doc(asA, `push_tokens/${uidA}`), { token: 12345, updatedAt: Date.now() }, { merge: true }), 'deny');
await check('other user cannot write to uidA push token', setDoc(doc(asB, `push_tokens/${uidA}`), { token: 'x'.repeat(150), updatedAt: Date.now() }, { merge: true }), 'deny');

// 9. Simulate the Cloud Function's Admin SDK having already added
// profileState/sentDaily fields (admin context bypasses rules, matching
// production behavior of firebase-admin), then a normal client merge-write
// of just {token, updatedAt} must still succeed afterwards.
await testEnv.withSecurityRulesDisabled(async (ctx) => {
  const adminDb = ctx.firestore();
  await setDoc(doc(adminDb, `push_tokens/${uidA}`), { profileState: { default: { sentDaily: '2026-07-10' } } }, { merge: true });
});
await check('client token refresh still succeeds after admin-written fields exist', setDoc(doc(asA, `push_tokens/${uidA}`), { token: 'b'.repeat(150), updatedAt: Date.now() }, { merge: true }), 'allow');

// 10. deleteDoc() must not be blocked by the create/update field validation
// (request.resource.data is null on delete — a rule that references it in
// the same allow block as delete denies every delete unconditionally).
await check('owner can delete their finance doc', deleteDoc(doc(asA, `users/${uidA}/max_tracker/finance`)), 'allow');
await check('owner can delete their shifts doc', deleteDoc(doc(asA, `users/${uidA}/max_tracker/shifts`)), 'allow');
await check('owner can delete their debt doc', deleteDoc(doc(asA, `users/${uidA}/max_tracker/debt`)), 'allow');
await check('other user cannot delete uidA finance doc', deleteDoc(doc(asB, `users/${uidA}/max_tracker/finance`)), 'deny');
await check('unauthenticated cannot delete finance doc', deleteDoc(doc(anon, `users/${uidA}/max_tracker/finance`)), 'deny');
await check('owner can delete their push token', deleteDoc(doc(asA, `push_tokens/${uidA}`)), 'allow');
await check('other user cannot delete uidA push token', deleteDoc(doc(asB, `push_tokens/${uidA}`)), 'deny');

console.log(`\n${passed} passed, ${failed} failed`);
await testEnv.cleanup();
if (failed > 0) process.exit(1);
