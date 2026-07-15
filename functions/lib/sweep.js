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

// Same suffixing convention, for the separate "debt" doc (debts/dueDate
// don't live in the finance doc — see CLAUDE.md's Firestore data model).
function debtDocName(profileId) {
  return profileId && profileId !== 'default' ? `debt@${profileId}` : 'debt';
}

// Pure determination of whether a profile's daily "log something today"
// reminder should fire this run, with no send attempted — factored out so
// sweepToken's consolidation pre-pass (see that function's own comment for
// why it needs this) asks exactly the same question sweepProfile's own
// step 1 does below, rather than two copies of this logic silently
// drifting apart over time.
function dailyReminderNeeded(financeSnap, transactionsSnap, prevState, now) {
  if (!financeSnap.exists) return { needed: false, today: null };
  const f = financeSnap.data();
  const notif = f.notifSettings || {};
  if (!notif.enabled) return { needed: false, today: null };
  const timeZone = notif.timeZone || 'UTC';
  const today = todayStr(now, timeZone);
  if (prevState.sentDaily === today) return { needed: false, today };
  const transactions = (transactionsSnap && !transactionsSnap.empty)
    ? transactionsSnap.docs.map((d) => d.data())
    : (Array.isArray(f.data) ? f.data : []);
  const localHour = zonedDateParts(now, timeZone).hour;
  // `h || 21` would be wrong here: a reminder time of exactly midnight
  // ("00:00") parses to h=0, and 0 is falsy in JS, so `h || 21` would
  // silently treat a midnight reminder as 21:00 instead. Number.isFinite
  // respects an explicit 0 while still falling back to 21 for a genuinely
  // missing/malformed hour.
  const [hRaw] = String(notif.time || '21:00').split(':').map(Number);
  const h = Number.isFinite(hRaw) ? hRaw : 21;
  const hasTodayTx = transactions.some((t) => t.date === today);
  return { needed: !hasTodayTx && localHour >= h, today };
}

