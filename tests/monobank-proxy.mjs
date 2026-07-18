// Unit tests for functions/lib/monobank.js's buildMonobankUrl() — the
// query-validation/URL-building logic behind the monobankProxy Cloud
// Function. No Firebase/network/browser needed — plain node:
//
//   node tests/monobank-proxy.mjs
//
// The ID-token verification and actual upstream fetch (the rest of
// monobankProxy in functions/index.js) aren't covered here — they need
// firebase-admin credentials to exercise for real, same reasoning as
// notificationSweep's own split between lib/sweep.js (unit-tested) and
// index.js's wiring (not).
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { MONOBANK_MAX_WINDOW_SEC, buildMonobankUrl } = require('../functions/lib/monobank.js');

let passed = 0;
async function test(name, fn) {
  await fn();
  passed++;
  console.log(`[ok] ${name}`);
}

await test('client-info: builds the fixed client-info URL with no params needed', () => {
  const r = buildMonobankUrl({ action: 'client-info' });
  assert.equal(r.ok, true);
  assert.equal(r.url, 'https://api.monobank.ua/personal/client-info');
});

await test('statement: builds the account/from/to URL', () => {
  const r = buildMonobankUrl({ action: 'statement', account: 'acc123', from: '1000', to: '2000' });
  assert.equal(r.ok, true);
  assert.equal(r.url, 'https://api.monobank.ua/personal/statement/acc123/1000/2000');
});

await test('statement: URL-encodes the account id (Monobank uses "0" for the default/jar aliasing too, but ids can contain unusual chars)', () => {
  const r = buildMonobankUrl({ action: 'statement', account: 'a/b c', from: '1000', to: '2000' });
  assert.equal(r.ok, true);
  assert.ok(r.url.includes(encodeURIComponent('a/b c')));
});

await test('statement: missing account is rejected', () => {
  const r = buildMonobankUrl({ action: 'statement', from: '1000', to: '2000' });
  assert.equal(r.ok, false);
  assert.equal(r.status, 400);
});

await test('statement: non-numeric from/to is rejected', () => {
  const r = buildMonobankUrl({ action: 'statement', account: 'a', from: 'nope', to: '2000' });
  assert.equal(r.ok, false);
  assert.equal(r.status, 400);
});

await test('statement: to <= from is rejected', () => {
  const r = buildMonobankUrl({ action: 'statement', account: 'a', from: '2000', to: '2000' });
  assert.equal(r.ok, false);
});

await test('statement: a window right at the 31-day-plus-1-hour limit is accepted', () => {
  const from = 1000000;
  const r = buildMonobankUrl({ action: 'statement', account: 'a', from: String(from), to: String(from + MONOBANK_MAX_WINDOW_SEC) });
  assert.equal(r.ok, true);
});

await test('statement: a window one second past the limit is rejected', () => {
  const from = 1000000;
  const r = buildMonobankUrl({ action: 'statement', account: 'a', from: String(from), to: String(from + MONOBANK_MAX_WINDOW_SEC + 1) });
  assert.equal(r.ok, false);
  assert.equal(r.status, 400);
});

await test('unknown action is rejected', () => {
  const r = buildMonobankUrl({ action: 'delete-everything' });
  assert.equal(r.ok, false);
  assert.equal(r.status, 400);
});

await test('missing action is rejected', () => {
  const r = buildMonobankUrl({});
  assert.equal(r.ok, false);
});

console.log(`\n${passed} tests passed.`);
console.log('MONOBANK PROXY UNIT TESTS PASSED');
