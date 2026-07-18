// Pure, side-effect-free helpers for the monobankProxy Cloud Function
// (functions/index.js) — split out so they're unit-testable
// (tests/monobank-proxy.mjs) without firebase-admin credentials, same
// reasoning as lib/pure.js/lib/sweep.js.
//
// WHY A PROXY AT ALL: api.monobank.ua never sends CORS headers, so a
// browser fetch() straight to it is blocked — same root cause documented
// on PRIVAT_RATES_URL in index.js. Unlike that endpoint (public exchange
// rates, no auth), Monobank's API is authenticated with the *user's own*
// personal API token (a live bank credential, sent as an X-Token header) —
// so this proxy also requires a valid Firebase ID token (verified in
// index.js via firebase-admin's auth().verifyIdToken()) before it will
// relay anything, so it can't be used as an anonymous internet-wide relay
// into Monobank's API.

const MONOBANK_BASE = 'https://api.monobank.ua';

// Monobank's statement endpoint rejects a [from,to) window wider than 31
// days + 1 hour (2682000s) in a single call — the client (js/monobank.js)
// chunks a longer sync range into windows this size.
const MONOBANK_MAX_WINDOW_SEC = 2682000;

// query: the Cloud Function request's parsed query string ({action, account, from, to}).
// Returns {ok:true, url} or {ok:false, status, error}.
function buildMonobankUrl(query) {
  const action = query && query.action;
  if (action === 'client-info') {
    return { ok: true, url: `${MONOBANK_BASE}/personal/client-info` };
  }
  if (action === 'statement') {
    const account = query.account;
    const from = Number(query.from);
    const to = Number(query.to);
    if (!account) return { ok: false, status: 400, error: 'missing account' };
    if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) {
      return { ok: false, status: 400, error: 'invalid from/to' };
    }
    if (to - from > MONOBANK_MAX_WINDOW_SEC) {
      return { ok: false, status: 400, error: 'from/to window exceeds Monobank\'s 31-day limit' };
    }
    return { ok: true, url: `${MONOBANK_BASE}/personal/statement/${encodeURIComponent(account)}/${from}/${to}` };
  }
  return { ok: false, status: 400, error: `unknown action "${action}"` };
}

module.exports = { MONOBANK_MAX_WINDOW_SEC, buildMonobankUrl };
