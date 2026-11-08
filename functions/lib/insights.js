// @ts-check
// AI month summary: the client sends only per-category totals of one month
// (no transactions, comments, merchants or wallets); Gemini on Vertex AI
// turns them into 3–4 plain sentences. Every number in the prompt is
// computed here, so the model only has to phrase them. Rate-limited per
// account in ai_usage/{uid} (Admin SDK only; rules deny clients).
const crypto = require('crypto');

const DAILY_LIMIT = 10;
const MAX_CATEGORIES = 8;
const MODEL = process.env.INSIGHTS_MODEL || 'gemini-2.5-flash';

class InsightsError extends Error {
  /** @param {string} code @param {string} message */
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

/** @param {unknown} v */
function money(v) {
  const n = Number(v);
  if (!Number.isFinite(n) || n < 0 || n > 1e10) throw new InsightsError('invalid-argument', 'bad amount');
  return Math.round(n);
}

/** @param {unknown} v */
function label(v) {
  // Category names are the user's own; keep them short and on one line.
  const s = String(v ?? '').replace(/[\r\n\t]+/g, ' ').replace(/[`"<>]/g, '').trim().slice(0, 40);
  if (!s) throw new InsightsError('invalid-argument', 'bad category');
  return s;
}

/**
 * @typedef {{name: string, amount: number, previous: number}} Category
 * @typedef {{month: string, lang: 'uk'|'en', partial: boolean, income: number, expense: number,
 *   previousExpense: number, txCount: number, categories: Category[]}} InsightsInput
 */

/**
 * Pure: validated, normalized request.
 * @param {any} data
 * @returns {InsightsInput}
 */
function parseInput(data) {
  const d = data || {};
  if (typeof d.month !== 'string' || !/^20\d\d-(0[1-9]|1[0-2])$/.test(d.month)) throw new InsightsError('invalid-argument', 'bad month');
  const cats = Array.isArray(d.categories) ? d.categories : [];
  if (cats.length > 50) throw new InsightsError('invalid-argument', 'too many categories');
  /** @type {Category[]} */
  const categories = cats
    .map((/** @type {any} */ c) => ({ name: label(c && c.name), amount: money(c && c.amount), previous: money(c && c.previous) }))
    .filter((/** @type {Category} */ c) => c.amount > 0 || c.previous > 0)
    .sort((/** @type {Category} */ a, /** @type {Category} */ b) => b.amount - a.amount)
    .slice(0, MAX_CATEGORIES);
  const txCount = Math.max(0, Math.min(100000, Math.round(Number(d.txCount) || 0)));
  if (txCount === 0) throw new InsightsError('failed-precondition', 'empty month');
  return {
    month: d.month,
    lang: d.lang === 'en' ? 'en' : 'uk',
    partial: d.partial === true,
    income: money(d.income),
    expense: money(d.expense),
    previousExpense: money(d.previousExpense),
    txCount,
    categories,
  };
}

/** @param {number} now @param {number} prev */
function changePct(now, prev) {
  return prev > 0 ? Math.round(((now - prev) / prev) * 100) : null;
}

/** @param {number|null} p */
function fmtPct(p) {
  return p === null ? 'n/a' : `${p > 0 ? '+' : ''}${p}%`;
}

/**
 * Pure: the prompt. Facts are precomputed and listed; the rules forbid new numbers.
 * @param {InsightsInput} i
 */
function buildPrompt(i) {
  const uk = i.lang === 'uk';
  const net = i.income - i.expense;
  const lines = [
    `month: ${i.month}${i.partial ? ' (in progress; previous month cut to the same days)' : ''}`,
    `income: ${i.income} UAH`,
    `expenses: ${i.expense} UAH (previous month: ${i.previousExpense} UAH, change ${fmtPct(changePct(i.expense, i.previousExpense))})`,
    `balance: ${net >= 0 ? 'saved' : 'overspent'} ${Math.abs(net)} UAH`,
    `operations: ${i.txCount}`,
    'expense categories (this month / previous month / change / share of expenses):',
    ...i.categories.map((c) => `- ${c.name}: ${c.amount} / ${c.previous} / ${fmtPct(changePct(c.amount, c.previous))} / ${i.expense > 0 ? Math.round((c.amount / i.expense) * 100) : 0}%`),
  ];
  const rules = uk
    ? [
        'Ти пишеш короткий підсумок місяця для застосунку обліку особистих фінансів Rytm.',
        'Напиши 3–4 короткі речення українською, звертайся на «ви», дружньо й без осуду.',
        'Використовуй лише числа з фактів нижче, нічого не вигадуй і не рахуй нових сум.',
        'Скажи, що змінилося найбільше, і дай одну практичну, конкретну пораду щодо витрат.',
        'Без порад щодо інвестицій, кредитів, акцій чи криптовалют. Без markdown, списків і емодзі.',
        'Суми пиши як «12 500 ₴».',
      ]
    : [
        'You write a short month summary for Rytm, a personal finance tracking app.',
        'Write 3–4 short sentences in English, friendly and non-judgmental.',
        'Use only numbers from the facts below; invent nothing and compute no new sums.',
        'Say what changed most and give one practical, concrete tip about spending.',
        'No investment, loan, stock or crypto advice. No markdown, lists or emoji.',
        'Write amounts like "12,500 ₴".',
      ];
  return `${rules.join('\n')}\n\nFacts:\n${lines.join('\n')}`;
}

/**
 * Pure: model output → one clean paragraph.
 * @param {string} text
 */
function cleanText(text) {
  const s = String(text || '').replace(/[*#_`]+/g, '').replace(/\s*\n+\s*/g, ' ').replace(/\s{2,}/g, ' ').trim();
  if (!s) throw new InsightsError('unavailable', 'empty answer');
  return s.length > 900 ? `${s.slice(0, 897).replace(/\s+\S*$/, '')}…` : s;
}

/** @param {InsightsInput} i */
function inputHash(i) {
  return crypto.createHash('sha256').update(JSON.stringify(i)).digest('hex').slice(0, 32);
}

/**
 * Counts one request against today's limit (Kyiv day); throws when over.
 * A repeat of the same input returns the stored answer without a new call.
 * @param {any} db
 * @param {string} uid
 * @param {InsightsInput} input
 * @param {(prompt: string) => Promise<string>} generate model call (injected for tests)
 * @param {number} now
 * @returns {Promise<{text: string, cached: boolean, remaining: number}>}
 */
async function monthInsight(db, uid, input, generate, now) {
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Kyiv' }).format(now);
  const hash = inputHash(input);
  const ref = db.doc(`ai_usage/${uid}`);
  const snap = await ref.get();
  const doc = snap.exists ? snap.data() || {} : {};
  const cache = doc.cache && typeof doc.cache === 'object' ? doc.cache : {};
  const used = doc.day === day ? Number(doc.count) || 0 : 0;
  if (cache[hash] && typeof cache[hash].text === 'string') {
    return { text: cache[hash].text, cached: true, remaining: Math.max(0, DAILY_LIMIT - used) };
  }
  if (used >= DAILY_LIMIT) throw new InsightsError('resource-exhausted', 'daily limit');
  // Reserve the slot first, so parallel calls can't run past the limit.
  await db.runTransaction(async (/** @type {any} */ tx) => {
    const s = await tx.get(ref);
    const d = s.exists ? s.data() || {} : {};
    const n = d.day === day ? Number(d.count) || 0 : 0;
    if (n >= DAILY_LIMIT) throw new InsightsError('resource-exhausted', 'daily limit');
    tx.set(ref, { day, count: n + 1, updatedAt: now }, { merge: true });
  });
  const text = cleanText(await generate(buildPrompt(input)));
  // Keep the newest few answers only.
  const kept = Object.entries(cache)
    .sort((a, b) => (Number(b[1] && b[1].at) || 0) - (Number(a[1] && a[1].at) || 0))
    .slice(0, 5);
  // update() replaces the whole map, so old entries really go.
  await ref.update({ cache: Object.fromEntries([...kept, [hash, { text, at: now }]]) }).catch(() => {});
  return { text, cached: false, remaining: Math.max(0, DAILY_LIMIT - used - 1) };
}

/**
 * Real Gemini call on Vertex AI with the function's own service account.
 * @param {string} prompt
 */
async function generateWithGemini(prompt) {
  const { GoogleAuth } = require('google-auth-library');
  const auth = new GoogleAuth({ scopes: ['https://www.googleapis.com/auth/cloud-platform'] });
  const client = await auth.getClient();
  const project = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || (await auth.getProjectId());
  const url = `https://aiplatform.googleapis.com/v1/projects/${project}/locations/global/publishers/google/models/${MODEL}:generateContent`;
  const res = await client.request({
    url,
    method: 'POST',
    timeout: 30000,
    data: {
      contents: [{ role: 'user', parts: [{ text: prompt }] }],
      generationConfig: { temperature: 0.5, maxOutputTokens: 500, thinkingConfig: { thinkingBudget: 0 } },
    },
  });
  const data = /** @type {any} */ (res.data);
  const parts = (data && data.candidates && data.candidates[0] && data.candidates[0].content && data.candidates[0].content.parts) || [];
  return parts.map((/** @type {any} */ p) => p.text || '').join('');
}

module.exports = { InsightsError, parseInput, buildPrompt, cleanText, monthInsight, generateWithGemini, DAILY_LIMIT, MODEL };
