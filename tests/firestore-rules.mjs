// Firestore security rules test — verifies firestore.rules against a real
// local Firestore emulator (not a static read of the rules file). Not part
// of tests/smoke.mjs or CI: it needs a JDK + the emulator jar (downloaded
// on first run) plus two npm packages this repo intentionally doesn't
// vendor (no root package.json — see CLAUDE.md). Run manually before any
// firestore.rules change:
//
//   mkdir -p /tmp/zminka-rules-test && cd /tmp/zminka-rules-test
//   npm init -y >/dev/null && npm install --no-audit --no-fund @firebase/rules-unit-testing firebase
//   npx --yes firebase-tools emulators:start --only firestore --project zminka-rules-test &
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
import { setDoc, doc, getDoc } from 'firebase/firestore';

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const rules = fs.readFileSync(path.join(ROOT, 'firestore.rules'), 'utf8');

const testEnv = await initializeTestEnvironment({
  projectId: 'zminka-rules-test',
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

console.log(`\n${passed} passed, ${failed} failed`);
await testEnv.cleanup();
if (failed > 0) process.exit(1);
