// Core notificationSweep logic, split out of index.js so it's unit-testable
// without firebase-admin/firebase-functions credentials or even a
// `node_modules` install — same "zero required install" property as
// pure.js (see that file's header) — but these two need `db`/`sendPushFn`
// passed in explicitly (rather than pure) since they do real Firestore
// reads/writes and FCM sends; index.js supplies the real Firestore/FCM
// clients and its own `firebase-functions/logger`, a test supplies fakes
// and a plain no-op/console logger.
const { toBase, zonedDateParts, todayStr, monthPrefix } = require('./pure');

const DCOL = 'max_tracker';

// Financial doc name for a given profile id, mirroring the client's
// userDoc() in index.html: the default profile keeps the unsuffixed name,
// any other profile reads "finance@<profileId>".
function financeDocName(profileId) {
  return profileId && profileId !== 'default' ? `finance@${profileId}` : 'finance';
}

// Runs one profile's three checks (daily reminder, budget exceeded, upcoming
// recurring payment) against its own dedup state, and returns the updated
// per-profile state to merge back onto the token doc. `financeSnap` is
// optional: sweepToken() already has the default profile's snapshot on hand
// from its own upfront parallel fetch, so it passes that through here
// instead of this function re-fetching the same document.
async function sweepProfile(db, sendPushFn, uid, profileId, token, prevState, now, financeSnap) {
  if (!financeSnap) financeSnap = await db.doc(`users/${uid}/${DCOL}/${financeDocName(profileId)}`).get();
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
    // `h || 21` would be wrong here: a reminder time of exactly midnight
    // ("00:00") parses to h=0, and 0 is falsy in JS, so `h || 21` would
    // silently treat a midnight reminder as 21:00 instead. Number.isFinite
    // respects an explicit 0 while still falling back to 21 for a genuinely
    // missing/malformed hour.
    const [hRaw] = String(notif.time || '21:00').split(':').map(Number);
    const h = Number.isFinite(hRaw) ? hRaw : 21;
    const hasTodayTx = transactions.some((t) => t.date === today);
    if (!hasTodayTx && localHour >= h) {
      const res = await sendPushFn(token, 'Zminka', 'Не забудь записати сьогоднішні операції.');
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
        const res = await sendPushFn(token, 'Бюджет перевищено', `Витрати за категорією "${cat}" перевищили місячний бюджет.`);
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
      const res = await sendPushFn(token, 'Наближається платіж', `Завтра автоматично додасться операція "${r.category || 'Інше'}" на ${amountStr}.`);
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
async function sweepToken(db, sendPushFn, tokenDoc, now, logFn = () => {}) {
  const uid = tokenDoc.id;
  const data = tokenDoc.data();
  const { token, sentDaily, sentBudget, sentRecurring, profileState } = data;
  if (!token) return;

  // profiles_meta lists every profile on the account (including 'default');
  // accounts that never created extra profiles just have the one default
  // entry, so single-profile users are swept exactly as before. Every
  // account has at least the default profile regardless of what
  // profiles_meta says, so its finance doc is fetched concurrently with
  // profiles_meta rather than waiting on it first — cuts this function's
  // per-user latency roughly in half for the common (single-profile) case,
  // which is what actually bounds how many users a single notificationSweep
  // invocation can get through before its own execution timeout as the
  // user base grows (each doc read here was previously a fully sequential
  // round trip, one profile at a time).
  const [metaSnap, defaultFinanceSnap] = await Promise.all([
    db.doc(`users/${uid}/${DCOL}/profiles_meta`).get(),
    db.doc(`users/${uid}/${DCOL}/${financeDocName('default')}`).get(),
  ]);
  const profileIds = metaSnap.exists && Array.isArray(metaSnap.data().list) && metaSnap.data().list.length
    ? metaSnap.data().list.map((p) => p.id).filter(Boolean)
    : ['default'];

  // For accounts with more than one profile, fetch every *other* profile's
  // finance doc in parallel too (rather than one at a time inside the loop
  // below) — the loop itself still runs sequentially since a dead token
  // found partway through must stop the remaining profiles, but there's no
  // reason the reads themselves can't all be in flight together first.
  const otherProfileIds = profileIds.filter((id) => id !== 'default');
  const otherFinanceSnaps = await Promise.all(
    otherProfileIds.map((id) => db.doc(`users/${uid}/${DCOL}/${financeDocName(id)}`).get()),
  );
  const financeSnapByProfile = { default: defaultFinanceSnap };
  otherProfileIds.forEach((id, i) => { financeSnapByProfile[id] = otherFinanceSnaps[i]; });

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

    const { updates: profileUpdates, tokenInvalid: invalid } = await sweepProfile(
      db, sendPushFn, uid, profileId, token, prevState, now, financeSnapByProfile[profileId],
    );
    if (profileUpdates) {
      // prevState's legacy fallback ({sentDaily, sentBudget, sentRecurring}
      // destructured straight off the token doc) very often has one or two
      // of those three as `undefined` — a brand-new token doc, or one that
      // simply never triggered a budget/recurring alert before, has no
      // such field at all. Firestore's Admin SDK throws on write for any
      // explicit `undefined` value anywhere in the payload (confirmed
      // against a real Firestore emulator, not just reasoned about — a
      // hand-rolled fake db used during development didn't validate this
      // and let it slip through silently). Stripping undefined keys here
      // is required for this write to ever succeed for a fresh token.
      nextProfileState[profileId] = Object.fromEntries(
        Object.entries({ ...prevState, ...profileUpdates }).filter(([, v]) => v !== undefined),
      );
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
    logFn('deleted invalid push token', { uid });
    return;
  }

  if (anyChange) {
    await tokenDoc.ref.set({ profileState: nextProfileState }, { merge: true });
  }
}

module.exports = { DCOL, financeDocName, sweepProfile, sweepToken };
