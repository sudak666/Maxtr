// @ts-check
// Pure, side-effect-free helpers shared by functions/index.js — split out
// so they can be unit-tested (tests/unit.mjs) without requiring
// firebase-admin credentials, which index.js's initializeApp() needs at
// module-load time.
//
// Second file opted into TypeScript's checkJs, after js/tx-validation.js —
// see CLAUDE.md's TypeScript adoption section. This one lives under
// functions/ (a separate, CommonJS npm project — no "type":"module" in its
// package.json, unlike the ESM root project), so it's checked via its own
// functions/tsconfig.json (module:"CommonJS") rather than the root one.

/** @typedef {Record<string, number>} RatesMap */

const SEED_RATES = { USD: 41, EUR: 44, GBP: 51, PLN: 10.5 };

/**
 * @param {number} amount
 * @param {string} fromCode
 * @param {string} toCode
 * @param {RatesMap} rates
 * @returns {number}
 */
function convertCurrency(amount, fromCode, toCode, rates) {
  if (fromCode === toCode) return amount;
  const fromRate = rates[fromCode] ?? SEED_RATES[fromCode] ?? 1;
  const toRate = rates[toCode] ?? SEED_RATES[toCode] ?? 1;
  return (amount * fromRate) / toRate;
}
/**
 * @param {number} amount
 * @param {string} code
 * @param {RatesMap} rates
 * @returns {number}
 */
function toBase(amount, code, rates) {
  return convertCurrency(amount, code || 'UAH', 'UAH', rates);
}

// Resolves a moment in time to the calendar date + hour it represents in a
// given IANA timeZone (falls back to UTC for a missing/invalid zone, e.g. a
// notifSettings.timeZone written before this field existed, or corrupted by
// a direct Firestore write — Firestore rules only check uid ownership, not
// field validity, so this must not throw and take the whole sweep down).
/**
 * @param {Date} date
 * @param {string} [timeZone]
 * @returns {{ dateStr: string, hour: number }}
 */
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
/**
 * @param {Date} d
 * @param {string} [timeZone]
 * @returns {string}
 */
function todayStr(d, timeZone) {
  return zonedDateParts(d, timeZone).dateStr;
}
/**
 * @param {Date} d
 * @param {string} [timeZone]
 * @returns {string}
 */
function monthPrefix(d, timeZone) {
  return zonedDateParts(d, timeZone).dateStr.slice(0, 7);
}

// Runs fn(item, index) over items with at most `limit` in flight at once,
// resolving to the same shape as Promise.allSettled (never rejects itself).
// notificationSweep used a bare Promise.allSettled over every push_tokens
// doc, which fires every user's Firestore reads + FCM send at once with no
// cap — fine at a handful of users, a burst risk once the account count
// grows. A fixed-size worker pool avoids adding a dependency for something
// this small.
/**
 * @template T, R
 * @param {T[]} items
 * @param {number} limit
 * @param {(item: T, index: number) => Promise<R>} fn
 * @returns {Promise<Array<{status: 'fulfilled', value: R} | {status: 'rejected', reason: any}>>}
 */
async function mapWithConcurrency(items, limit, fn) {
  const results = new Array(items.length);
  let next = 0;
  async function worker() {
    while (next < items.length) {
      const i = next++;
      try {
        results[i] = { status: 'fulfilled', value: await fn(items[i], i) };
      } catch (error) {
        results[i] = { status: 'rejected', reason: error };
      }
    }
  }
  const workerCount = Math.max(1, Math.min(limit, items.length));
  await Promise.all(Array.from({ length: workerCount }, worker));
  return results;
}

module.exports = { SEED_RATES, convertCurrency, toBase, zonedDateParts, todayStr, monthPrefix, mapWithConcurrency };
