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

async function sendPush(token, title, body) {
  try {
    await messaging.send({ token, notification: { title, body }, webpush: { fcmOptions: { link: '/' } } });
    return true;
  } catch (err) {
    logger.warn('push send failed', { code: err.code, message: err.message });
    return false;
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

  // 1. Daily reminder — hasn't logged anything today, past their set time.
  if (notif.enabled && sentDaily !== today) {
    const [h] = String(notif.time || '21:00').split(':').map(Number);
    const hasTodayTx = transactions.some((t) => t.date === today);
    if (!hasTodayTx && localHour >= (h || 21)) {
      if (await sendPush(token, 'Zminka', 'Не забудь записати сьогоднішні операції.')) {
        updates.sentDaily = today;
      }
    }
  }

  // 2. Budget exceeded — once per category per month.
  if (notif.budgetAlerts) {
    const sent = { ...(sentBudget || {}) };
    let changed = false;
    for (const [cat, limit] of Object.entries(budgets)) {
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
        if (await sendPush(token, 'Бюджет перевищено', `Витрати за категорією "${cat}" перевищили місячний бюджет.`)) {
          sent[key] = true;
          changed = true;
        }
      }
    }
    if (changed) updates.sentBudget = sent;
  }

  // 3. Upcoming recurring payment — one day ahead of auto-add.
  if (notif.recurringAlerts) {
    const sent = { ...(sentRecurring || {}) };
    let changed = false;
    for (const r of recurring) {
      if (r.active === false || r.nextDate !== tomorrow || !r.amount) continue;
      const key = `${r.id}_${tomorrow}`;
      if (sent[key]) continue;
      const amountStr = `${r.amount.toLocaleString('uk-UA')} ${walletCurrency(r.wallet)}`;
      if (await sendPush(token, 'Наближається платіж', `Завтра автоматично додасться операція "${r.category || 'Інше'}" на ${amountStr}.`)) {
        sent[key] = true;
        changed = true;
      }
    }
    if (changed) updates.sentRecurring = sent;
  }

  return Object.keys(updates).length ? updates : null;
}

// Runs hourly. notif.time is a plain "HH:MM" the client captured from the
// device's local clock; notif.timeZone (IANA zone name, e.g. "Europe/Kyiv")
// is captured alongside it whenever notification settings are saved, and
// sweepProfile() uses it to compute "today"/"tomorrow"/the current month and
// the reminder hour in the user's own local time instead of this function's
// UTC clock. Accounts whose settings predate the timeZone field fall back
// to UTC (best-effort, not to-the-minute precise) until they next touch
// their notification settings, at which point the client backfills it.
exports.notificationSweep = onSchedule('every 60 minutes', async () => {
  const tokensSnap = await db.collection('push_tokens').get();
  if (tokensSnap.empty) return;

  const now = new Date();

  for (const tokenDoc of tokensSnap.docs) {
    const uid = tokenDoc.id;
    const data = tokenDoc.data();
    const { token, sentDaily, sentBudget, sentRecurring, profileState } = data;
    if (!token) continue;

    // profiles_meta lists every profile on the account (including
    // 'default'); accounts that never created extra profiles just have the
    // one default entry, so single-profile users are swept exactly as before.
    const metaSnap = await db.doc(`users/${uid}/${DCOL}/profiles_meta`).get();
    const profileIds = metaSnap.exists && Array.isArray(metaSnap.data().list) && metaSnap.data().list.length
      ? metaSnap.data().list.map((p) => p.id).filter(Boolean)
      : ['default'];

    const nextProfileState = { ...(profileState || {}) };
    let anyChange = false;

    for (const profileId of profileIds) {
      // Pre-existing token docs kept their dedup fields flat (sentDaily/
      // sentBudget/sentRecurring) because there was only ever one profile.
      // Fall back to those for 'default' until this sweep has written a
      // profileState.default at least once — no migration step needed.
      const prevState = profileState?.[profileId]
        ?? (profileId === 'default' ? { sentDaily, sentBudget, sentRecurring } : {});

      const profileUpdates = await sweepProfile(uid, profileId, token, prevState, now);
      if (profileUpdates) {
        nextProfileState[profileId] = { ...prevState, ...profileUpdates };
        anyChange = true;
      }
    }

    if (anyChange) {
      await tokenDoc.ref.set({ profileState: nextProfileState }, { merge: true });
    }
  }
});
