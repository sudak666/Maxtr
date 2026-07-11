// Unit tests for the pure helpers in functions/lib/pure.js (currency
// conversion, timezone-aware date/hour resolution used by the push
// notification sweep). No Firebase/network/browser needed — plain node:
//
//   node tests/unit.mjs
//
// Split out from functions/index.js specifically so these are testable
// without firebase-admin credentials (see functions/lib/pure.js header).
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { convertCurrency, toBase, zonedDateParts, todayStr, monthPrefix, mapWithConcurrency } = require('../functions/lib/pure.js');

let passed = 0;
async function test(name, fn) {
  await fn();
  passed++;
  console.log(`[ok] ${name}`);
}

test('convertCurrency: same currency is a no-op', () => {
  assert.equal(convertCurrency(100, 'UAH', 'UAH', {}), 100);
});

test('convertCurrency: uses provided rates over seed defaults', () => {
  const rates = { USD: 40, EUR: 45 };
  // 10 USD -> EUR at the given cross rates
  assert.equal(convertCurrency(10, 'USD', 'EUR', rates), (10 * 40) / 45);
});

test('convertCurrency: falls back to seed rates for a currency missing from rates', () => {
  // USD present, PLN absent from rates -> seed PLN rate (10.5) used
  const result = convertCurrency(100, 'USD', 'PLN', { USD: 41 });
  assert.equal(result, (100 * 41) / 10.5);
});

test('toBase: converts to UAH by default', () => {
  const rates = { USD: 41 };
  assert.equal(toBase(10, 'USD', rates), convertCurrency(10, 'USD', 'UAH', rates));
});

test('toBase: treats a missing currency code as UAH', () => {
  assert.equal(toBase(50, undefined, {}), 50);
});

test('zonedDateParts: Europe/Kyiv is UTC+3 in July (DST)', () => {
  // 18:05 UTC on 2026-07-10 is 21:05 in Kyiv during summer DST
  const d = new Date('2026-07-10T18:05:00Z');
  const parts = zonedDateParts(d, 'Europe/Kyiv');
  assert.equal(parts.dateStr, '2026-07-10');
  assert.equal(parts.hour, 21);
});

test('zonedDateParts: date rolls over at local midnight, not UTC midnight', () => {
  // 21:30 UTC on 2026-07-10 is 00:30 on 2026-07-11 in Kyiv (UTC+3)
  const d = new Date('2026-07-10T21:30:00Z');
  const parts = zonedDateParts(d, 'Europe/Kyiv');
  assert.equal(parts.dateStr, '2026-07-11');
  assert.equal(parts.hour, 0);
});

test('zonedDateParts: invalid/unknown timeZone falls back to UTC instead of throwing', () => {
  const d = new Date('2026-07-10T18:05:00Z');
  const parts = zonedDateParts(d, 'Not/AZone');
  assert.equal(parts.dateStr, '2026-07-10');
  assert.equal(parts.hour, 18);
});

test('zonedDateParts: missing timeZone defaults to UTC', () => {
  const d = new Date('2026-07-10T18:05:00Z');
  assert.deepEqual(zonedDateParts(d, undefined), zonedDateParts(d, 'UTC'));
});

test('todayStr / monthPrefix: consistent with zonedDateParts', () => {
  const d = new Date('2026-07-10T18:05:00Z');
  assert.equal(todayStr(d, 'Europe/Kyiv'), '2026-07-10');
  assert.equal(monthPrefix(d, 'Europe/Kyiv'), '2026-07');
});

await test('mapWithConcurrency: runs every item and preserves result order', async () => {
  const items = [5, 1, 4, 2, 3];
  const results = await mapWithConcurrency(items, 2, async (n) => {
    await new Promise((r) => setTimeout(r, n));
    return n * 10;
  });
  assert.deepEqual(results.map((r) => r.value), [50, 10, 40, 20, 30]);
  assert.ok(results.every((r) => r.status === 'fulfilled'));
});

await test('mapWithConcurrency: never runs more than `limit` items at once', async () => {
  let active = 0;
  let maxActive = 0;
  const items = Array.from({ length: 10 }, (_, i) => i);
  await mapWithConcurrency(items, 3, async () => {
    active++;
    maxActive = Math.max(maxActive, active);
    await new Promise((r) => setTimeout(r, 5));
    active--;
  });
  assert.ok(maxActive <= 3, `expected max 3 concurrent, saw ${maxActive}`);
});

await test('mapWithConcurrency: a rejected item is reported without aborting the rest', async () => {
  const items = [1, 2, 3];
  const results = await mapWithConcurrency(items, 2, async (n) => {
    if (n === 2) throw new Error('boom');
    return n;
  });
  assert.equal(results[0].status, 'fulfilled');
  assert.equal(results[1].status, 'rejected');
  assert.equal(results[1].reason.message, 'boom');
  assert.equal(results[2].status, 'fulfilled');
});

console.log(`\n${passed} unit test(s) passed`);
