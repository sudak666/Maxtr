// Unit tests for js/receipt-ocr.js's parseReceiptText() — plain node, no
// Tesseract/WASM/DOM/Playwright needed, since the text-parsing regexes are
// deliberately kept in a pure function separate from the actual OCR call
// (see the module's own header comment), the same split js/tx-validation.js
// already uses for the same reason. Run with:
//
//   node tests/receipt-ocr-parse.mjs
import assert from 'node:assert/strict';
import { parseReceiptText } from '../js/receipt-ocr.js';

let passed = 0;
function test(name, fn) {
  fn();
  passed++;
  console.log(`[ok] ${name}`);
}

test('finds the amount on a line with a Ukrainian total keyword, ignoring earlier line-item numbers', () => {
  const text = 'МАГАЗИН "СІЛЬПО"\nХліб              32.50\nМолоко            45.00\nСУМА:            77.50\nДЯКУЄМО ЗА ПОКУПКУ';
  const { amount } = parseReceiptText(text);
  assert.equal(amount, 77.5);
});

test('finds the amount on a line with an English total keyword', () => {
  const text = 'STORE RECEIPT\nItem A   10.00\nItem B   5.00\nTOTAL    15.00\nThank you';
  const { amount } = parseReceiptText(text);
  assert.equal(amount, 15);
});

test('handles a comma decimal separator and a space thousands separator', () => {
  const text = 'ЧЕК\nІТОГО ДО СПЛАТИ: 1 250,75 грн';
  const { amount } = parseReceiptText(text);
  assert.equal(amount, 1250.75);
});

test('falls back to the largest number in the text when no keyword line matches', () => {
  const text = 'Line one 12.00\nLine two 340.00\nLine three 5.00';
  const { amount } = parseReceiptText(text);
  assert.equal(amount, 340);
});

test('does not mistake the year in a date for the amount when no keyword line matches (regression: real-device OCR test picked 2026 over 97.50)', () => {
  const text = 'MAGAZIN SILPO\n16.07.2026 14:32\nKhlib   32.50\nSUMA:   97.50';
  // No Cyrillic "СУМА" keyword present (this is the transliterated text an
  // OCR pass can actually produce), so this exercises the numeric fallback
  // specifically, with a bare "2026" from the unmasked date sitting right
  // next to real decimal amounts.
  const { amount, date } = parseReceiptText(text);
  assert.equal(amount, 97.5);
  assert.equal(date, '2026-07-16');
});

test('falls back to the largest whole number only when no decimal-formatted number exists', () => {
  const text = 'Qty 3\nQty 12\nQty 4';
  const { amount } = parseReceiptText(text);
  assert.equal(amount, 12);
});

test('returns a null amount for text with no numbers at all', () => {
  const { amount } = parseReceiptText('this is not a receipt');
  assert.equal(amount, null);
});

test('extracts a dd.mm.yyyy date and normalizes it to yyyy-mm-dd', () => {
  const { date } = parseReceiptText('Чек від 16.07.2026\nСУМА 100.00');
  assert.equal(date, '2026-07-16');
});

test('extracts a dd/mm/yy date with a 2-digit year, expanding to 20yy', () => {
  const { date } = parseReceiptText('Date: 03/01/26\nTOTAL 20.00');
  assert.equal(date, '2026-01-03');
});

test('rejects an out-of-range month/day as a likely OCR misread rather than guessing', () => {
  const { date } = parseReceiptText('99.13.2026\nTOTAL 20.00');
  assert.equal(date, null);
});

test('returns a null date when nothing date-shaped is present', () => {
  const { date } = parseReceiptText('no date here, just TOTAL 5.00');
  assert.equal(date, null);
});

test('rawText passes the original input through unchanged', () => {
  const { rawText } = parseReceiptText('SOME  raw\nOCR text');
  assert.equal(rawText, 'SOME  raw\nOCR text');
});

test('handles empty/undefined input without throwing', () => {
  assert.deepEqual(parseReceiptText(''), { amount: null, date: null, rawText: '' });
  assert.deepEqual(parseReceiptText(undefined), { amount: null, date: null, rawText: '' });
});

console.log(`\n${passed} passed`);
