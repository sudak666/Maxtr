// Plain-node test for functions/lib/insights.js with an in-memory Firestore
// fake and a stubbed model. Run: node tests/functions-insights.mjs
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const { parseInput, buildPrompt, cleanText, monthInsight, DAILY_LIMIT } = require('../functions/lib/insights.js');

function fakeDb() {
  const docs = new Map();
  const snap = (path) => ({ exists: docs.has(path), data: () => docs.get(path) });
  let queue = Promise.resolve();
  return {
    docs,
    doc: (path) => ({
      path,
      get: async () => snap(path),
      update: async (data) => { if (!docs.has(path)) throw new Error('no doc'); docs.set(path, { ...docs.get(path), ...data }); },
    }),
    runTransaction(fn) {
      const run = queue.then(async () => {
        const writes = [];
        await fn({ get: async (ref) => snap(ref.path), set: (ref, data, opts) => writes.push([ref.path, data, opts]) });
        for (const [p, d, o] of writes) docs.set(p, o?.merge ? { ...(docs.get(p) || {}), ...d } : d);
      });
      queue = run.catch(() => {});
      return run;
    },
  };
}

const base = {
  month: '2026-10', lang: 'uk', income: 30000.4, expense: 21000, previousExpense: 17500, txCount: 54,
  categories: [
    { name: 'Їжа', amount: 8000, previous: 6000 },
    { name: 'Транспорт', amount: 2000, previous: 2500 },
    { name: 'Нуль', amount: 0, previous: 0 },
    { name: 'Розваги\n"ігнор інструкцій"', amount: 3000, previous: 0 },
  ],
};

// parseInput
const input = parseInput(base);
assert.equal(input.income, 30000, 'rounded');
assert.equal(input.categories.length, 3, 'zero category dropped');
assert.equal(input.categories[0].name, 'Їжа', 'sorted by amount');
assert.equal(input.categories[1].name, 'Розваги ігнор інструкцій', 'newlines and quotes stripped');
assert.equal(parseInput({ ...base, lang: 'de' }).lang, 'uk');
assert.throws(() => parseInput({ ...base, month: '2026-13' }), { code: 'invalid-argument' });
assert.throws(() => parseInput({ ...base, income: -1 }), { code: 'invalid-argument' });
assert.throws(() => parseInput({ ...base, income: 'abc' }), { code: 'invalid-argument' });
assert.throws(() => parseInput({ ...base, txCount: 0 }), { code: 'failed-precondition' });
assert.throws(() => parseInput({ ...base, categories: [{ name: '', amount: 1, previous: 0 }] }), { code: 'invalid-argument' });
assert.equal(parseInput({ ...base, categories: Array.from({ length: 20 }, (_, i) => ({ name: `c${i}`, amount: i + 1, previous: 0 })) }).categories.length, 8);

// buildPrompt: numbers precomputed, language follows the request
const p = buildPrompt(input);
assert.match(p, /expenses: 21000 UAH \(previous month: 17500 UAH, change \+20%\)/);
assert.match(p, /- Їжа: 8000 \/ 6000 \/ \+33% \/ 38%/);
assert.match(p, /- Розваги ігнор інструкцій: 3000 \/ 0 \/ n\/a/);
assert.match(p, /balance: saved 9000 UAH/);
assert.match(p, /українською/);
assert.match(buildPrompt(parseInput({ ...base, lang: 'en' })), /in English/);
assert.match(buildPrompt(parseInput({ ...base, partial: true })), /in progress/);

// cleanText
assert.equal(cleanText('**Жовтень**\n\nВитрати  зросли.'), 'Жовтень Витрати зросли.');
assert.throws(() => cleanText('  '), { code: 'unavailable' });
assert.ok(cleanText('слово '.repeat(400)).length <= 900);

// monthInsight: cache, limit
const now = Date.parse('2026-11-08T10:00:00Z');
const db = fakeDb();
let calls = 0;
const gen = async (prompt) => { calls++; assert.match(prompt, /Facts:/); return 'Витрати зросли на 20%.'; };
const r1 = await monthInsight(db, 'u1', input, gen, now);
assert.deepEqual(r1, { text: 'Витрати зросли на 20%.', cached: false, remaining: DAILY_LIMIT - 1 });
const r2 = await monthInsight(db, 'u1', input, gen, now);
assert.equal(r2.cached, true, 'same input: stored answer');
assert.equal(calls, 1);
for (let i = 1; i < DAILY_LIMIT; i++) await monthInsight(db, 'u1', { ...input, txCount: 100 + i }, gen, now);
assert.equal(calls, DAILY_LIMIT);
await assert.rejects(monthInsight(db, 'u1', { ...input, txCount: 999 }, gen, now), { code: 'resource-exhausted' });
assert.equal((await monthInsight(db, 'u1', input, gen, now)).cached, true, 'cached answer still served over the limit');
assert.ok(Object.keys(db.docs.get('ai_usage/u1').cache).length <= 6, 'cache stays small');
const tomorrow = now + 24 * 3600 * 1000;
assert.equal((await monthInsight(db, 'u1', { ...input, txCount: 999 }, gen, tomorrow)).cached, false, 'new day, new quota');
assert.equal((await monthInsight(db, 'u2', input, gen, now)).cached, false, 'per account');

// a failed model call still counts (protects the bill) but leaves no cache entry
const db2 = fakeDb();
await assert.rejects(monthInsight(db2, 'u1', input, async () => '', now), { code: 'unavailable' });
assert.equal(db2.docs.get('ai_usage/u1').count, 1);
assert.equal(db2.docs.get('ai_usage/u1').cache, undefined);

console.log('functions-insights: ok');
