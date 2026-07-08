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

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

const DCOL = 'max_tracker';
const SEED_RATES = { USD: 41, EUR: 44, GBP: 51, PLN: 10.5 };

function convertCurrency(amount, fromCode, toCode, rates) {
  if (fromCode === toCode) return amount;
  const fromRate = rates[fromCode] ?? SEED_RATES[fromCode] ?? 1;
  const toRate = rates[toCode] ?? SEED_RATES[toCode] ?? 1;
  return (amount * fromRate) / toRate;
}
function toBase(amount, code, rates) {
  return convertCurrency(amount, code || 'UAH', 'UAH', rates);
}
function todayStr(d) {
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`;
}
function monthPrefix(d) {
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

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
async function sweepProfile(uid, profileId, token, prevState, now, today, tomorrow, mPrefix) {
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

  const { sentDaily, sentBudget, sentRecurring } = prevState;
  const updates = {};

  // 1. Daily reminder — hasn't logged anything today, past their set time.
  if (notif.enabled && sentDaily !== today) {
    const [h] = String(notif.time || '21:00').split(':').map(Number);
    const hasTodayTx = transactions.some((t) => t.date === today);
    if (!hasTodayTx && now.getUTCHours() >= (h || 21)) {
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
// device's local clock, but this sweep compares it directly against
// now.getUTCHours() — there's no stored timezone/offset per user, so a
// reminder set for e.g. 21:00 fires at 21:00 UTC, not 21:00 in whatever
// timezone the user is actually in. That's an intentional known limitation
// (best-effort daily reminder, not to-the-minute precise), not a bug to
// silently work around — fixing it means storing the user's timezone/offset
// in notifSettings and comparing against local time here instead.
exports.notificationSweep = onSchedule('every 60 minutes', async () => {
  const tokensSnap = await db.collection('push_tokens').get();
  if (tokensSnap.empty) return;

  const now = new Date();
  const today = todayStr(now);
  const tomorrow = todayStr(new Date(now.getTime() + 86400000));
  const mPrefix = monthPrefix(now);

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

      const profileUpdates = await sweepProfile(uid, profileId, token, prevState, now, today, tomorrow, mPrefix);
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
