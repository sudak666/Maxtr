/**
 * Scheduled push-notification sweep for Zminka.
 *
 * The client already does the same three checks (daily reminder, budget
 * exceeded, upcoming recurring payment) locally via local Notifications,
 * but those only fire while the app is open in a browser tab. This
 * function re-runs the same logic server-side on a schedule and sends a
 * real FCM push, so reminders arrive even with the app fully closed.
 *
 * Dedup state (what's already been sent) is stored on each user's
 * push_tokens/{uid} doc, separate from the client's own localStorage
 * dedup keys used for the in-app local notifications.
 *
 * Deploy with: firebase deploy --only functions
 * (requires the project to be on the Blaze plan and firebase-tools logged
 * in with access to the project — see ../SETUP.md).
 */
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const logger = require('firebase-functions/logger');
const { toBase, zonedDateParts, todayStr, monthPrefix } = require('./lib/pure');

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

const DCOL = 'max_tracker';

// registration-token-not-registered means the token is permanently dead
// (app uninstalled, browser data cleared, push permission revoked) — FCM
// itself will never accept it again, so the caller should stop retrying it
// and delete the push_tokens doc rather than re-attempting (and re-logging
// the same failure) every hour indefinitely.
const TOKEN_INVALID_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);
async function sendPush(token, title, body) {
  try {
    await messaging.send({ token, notification: { title, body }, webpush: { fcmOptions: { link: '/' } } });
    return { ok: true, invalid: false };
  } catch (err) {
    logger.warn('push send failed', { code: err.code, message: err.message });
    return { ok: false, invalid: TOKEN_INVALID_CODES.has(err.code) };
  }
}

// Financial doc name for a given profile id, mirroring the client's
// userDoc() in index.html: the default profile keeps the unsuffixed name,
// any other profile reads "finance@<profileId>".
function financeDocName(profileId) {
  return profileId && profileId !== 'default' ? `finance@${profileId}` : 'finance';
}

// Runs one profile's three checks (daily reminder, budget exceeded, upcoming
// recurring payment) against its own dedup state, and returns the updated
// per-profile state to merge back onto the token doc.
async function sweepProfile(uid, profileId, token, prevState, now) {
  const financeSnap = await db.doc(`users/${uid}/${DCOL}/${financeDocName(profileId)}`).get();
  if (!financeSnap.exists) return null;
  const f = financeSnap.data();
  const notif = f.notifSettings || {};
  const transactions = Array.isArray(f.data) ? f.data : [];
  const wallets = Array.isArray(f.wallets) ? f.wallets : [];
  const budgets = f.budgets || {};
  const categories = f.categories || {};
  const recurring = Array.isArray(f.recurring) ? f.recurring : [];
  const rates = f.currencyRates || {};
  const walletCurrency = (id) => (wallets.find((w) => w.id === id) || {}).currency || 'UAH';

  // Each profile carries its own timeZone (captured client-side via
  // Intl.DateTimeFormat().resolvedOptions().timeZone whenever notification
  // settings are saved) — the "today"/"tomorrow"/current-month window and
  // the reminder hour comparison all need to be computed in *that* zone,
  // not the Cloud Function's own UTC clock, or a 21:00 reminder fires at
  // 21:00 UTC instead of 21:00 for the user.
  const timeZone = notif.timeZone || 'UTC';
  const today = todayStr(now, timeZone);
  const tomorrow = todayStr(new Date(now.getTime() + 86400000), timeZone);
  const mPrefix = monthPrefix(now, timeZone);
  const localHour = zonedDateParts(now, timeZone).hour;

  const { sentDaily, sentBudget, sentRecurring } = prevState;
  const updates = {};
  // Once a send reports the token as permanently dead, stop attempting
  // further sends for the rest of this profile's checks (and the caller
  // stops for any remaining profiles too) — no point re-hitting FCM with a
  // token it's already told us it will never accept again.
  let tokenInvalid = false;

  // 1. Daily reminder — hasn't logged anything today, past their set time.
  if (notif.enabled && sentDaily !== today) {
    const [h] = String(notif.time || '21:00').split(':').map(Number);
    const hasTodayTx = transactions.some((t) => t.date === today);
    if (!hasTodayTx && localHour >= (h || 21)) {
      const res = await sendPush(token, 'Zminka', 'Не забудь записати сьогоднішні операції.');
      if (res.ok) updates.sentDaily = today;
      if (res.invalid) tokenInvalid = true;
    }
  }

  // 2. Budget exceeded — once per category per month.
  if (!tokenInvalid && notif.budgetAlerts) {
    const sent = { ...(sentBudget || {}) };
    let changed = false;
    for (const [cat, limit] of Object.entries(budgets)) {
      if (tokenInvalid) break;
      if (!(limit > 0) || !(categories.expense || []).includes(cat)) continue;
      const key = `${mPrefix}_${cat}`;
      if (sent[key]) continue;
      const spent = transactions.reduce((s, t) => {
        if (t.type === 'expense' && t.category === cat && t.date && t.date.startsWith(mPrefix)) {
          return s + toBase(t.amount, t.currency || 'UAH', rates);
        }
        return s;
      }, 0);
      if (spent > limit) {
        const res = await sendPush(token, 'Бюджет перевищено', `Витрати за категорією "${cat}" перевищили місячний бюджет.`);
        if (res.ok) { sent[key] = true; changed = true; }
        if (res.invalid) tokenInvalid = true;
      }
    }
    if (changed) updates.sentBudget = sent;
  }

  // 3. Upcoming recurring payment — one day ahead of auto-add.
  if (!tokenInvalid && notif.recurringAlerts) {
    const sent = { ...(sentRecurring || {}) };
    let changed = false;
    for (const r of recurring) {
      if (tokenInvalid) break;
      if (r.active === false || r.nextDate !== tomorrow || !r.amount) continue;
      const key = `${r.id}_${tomorrow}`;
      if (sent[key]) continue;
      const amountStr = `${r.amount.toLocaleString('uk-UA')} ${walletCurrency(r.wallet)}`;
      const res = await sendPush(token, 'Наближається платіж', `Завтра автоматично додасться операція "${r.category || 'Інше'}" на ${amountStr}.`);
      if (res.ok) { sent[key] = true; changed = true; }
      if (res.invalid) tokenInvalid = true;
    }
    if (changed) updates.sentRecurring = sent;
  }

  return { updates: Object.keys(updates).length ? updates : null, tokenInvalid };
}

