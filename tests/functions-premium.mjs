// Plain-node test for functions/lib/premium.js against an in-memory Firestore
// fake and a stubbed Play API. Run: node tests/functions-premium.mjs
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const { accountIdFor, evaluateSubscription, entitlementFor, verifyPurchase } = require('../functions/lib/premium.js');

function fakeDb() {
  const docs = new Map();
  return {
    docs,
    doc: (path) => ({
      get: async () => ({ exists: docs.has(path), get: (k) => docs.get(path)?.[k], data: () => docs.get(path) }),
      set: async (data, opts) => { docs.set(path, opts?.merge ? { ...(docs.get(path) || {}), ...data } : data); },
    }),
  };
}

const now = Date.parse('2026-11-03T12:00:00Z');
const later = '2026-12-03T12:00:00Z';
const sub = (over = {}) => ({
  subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
  lineItems: [{ productId: 'rytm_premium', expiryTime: later, offerDetails: { basePlanId: 'monthly' } }],
  externalAccountIdentifiers: { obfuscatedExternalAccountId: accountIdFor('u1') },
  ...over,
});

// evaluateSubscription
assert.equal(evaluateSubscription(sub(), 'u1', now).premium, true);
assert.equal(evaluateSubscription(sub(), 'u1', now).basePlan, 'monthly');
assert.equal(evaluateSubscription(sub({ subscriptionState: 'SUBSCRIPTION_STATE_CANCELED' }), 'u1', now).premium, true, 'canceled keeps the paid period');
assert.equal(evaluateSubscription(sub({ subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED' }), 'u1', now).premium, false);
assert.equal(evaluateSubscription(sub({ subscriptionState: 'SUBSCRIPTION_STATE_ON_HOLD' }), 'u1', now).premium, false);
assert.equal(evaluateSubscription(sub({ lineItems: [{ productId: 'rytm_premium', expiryTime: '2026-11-01T00:00:00Z' }] }), 'u1', now).premium, false, 'past expiry');
assert.equal(evaluateSubscription(sub({ externalAccountIdentifiers: undefined }), 'u1', now).premium, true, 'no account id: token claim decides');
assert.throws(() => evaluateSubscription(sub(), 'u2', now), { code: 'permission-denied' });
assert.throws(() => evaluateSubscription(sub({ lineItems: [{ productId: 'other', expiryTime: later }] }), 'u1', now), { code: 'invalid-argument' });

// entitlementFor
assert.deepEqual(entitlementFor(undefined, 'Owner@Mail.com', ['owner@mail.com'], now), { premium: true, source: 'grant', expiresAt: null });
assert.equal(entitlementFor(undefined, 'x@mail.com', ['owner@mail.com'], now).premium, false);
assert.equal(entitlementFor({ premium: true, expiresAt: now + 1 }, undefined, [], now).premium, true);
assert.equal(entitlementFor({ premium: true, expiresAt: now - 1 }, undefined, [], now).premium, false, 'stored answer expires by itself');

// verifyPurchase: claim, store, refuse another account
const db = fakeDb();
const token = 'tok-1234567890';
const r1 = await verifyPurchase(db, 'u1', token, async () => sub(), now);
assert.equal(r1.premium, true);
assert.equal(db.docs.get('entitlements/u1').premium, true);
assert.equal(db.docs.get('entitlements/u1').expiresAt, Date.parse(later));
await assert.rejects(verifyPurchase(db, 'u2', token, async () => sub({ externalAccountIdentifiers: undefined }), now), { code: 'permission-denied' });
assert.equal(db.docs.has('entitlements/u2'), false);
await assert.rejects(verifyPurchase(db, 'u1', 'x', async () => sub(), now), { code: 'invalid-argument' });
const r2 = await verifyPurchase(db, 'u1', token, async () => sub({ subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED' }), now);
assert.equal(r2.premium, false);
assert.equal(db.docs.get('entitlements/u1').premium, false, 'expiry overwrites the stored answer');

console.log('functions-premium: all passed');
