// @ts-check
// Premium purchase check against the Play Developer API (subscriptionsv2).
// The client sends the purchase token it got from Play Billing; the server
// asks Google what that token really is and keeps the answer in
// entitlements/{uid}, which only the Admin SDK can write (firestore.rules
// falls through to deny-all). A token belongs to the first account that
// claims it, so one purchase cannot unlock several accounts.
const crypto = require('crypto');

const PACKAGE_NAME = 'ua.rytm.app';
const PRODUCT_ID = 'rytm_premium';
// CANCELED keeps access until the paid period ends; ON_HOLD/PAUSED/EXPIRED do not.
const ENTITLED_STATES = new Set(['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED']);

class PremiumError extends Error {
  /** @param {string} code @param {string} message */
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

/** @param {string} s */
function sha256(s) {
  return crypto.createHash('sha256').update(s).digest('hex');
}

/**
 * What the client passes as obfuscatedAccountId (≤64 chars, no raw uid in Play).
 * @param {string} uid
 */
function accountIdFor(uid) {
  return sha256(uid);
}

/**
 * Pure: is this subscriptionsv2 answer premium for that account right now?
 * @param {any} sub Play's SubscriptionPurchaseV2
 * @param {string} uid
 * @param {number} now
 * @returns {{premium: boolean, expiresAt: number, state: string, basePlan: string | null}}
 */
function evaluateSubscription(sub, uid, now) {
  const items = (sub && sub.lineItems || []).filter((/** @type {any} */ li) => li.productId === PRODUCT_ID);
  if (!items.length) throw new PremiumError('invalid-argument', 'not a Rytm Premium purchase');
  const owner = sub.externalAccountIdentifiers && sub.externalAccountIdentifiers.obfuscatedExternalAccountId;
  if (owner && owner !== accountIdFor(uid)) throw new PremiumError('permission-denied', 'purchase belongs to another account');
  const expiresAt = Math.max(...items.map((/** @type {any} */ li) => Date.parse(li.expiryTime) || 0));
  const state = String(sub.subscriptionState || '');
  const basePlan = (items[0].offerDetails && items[0].offerDetails.basePlanId) || null;
  return { premium: ENTITLED_STATES.has(state) && expiresAt > now, expiresAt, state, basePlan };
}

/**
 * Pure: the account's premium answer from its entitlement doc and grant list.
 * @param {any} doc entitlements/{uid} data or undefined
 * @param {string | undefined} email verified sign-in e-mail
 * @param {string[]} grantEmails owner/staff e-mails with free access
 * @param {number} now
 */
function entitlementFor(doc, email, grantEmails, now) {
  if (email && grantEmails.includes(email.toLowerCase())) return { premium: true, source: 'grant', expiresAt: null };
  if (doc && doc.premium === true && typeof doc.expiresAt === 'number' && doc.expiresAt > now) {
    return { premium: true, source: 'play', expiresAt: doc.expiresAt };
  }
  return { premium: false, source: null, expiresAt: doc && typeof doc.expiresAt === 'number' ? doc.expiresAt : null };
}

/**
 * Checks one purchase token with Google and stores the result.
 * @param {any} db Firestore (Admin)
 * @param {string} uid
 * @param {string} purchaseToken
 * @param {(token: string) => Promise<any>} fetchSubscription Play API call (injected for tests)
 * @param {number} now
 */
async function verifyPurchase(db, uid, purchaseToken, fetchSubscription, now) {
  if (typeof purchaseToken !== 'string' || purchaseToken.length < 10 || purchaseToken.length > 4096) {
    throw new PremiumError('invalid-argument', 'bad purchase token');
  }
  const claimRef = db.doc(`purchase_tokens/${sha256(purchaseToken)}`);
  const claim = await claimRef.get();
  if (claim.exists && claim.get('uid') !== uid) throw new PremiumError('permission-denied', 'purchase belongs to another account');
  const result = evaluateSubscription(await fetchSubscription(purchaseToken), uid, now);
  if (!claim.exists) await claimRef.set({ uid, createdAt: now });
  await db.doc(`entitlements/${uid}`).set({
    premium: result.premium, expiresAt: result.expiresAt, state: result.state, basePlan: result.basePlan, updatedAt: now,
  }, { merge: true });
  return result;
}

/**
 * The real Play Developer API call, with the function's own service account.
 * That account must be added in Play Console → Users and permissions.
 * @param {string} token
 */
async function fetchSubscriptionFromPlay(token) {
  const { GoogleAuth } = require('google-auth-library');
  const client = await new GoogleAuth({ scopes: ['https://www.googleapis.com/auth/androidpublisher'] }).getClient();
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PACKAGE_NAME}/purchases/subscriptionsv2/tokens/${encodeURIComponent(token)}`;
  const res = await client.request({ url });
  return res.data;
}

module.exports = { PremiumError, accountIdFor, evaluateSubscription, entitlementFor, verifyPurchase, fetchSubscriptionFromPlay, PRODUCT_ID };
