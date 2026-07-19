// @ts-check
// ── TX VALIDATION ──────────────────────────────────────
// Pure transaction-draft validation, split out of js/finance.js so it's
// importable in plain Node without pulling in Firebase/DOM — same reason
// functions/lib/pure.js exists separately from functions/index.js (see
// CLAUDE.md's Commands section: functions/index.js's top-level
// initializeApp() call needs firebase-admin credentials just to import).
// This file has zero imports and touches no AppState/DOM, so
// tests/tx-validation.mjs can import it directly.
//
// First file opted into TypeScript's checkJs via the pragma above — see
// CLAUDE.md's TypeScript adoption section for why this file specifically
// (zero imports, pure logic) was the deliberately low-risk starting point,
// and tsconfig.json for why every other js/*.js file stays unchecked
// (checkJs:false project-wide) until opted in the same way, one at a time.

export const TX_AMOUNT_MAX = 1000000000;
export const TX_COMMENT_MAX = 500;
export const TX_DATE_RE = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;

/**
 * @typedef {Object} TransactionDraft
 * @property {number} amount
 * @property {string} date
 * @property {string} ws - source wallet id
 * @property {string} [wt] - target wallet id (transfers only)
 * @property {string} [cat]
 * @property {string} [sub]
 * @property {string} comment
 */

// draft comes from readTransactionForm() in js/finance.js. isTransfer is
// passed in explicitly (AppState.currentFinanceType==='transfer' at the
// call site) rather than read off AppState, so this function stays pure
// and doesn't need to import state.js.
/**
 * @param {TransactionDraft} draft
 * @param {boolean} isTransfer
 * @returns {string} an i18n error key, or '' if the draft is valid
 */
export function validateTransactionDraft(draft, isTransfer){
  if(!Number.isFinite(draft.amount)||draft.amount<=0) return 'finance_err_amount';
  if(draft.amount>=TX_AMOUNT_MAX) return 'finance_err_amount_large';
  if(!draft.date) return 'finance_err_date';
  if(!TX_DATE_RE.test(draft.date)) return 'finance_err_date_format';
  if(!draft.ws) return 'finance_err_wallet';
  if(draft.comment.length>TX_COMMENT_MAX) return 'finance_err_comment_long';
  if(String(draft.cat||'').length>120 || String(draft.sub||'').length>120) return 'finance_err_field_long';
  if(isTransfer){
    if(!draft.wt) return 'finance_err_wallet';
    if(draft.ws===draft.wt) return 'finance_err_same_wallet';
  }
  return '';
}