// Runs hourly. notif.time is a plain "HH:MM" the client captured from the
// device's local clock; notif.timeZone (IANA zone name, e.g. "Europe/Kyiv")
// is captured alongside it whenever notification settings are saved, and
// sweepProfile() uses it to compute "today"/"tomorrow"/the current month and
// the reminder hour in the user's own local time instead of this function's
// UTC clock. Accounts whose settings predate the timeZone field fall back
// to UTC (best-effort, not to-the-minute precise) until they next touch
// their notification settings, at which point the client backfills it.
// One token doc's full sweep (every profile on that account) — pulled out
// of the main loop so notificationSweep can run every user's sweep
// concurrently (Promise.allSettled below) instead of one uid at a time,
// which is what actually doesn't scale past a handful of users: each
// iteration was a fully sequential chain of Firestore reads/FCM sends with
// nothing overlapping.
async function sweepToken(tokenDoc, now) {
  const uid = tokenDoc.id;
  const data = tokenDoc.data();
  const { token, sentDaily, sentBudget, sentRecurring, profileState } = data;
  if (!token) return;

  // profiles_meta lists every profile on the account (including 'default');
  // accounts that never created extra profiles just have the one default
  // entry, so single-profile users are swept exactly as before.
  const metaSnap = await db.doc(`users/${uid}/${DCOL}/profiles_meta`).get();
  const profileIds = metaSnap.exists && Array.isArray(metaSnap.data().list) && metaSnap.data().list.length
    ? metaSnap.data().list.map((p) => p.id).filter(Boolean)
    : ['default'];

  const nextProfileState = { ...(profileState || {}) };
  let anyChange = false;
  let tokenInvalid = false;

  for (const profileId of profileIds) {
    // Pre-existing token docs kept their dedup fields flat (sentDaily/
    // sentBudget/sentRecurring) because there was only ever one profile.
    // Fall back to those for 'default' until this sweep has written a
    // profileState.default at least once — no migration step needed.
    const prevState = profileState?.[profileId]
      ?? (profileId === 'default' ? { sentDaily, sentBudget, sentRecurring } : {});

    const { updates: profileUpdates, tokenInvalid: invalid } = await sweepProfile(uid, profileId, token, prevState, now);
    if (profileUpdates) {
      nextProfileState[profileId] = { ...prevState, ...profileUpdates };
      anyChange = true;
    }
    if (invalid) { tokenInvalid = true; break; }
  }

  if (tokenInvalid) {
    // Permanently dead token (app uninstalled, data cleared, permission
    // revoked) — FCM will keep rejecting it forever, so stop tracking it
    // instead of re-attempting (and re-logging) the same failure hourly.
    // The client re-creates this doc itself next time it enables push.
    await tokenDoc.ref.delete();
    logger.info('deleted invalid push token', { uid });
    return;
  }

  if (anyChange) {
    await tokenDoc.ref.set({ profileState: nextProfileState }, { merge: true });
  }
}

exports.notificationSweep = onSchedule('every 60 minutes', async () => {
  const tokensSnap = await db.collection('push_tokens').get();
  if (tokensSnap.empty) return;

  const now = new Date();
  const results = await Promise.allSettled(tokensSnap.docs.map((tokenDoc) => sweepToken(tokenDoc, now)));
  results.forEach((r, i) => {
    if (r.status === 'rejected') {
      logger.error('sweepToken failed', { uid: tokensSnap.docs[i].id, error: r.reason?.message || String(r.reason) });
    }
  });
});
