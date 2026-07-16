// Unit tests for js/tx-validation.js — plain node, no Firebase/DOM/
// Playwright needed, since that module has zero imports and touches no
// AppState/DOM (see its own header comment for why it's split out of
// js/finance.js). Run with:
//
//   node tests/tx-validation.mjs
import assert from 'node:assert/strict';
import { TX_AMOUNT_MAX, TX_COMMENT_MAX, validateTransactionDraft } from '../js/tx-validation.js';

let passed = 0;
function test(name, fn) {
  fn();
  passed++;
  console.log(`[ok] ${name}`);
}

const baseDraft = { amount: 100, date: '2026-07-16', ws: 'wallet1', wt: '', cat: 'Продукти', sub: null, comment: '' };

test('a well-formed expense/income draft is valid', () => {
  assert.equal(validateTransactionDraft(baseDraft, false), '');
});

test('rejects a zero or negative amount', () => {
  assert.equal(validateTransactionDraft({ ...baseDraft, amount: 0 }, false), 'finance_err_amount');
  assert.equal(validateTransactionDraft({ ...baseDraft, amount: -5 }, false), 'finance_err_amount');
});

test('rejects a non-finite amount (NaN from an empty/non-numeric input)', () => {
  assert.equal(validateTransactionDraft({ ...baseDraft, amount: NaN }, false), 'finance_err_amount');
});

test('rejects an amount at or past TX_AMOUNT_MAX', () => {
  assert.equal(validateTransactionDraft({ ...baseDraft, amount: TX_AMOUNT_MAX }, false), 'finance_err_amount_large');
  assert.equal(validateTransactionDraft({ ...baseDraft, amount: TX_AMOUNT_MAX + 1 }, false), 'finance_err_amount_large');
});

test('rejects a missing date', () => {
  assert.equal(validateTransactionDraft({ ...baseDraft, date: '' }, false), 'finance_err_date');
});

test('rejects a malformed date (not YYYY-MM-DD)', () => {
  assert.equal(validateTransactionDraft({ ...baseDraft, date: '16.07.2026' }, false), 'finance_err_date_format');
});

test('rejects a missing wallet', () => {
  assert.equal(validateTransactionDraft({ ...baseDraft, ws: '' }, false), 'finance_err_wallet');
});

test('rejects a comment past TX_COMMENT_MAX', () => {
  const longComment = 'x'.repeat(TX_COMMENT_MAX + 1);
  assert.equal(validateTransactionDraft({ ...baseDraft, comment: longComment }, false), 'finance_err_comment_long');
  const maxComment = 'x'.repeat(TX_COMMENT_MAX);
  assert.equal(validateTransactionDraft({ ...baseDraft, comment: maxComment }, false), '');
});

test('rejects a category or subcategory longer than 120 chars', () => {
  const longCat = 'x'.repeat(121);
  assert.equal(validateTransactionDraft({ ...baseDraft, cat: longCat }, false), 'finance_err_field_long');
  assert.equal(validateTransactionDraft({ ...baseDraft, sub: longCat }, false), 'finance_err_field_long');
});

test('a transfer requires a target wallet even though non-transfers do not', () => {
  assert.equal(validateTransactionDraft({ ...baseDraft, wt: '' }, false), '');
  assert.equal(validateTransactionDraft({ ...baseDraft, wt: '' }, true), 'finance_err_wallet');
});

test('rejects a transfer to the same wallet it came from', () => {
  assert.equal(validateTransactionDraft({ ...baseDraft, wt: 'wallet1' }, true), 'finance_err_same_wallet');
});

test('accepts a well-formed transfer between two different wallets', () => {
  assert.equal(validateTransactionDraft({ ...baseDraft, wt: 'wallet2' }, true), '');
});

console.log(`\n${passed} tx-validation test(s) passed`);