// Runs one profile's four checks (daily reminder, budget exceeded, upcoming
// recurring payment, upcoming debt due date) against its own dedup state,
// and returns the updated per-profile state to merge back onto the token
// doc. `financeSnap`/`debtSnap` are optional: sweepToken() already has the
// default profile's snapshots on hand from its own upfront parallel fetch,
// so it passes those through here instead of this function re-fetching the
// same documents. `transactionsSnap` (also optional) is the finance doc's
// `transactions` subcollection QuerySnapshot — see
// MIGRATION_PLAN_transactions.md / CLAUDE.md's Firebase data model section.
// Falls back to the legacy finance.data array whenever transactionsSnap is
// missing/empty, so a caller (or an existing test) that doesn't pass one
// still works exactly as before against pre-migration data.
// `skipDaily` (default false): set by sweepToken when it has already
// covered this profile's daily reminder in one consolidated push alongside
// other profiles on the same device/token (see sweepToken's own comment) —
// every other caller (including every existing direct sweepProfile() call
// in this file's tests) omits it and gets the original single-profile
// behavior unchanged.
async function sweepProfile(db, sendPushFn, uid, profileId, token, prevState, now, financeSnap, debtSnap, transactionsSnap, skipDaily = false) {
  if (!financeSnap) financeSnap = await db.doc(`users/${uid}/${DCOL}/${financeDocName(profileId)}`).get();
  if (!financeSnap.exists) return null;
  const f = financeSnap.data();
  const notif = f.notifSettings || {};
  const transactions = (transactionsSnap && !transactionsSnap.empty)
    ? transactionsSnap.docs.map((d) => d.data())
    : (Array.isArray(f.data) ? f.data : []);
  const wallets = Array.isArray(f.wallets) ? f.wallets : [];
  const budgets = f.budgets || {};
  const categories = f.categories || {};
  const recurring = Array.isArray(f.recurring) ? f.recurring : [];
  const rates = f.currencyRates || {};
  const walletCurrency = (id) => (wallets.find((w) => w.id === id) || {}).currency || 'UAH';
  if (!debtSnap) debtSnap = await db.doc(`users/${uid}/${DCOL}/${debtDocName(profileId)}`).get();
  const debts = debtSnap.exists && Array.isArray(debtSnap.data().debts) ? debtSnap.data().debts : [];

  // Each profile carries its own timeZone (captured client-side via
  // Intl.DateTimeFormat().resolvedOptions().timeZone whenever notification
  // settings are saved) — "tomorrow"/the current-month window need to be
  // computed in *that* zone, not the Cloud Function's own UTC clock (the
  // daily reminder's own "today"/reminder-hour math lives inside
  // dailyReminderNeeded() now, computed independently the same way).
  const timeZone = notif.timeZone || 'UTC';
  const tomorrow = todayStr(new Date(now.getTime() + 86400000), timeZone);
  const mPrefix = monthPrefix(now, timeZone);

  const { sentBudget, sentRecurring, sentDebt } = prevState;
  const updates = {};
  // Once a send reports the token as permanently dead, stop attempting
  // further sends for the rest of this profile's checks (and the caller
  // stops for any remaining profiles too) — no point re-hitting FCM with a
  // token it's already told us it will never accept again.
  let tokenInvalid = false;

  // 1. Daily reminder — hasn't logged anything today, past their set time.
  // Skipped entirely when sweepToken already sent one consolidated push
  // covering this profile (see that function's own comment) — recomputing
  // dailyReminderNeeded() here anyway (rather than trusting the caller
  // blindly) means a stale/wrong skipDaily flag can never cause a send
  // this profile didn't actually need, only ever suppress one it did.
  if (!skipDaily) {
    const daily = dailyReminderNeeded(financeSnap, transactionsSnap, prevState, now);
    if (daily.needed) {
      const res = await sendPushFn(token, 'Zminka', 'Не забудь записати сьогоднішні операції.', 'daily');
      if (res.ok) updates.sentDaily = daily.today;
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
        const res = await sendPushFn(token, 'Бюджет перевищено', `Витрати за категорією "${cat}" перевищили місячний бюджет.`, 'budget');
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
      const res = await sendPushFn(token, 'Наближається платіж', `Завтра автоматично додасться операція "${r.category || 'Інше'}" на ${amountStr}.`, 'recurring');
      if (res.ok) { sent[key] = true; changed = true; }
      if (res.invalid) tokenInvalid = true;
    }
    if (changed) updates.sentRecurring = sent;
  }

  // 4. Upcoming debt due date — one day ahead, same convention as recurring.
  if (!tokenInvalid && notif.debtAlerts) {
    const sent = { ...(sentDebt || {}) };
    let changed = false;
    for (const d of debts) {
      if (tokenInvalid) break;
      if (d.dueDate !== tomorrow) continue;
      const key = `${d.id}_${tomorrow}`;
      if (sent[key]) continue;
      const res = await sendPushFn(token, 'Наближається термін боргу', `Завтра настає дата, до якої треба віддати "${d.name || 'Розрахунок'}".`, 'debt');
      if (res.ok) { sent[key] = true; changed = true; }
      if (res.invalid) tokenInvalid = true;
    }
    if (changed) updates.sentDebt = sent;
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
  const [metaSnap, defaultFinanceSnap, defaultDebtSnap, defaultTransactionsSnap] = await Promise.all([
    db.doc(`users/${uid}/${DCOL}/profiles_meta`).get(),
    db.doc(`users/${uid}/${DCOL}/${financeDocName('default')}`).get(),
    db.doc(`users/${uid}/${DCOL}/${debtDocName('default')}`).get(),
    db.collection(`users/${uid}/${DCOL}/${financeDocName('default')}/transactions`).get(),
  ]);
  const profileIds = metaSnap.exists && Array.isArray(metaSnap.data().list) && metaSnap.data().list.length
    ? metaSnap.data().list.map((p) => p.id).filter(Boolean)
    : ['default'];

  // For accounts with more than one profile, fetch every *other* profile's
  // finance/debt docs in parallel too (rather than one at a time inside the
  // loop below) — the loop itself still runs sequentially since a dead
  // token found partway through must stop the remaining profiles, but
  // there's no reason the reads themselves can't all be in flight together
  // first.
  const otherProfileIds = profileIds.filter((id) => id !== 'default');
  const [otherFinanceSnaps, otherDebtSnaps, otherTransactionsSnaps] = await Promise.all([
    Promise.all(otherProfileIds.map((id) => db.doc(`users/${uid}/${DCOL}/${financeDocName(id)}`).get())),
    Promise.all(otherProfileIds.map((id) => db.doc(`users/${uid}/${DCOL}/${debtDocName(id)}`).get())),
    Promise.all(otherProfileIds.map((id) => db.collection(`users/${uid}/${DCOL}/${financeDocName(id)}/transactions`).get())),
  ]);
  const financeSnapByProfile = { default: defaultFinanceSnap };
  const debtSnapByProfile = { default: defaultDebtSnap };
  const transactionsSnapByProfile = { default: defaultTransactionsSnap };
  otherProfileIds.forEach((id, i) => {
    financeSnapByProfile[id] = otherFinanceSnaps[i];
    debtSnapByProfile[id] = otherDebtSnaps[i];
    transactionsSnapByProfile[id] = otherTransactionsSnaps[i];
  });

  const nextProfileState = { ...(profileState || {}) };
  let anyChange = false;
  let tokenInvalid = false;

  // Helper, shared by both the pre-pass below and the main loop, to fall
  // back a profile's dedup state to the token doc's own flat legacy
  // fields (pre-multi-profile token docs) exactly as sweepProfile's own
  // callers always have.
  const prevStateFor = (profileId) => profileState?.[profileId]
    ?? (profileId === 'default' ? { sentDaily, sentBudget, sentRecurring } : {});

  // Daily-reminder consolidation: sending it from inside sweepProfile()'s
  // normal per-profile pass would mean an account with 2+ profiles enabled
  // on the SAME device gets one push per profile, every one with identical
  // text — reads as a broken duplicate notification, not as two distinct
  // reminders (a real report: an account with a second profile on the same
  // phone saw two literally-identical "Не забудь записати..." pushes at
  // once). Budget/recurring/debt pushes don't have this problem — their
  // text already names a specific category/amount/debt, so two profiles
  // triggering the same check type still produce two visually distinct
  // pushes, not confusing-looking duplicates — so only the daily reminder
  // gets this treatment. dailyReminderNeeded() (shared with sweepProfile's
  // own fallback for a profile *not* covered here) determines each
  // profile's need without sending anything, so exactly one push covers
  // every profile due for it, naming them by their profiles_meta name when
  // there's more than one.
  const dailyNeededProfileIds = [];
  const dailyTodayByProfile = {};
  for (const profileId of profileIds) {
    const { needed, today: profileToday } = dailyReminderNeeded(
      financeSnapByProfile[profileId], transactionsSnapByProfile[profileId], prevStateFor(profileId), now,
    );
    dailyTodayByProfile[profileId] = profileToday;
    if (needed) dailyNeededProfileIds.push(profileId);
  }
  if (dailyNeededProfileIds.length) {
    const profileNameById = Object.fromEntries(
      (metaSnap.exists && Array.isArray(metaSnap.data().list) ? metaSnap.data().list : [])
        .map((p) => [p.id, p.name]).filter(([, name]) => name),
    );
    const names = dailyNeededProfileIds.map((id) => profileNameById[id]);
    const body = dailyNeededProfileIds.length > 1 && names.every(Boolean)
      ? `Не забудь записати сьогоднішні операції: ${names.join(', ')}.`
      : 'Не забудь записати сьогоднішні операції.';
    const res = await sendPushFn(token, 'Zminka', body, 'daily');
    if (res.invalid) {
      tokenInvalid = true;
    } else if (res.ok) {
      dailyNeededProfileIds.forEach((id) => {
        nextProfileState[id] = { ...prevStateFor(id), ...(nextProfileState[id] || {}), sentDaily: dailyTodayByProfile[id] };
      });
      anyChange = true;
    }
    // On a non-fatal send failure (res.ok===false, res.invalid===false):
    // deliberately mark nothing sent and don't fall back to a per-profile
    // retry within this same run — every other check in this file treats a
    // failed send the same way (next hour's sweep just naturally retries,
    // since sentDaily/sentBudget/etc. was never updated), so this stays
    // consistent with that instead of being a special case.
  }

  if (!tokenInvalid) {
    for (const profileId of profileIds) {
      const prevState = prevStateFor(profileId);

      const { updates: profileUpdates, tokenInvalid: invalid } = await sweepProfile(
        db, sendPushFn, uid, profileId, token, prevState, now, financeSnapByProfile[profileId], debtSnapByProfile[profileId], transactionsSnapByProfile[profileId],
        dailyNeededProfileIds.includes(profileId), // skipDaily — already covered by the consolidated push above
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
        // `nextProfileState[profileId]` is spread in ahead of `prevState`'s
        // own values so a sentDaily the consolidated pass above already
        // wrote for this profile survives this merge instead of being
        // clobbered back to its pre-run value.
        nextProfileState[profileId] = Object.fromEntries(
          Object.entries({ ...prevState, ...(nextProfileState[profileId] || {}), ...profileUpdates }).filter(([, v]) => v !== undefined),
        );
        anyChange = true;
      }
      if (invalid) { tokenInvalid = true; break; }
    }
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

module.exports = { DCOL, financeDocName, debtDocName, sweepProfile, sweepToken };
