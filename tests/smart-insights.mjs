// Plain-node tests for js/smart-insights.js (zero imports, no DOM):
//   node tests/smart-insights.mjs
import assert from 'node:assert/strict';
import { safeToSpendPerDay, matchMerchant, parseSmartSearch, smartQueryMatches } from '../js/smart-insights.js';

let passed = 0;
function test(name, fn) { fn(); passed++; console.log(`[ok] ${name}`); }

const OCT20 = new Date(2026, 9, 20);

test('safe to spend: free money ÷ days left incl. today', () => {
  assert.equal(safeToSpendPerDay(1500, 300, OCT20), 100); // 12 days left
  assert.equal(safeToSpendPerDay(500, 0, new Date(2026, 9, 31)), 500);
  assert.equal(safeToSpendPerDay(200, 300, OCT20), null);
});

test('merchants are recognised from bank comments', () => {
  assert.equal(matchMerchant('KOLO KIYEV UA')?.name, 'Коло');
  assert.equal(matchMerchant('Сільпо, Київ')?.name, 'Сільпо');
  assert.equal(matchMerchant('Google YouTube London GB')?.name, 'YouTube');
  assert.equal(matchMerchant('ATB-Market 123')?.name, 'АТБ');
  assert.equal(matchMerchant('кава з друзями'), null);
  assert.equal(matchMerchant('околиця'), null);
  assert.equal(matchMerchant(null), null);
});

test('smart search turns words into filters', () => {
  const q = parseSmartSearch('Кафе у жовтні більше 200', OCT20);
  assert.deepEqual(q, { text: 'кафе', min: 200, max: null, month: '2026-10', date: null });
  const r = parseSmartSearch('від 100 до 300', OCT20);
  assert.equal(r.min, 100); assert.equal(r.max, 300); assert.equal(r.text, '');
  assert.equal(parseSmartSearch('грудень', OCT20).month, '2025-12');
  assert.equal(parseSmartSearch('вчора', OCT20).date, '2026-10-19');
  const w = parseSmartSearch('червоний market', OCT20);
  assert.equal(w.text, 'червоний market'); assert.equal(w.month, null);
});

test('smart query matches amount/month/date', () => {
  const q = parseSmartSearch('жовтень більше 200', OCT20);
  assert.equal(smartQueryMatches(q, { amount: 450, date: '2026-10-05' }), true);
  assert.equal(smartQueryMatches(q, { amount: 150, date: '2026-10-05' }), false);
  assert.equal(smartQueryMatches(q, { amount: 450, date: '2026-09-05' }), false);
});

console.log(`\n${passed} smart-insights test(s) passed`);
