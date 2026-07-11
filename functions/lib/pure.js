// Pure, side-effect-free helpers shared by functions/index.js — split out
// so they can be unit-tested (tests/unit.mjs) without requiring
// firebase-admin credentials, which index.js's initializeApp() needs at
// module-load time.

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

// Resolves a moment in time to the calendar date + hour it represents in a
// given IANA timeZone (falls back to UTC for a missing/invalid zone, e.g. a
// notifSettings.timeZone written before this field existed, or corrupted by
// a direct Firestore write — Firestore rules only check uid ownership, not
// field validity, so this must not throw and take the whole sweep down).
function zonedDateParts(date, timeZone) {
  try {
    const fmt = new Intl.DateTimeFormat('en-US', {
      timeZone: timeZone || 'UTC', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', hourCycle: 'h23',
    });
    const parts = Object.fromEntries(fmt.formatToParts(date).map((p) => [p.type, p.value]));
    return { dateStr: `${parts.year}-${parts.month}-${parts.day}`, hour: Number(parts.hour) };
  } catch (err) {
    if (timeZone === 'UTC') throw err;
    return zonedDateParts(date, 'UTC');
  }
}
function todayStr(d, timeZone) {
  return zonedDateParts(d, timeZone).dateStr;
}
function monthPrefix(d, timeZone) {
  return zonedDateParts(d, timeZone).dateStr.slice(0, 7);
}

module.exports = { SEED_RATES, convertCurrency, toBase, zonedDateParts, todayStr, monthPrefix };
