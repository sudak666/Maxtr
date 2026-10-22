// Plain-node tests for js/debt-pace.js. Run: node tests/debt-pace.mjs
import assert from 'node:assert/strict';
import { debtPace } from '../js/debt-pace.js';

// One early lump, then ~monthly small payments — same shape as Android's DebtPaceTest.
const entries = [
  [55000, '10.01.2026'], [54870, '10.02.2026'], [54750, '10.03.2026'], [54640, '10.04.2026'],
  [54520, '10.05.2026'], [54391, '10.06.2026'], [54287, '11.07.2026'], [54176, '10.08.2026'], [54021, '10.09.2026'],
].map(([balance, date]) => ({ balance, date }));
const p = debtPace(75000, entries);
assert.equal(p.typicalPayment, 115.5);
assert.equal(p.typicalGapDays, 31);

assert.equal(debtPace(1000, [{ balance: 900 }, { balance: 950 }, { balance: 850 }]).typicalPayment, 100);
assert.equal(debtPace(1000, [{ balance: 900 }, { balance: 950 }, { balance: 850 }]).typicalGapDays, null);
assert.equal(debtPace(1000, [{ balance: 1000 }, { balance: 1100 }]), null);
// AUDIT_2026-10 P4-03: an even gap count rounds half up, same as Android (gaps 30, 31 -> 31).
assert.equal(debtPace(1000, [{ balance: 900, date: '01.01.2026' }, { balance: 800, date: '31.01.2026' }, { balance: 700, date: '03.03.2026' }]).typicalGapDays, 31);
console.log('debt-pace: OK');
