// Plain-node tests for js/shift-money.js (no DOM/AppState). Run: node tests/shift-money.mjs
import assert from 'node:assert/strict';
import { typicalShiftPay, remainingShiftPay, recurringUntilMonthEnd } from '../js/shift-money.js';

const types = [
  { id: 'd', amount: 1000 },
  { id: 'n', amount: 1500 },
  { id: 'o', amount: 0, isOff: true },
];

// Autofill type wins; otherwise the most used paid type.
assert.equal(typicalShiftPay({}, types, { typeId: 'n' }), 1500);
assert.equal(typicalShiftPay({ '2026-10-01': ['d'], '2026-10-02': ['d'], '2026-10-03': ['n'] }, types, {}), 1000);
assert.equal(typicalShiftPay({}, [{ id: 'o', amount: 0, isOff: true }], {}), null);

// Placed shifts after today, plus autofill on empty days (every day), to month end only.
const today = new Date(2026, 9, 28); // 28 Oct → 29, 30, 31 remain
assert.equal(remainingShiftPay(today, { '2026-10-29': ['n'], '2026-10-28': ['d'], '2026-11-01': ['d'] }, types, {}), 1500);
assert.equal(remainingShiftPay(today, { '2026-10-29': ['n'] }, types, { enabled: true, typeId: 'd', pattern: 'every', anchorDate: '2026-10-01' }), 1500 + 2 * 1000);

// Weekly recurring counts each occurrence from today to month end; inactive/past are skipped.
const rec = recurringUntilMonthEnd(new Date(2026, 9, 6), [
  { type: 'expense', amount: 100, nextDate: '2026-10-06', frequency: 'weekly' }, // 6,13,20,27
  { type: 'expense', amount: 999, nextDate: '2026-10-07', active: false },
  { type: 'income', amount: 50, nextDate: '2026-10-20', frequency: 'monthly' },
  { type: 'expense', amount: 7, nextDate: '2026-11-02', frequency: 'monthly' },
], (amount) => amount);
assert.equal(rec.out, 400);
assert.equal(rec.inc, 50);

console.log('shift-money: OK');
