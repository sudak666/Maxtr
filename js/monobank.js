// ── MONOBANK ──────────────────────────────────────────
// Client-side half of the Monobank Open API integration (Settings →
// Фінанси → "Прив'язати Monobank"). Manual "Синхронізувати" pulls, not a
// webhook — see CLAUDE.md for why. api.monobank.ua never sends CORS
// headers, so every call here goes through this app's own monobankProxy
// Cloud Function (functions/index.js, exposed same-origin via the
// "/api/monobank" Hosting rewrite in firebase.json) instead of hitting
// Monobank directly from the browser. The user's personal Monobank API
// token is only ever sent to that proxy (alongside this account's own
// Firebase ID token, which the proxy verifies before relaying anything) —
// it's stored in AppState.integrations.monobank the same way every other
// per-profile setting is stored (synced in the finance doc, see
// js/color-picker.js's fbSaveNow/seedConfigFromDocs), never sent anywhere
// else.
import { AppState } from './state.js';
import { PALETTE, canEditActiveProfile, walletCurrency } from './core.js';
import { renderFinance } from './analytics-csv.js';
import { renderFinanceChart } from './calendar.js';
import { saveConfigLocal, scheduleSave } from './color-picker.js';
import { findMatchingRule, newTransactionId, refreshWalletSelects } from './finance.js';
import { batchWriteTransactions, lsKey } from './firebase-sync.js';
import { setCacheItem } from './privacy-cache.js';
import { uid } from './settings-managers.js';
import { escapeHtml, showToast, uiConfirm } from './ui-widgets.js';

// Monobank rejects a statement call whose [from,to) window is wider than
// 31 days + 1 hour — kept in sync with functions/lib/monobank.js's own
// MONOBANK_MAX_WINDOW_SEC by hand (client/server are different module
// systems in this repo, see CLAUDE.md, so this isn't a shared import).
const MONOBANK_MAX_WINDOW_SEC = 2682000;
// A first-ever sync (no lastSyncAt yet) only pulls the last 31 days, not a
// user's entire history — keeps the very first "Синхронізувати" click to a
// single rate-limited request per account instead of potentially many.
const DEFAULT_SYNC_LOOKBACK_SEC = MONOBANK_MAX_WINDOW_SEC;
// Monobank enforces at most 1 request per 60 seconds per token, across
// every endpoint (not just statement) — every call this module makes is
// paced at least this far apart via paceMonobankRequest() below. 61s
// (not a bare 60s) leaves a small safety margin against clock drift.
// `let`, not `const`, purely so tests/monobank-connect.mjs can shrink this
// to a few ms via setMonobankSyncGapMsForTesting() below — a real 61s gap
// per request would make a Playwright test of a multi-call sync take
// minutes for no real coverage benefit (the pacing logic itself is what's
// under test, not Monobank's actual rate limit).
let SYNC_REQUEST_GAP_MS=61000;
export function setMonobankSyncGapMsForTesting(ms){ SYNC_REQUEST_GAP_MS=ms; }
// Defensive cap on how many 31-day chunks a single account can pull in one
// "Синхронізувати" click (~14 months) — not a real-world limit (nobody
// should realistically go over a year between syncs), just a guard against
// a runaway loop if lastSyncAt is ever corrupted to something ancient.
const MAX_SYNC_CHUNKS_PER_ACCOUNT = 14;

const MONOBANK_CURRENCY_MAP = {980:'UAH', 840:'USD', 978:'EUR', 826:'GBP', 985:'PLN'};
function monobankCurrencyAlpha(numericCode){ return MONOBANK_CURRENCY_MAP[numericCode] || 'UAH'; }

function monoDateStr(unixSec){
  const d=new Date(unixSec*1000);
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

let lastMonobankRequestAt=0;
async function paceMonobankRequest(){
  const wait=SYNC_REQUEST_GAP_MS-(Date.now()-lastMonobankRequestAt);
  if(wait>0) await new Promise(r=>setTimeout(r,wait));
  lastMonobankRequestAt=Date.now();
}

async function monobankApiRequest(action, params, monobankToken){
  const idToken=await AppState.currentUser.getIdToken();
  const qs=new URLSearchParams({action, ...params});
  const res=await fetch(`/api/monobank?${qs}`, {
    headers:{'Authorization':'Bearer '+idToken, 'X-Monobank-Token':monobankToken},
  });
  const bodyText=await res.text();
  let body=null; try{ body=JSON.parse(bodyText); }catch(e){ /* non-JSON error body, handled via res.ok below */ }
  if(!res.ok){
    const err=new Error((body&&body.error)||`Monobank HTTP ${res.status}`);
    err.status=res.status;
    throw err;
  }
  return body;
}

function monobankErrorMessage(err){
  if(err && (err.status===401||err.status===403)) return tr('monobank_err_invalid_token');
  if(err && err.status===429) return tr('monobank_err_rate_limit');
  return tr('monobank_err_generic');
}

function monobankAccountLabel(a){
  const maskedPan=Array.isArray(a.maskedPan)&&a.maskedPan[0]?a.maskedPan[0]:'';
  return maskedPan || (a.type ? `Monobank ${a.type}` : 'Monobank');
}

// Every real account + every jar becomes its own entry — per the account
// owner's explicit choice ("всі картки й банки одразу", see CLAUDE.md),
// v1 links everything at once rather than a manual per-account picker.
function buildMonobankAccountsList(info){
  const accounts=[];
  (info.accounts||[]).forEach(a=>{
    accounts.push({id:a.id, kind:'account', label:monobankAccountLabel(a), currencyAlpha:monobankCurrencyAlpha(a.currencyCode)});
  });
  (info.jars||[]).forEach(j=>{
    accounts.push({id:j.id, kind:'jar', label:j.title||tr('monobank_jar_default_name'), currencyAlpha:monobankCurrencyAlpha(j.currencyCode)});
  });
  return accounts;
}

function createWalletForMonobankAccount(a){
  const wallet={id:uid('w'), name:`Monobank ${a.label}`, color:PALETTE[AppState.wallets.length%PALETTE.length], icon:a.kind==='jar'?'target':'card', currency:a.currencyAlpha};
  AppState.wallets.push(wallet);
  return wallet;
}

const connectMonobankUI=async function(){
  if(!canEditActiveProfile()){ showToast(tr('shared_profile_readonly'),'xmark'); return; }
  const input=document.getElementById('monobank-token-input');
  const token=(input&&input.value||'').trim();
  const errEl=document.getElementById('monobank-connect-error');
  if(errEl) errEl.textContent='';
  if(!token){ if(errEl) errEl.textContent=tr('monobank_token_required'); return; }
  const btn=document.getElementById('monobank-connect-btn');
  const originalLabel=btn?btn.textContent:'';
  if(btn){ btn.disabled=true; btn.textContent=tr('monobank_connecting'); }
  try{
    await paceMonobankRequest();
    const info=await monobankApiRequest('client-info', {}, token);
    const accounts=buildMonobankAccountsList(info);
    if(!accounts.length) throw new Error(tr('monobank_no_accounts'));
    const mapping={};
    accounts.forEach(a=>{ mapping[a.id]=createWalletForMonobankAccount(a).id; });
    AppState.integrations.monobank={token, clientName:info.name||'', accounts, mapping, lastSyncAt:null};
    saveConfigLocal(); scheduleSave();
    refreshWalletSelects(); renderFinance();
    if(input) input.value='';
    renderMonobankUI();
    showToast(tr('monobank_connected'),'check');
  }catch(err){
    console.error(err);
    if(errEl) errEl.textContent=monobankErrorMessage(err);
  }finally{
    if(btn){ btn.disabled=false; btn.textContent=originalLabel; }
  }
};

// hold:true is a pending/preauth entry Monobank hasn't finalized yet (it
// can still disappear or change) — skipped, same reasoning CSV import and
// every other transaction-source integration in this app applies to
// not-yet-certain data. Dedup is by Monobank's own stable per-entry id
// (stored as monobankId on the created transaction), so re-syncing an
// overlapping date range never creates duplicates.
function buildTransactionFromMonobankEntry(r, walletId){
  if(r.hold) return null;
  const monobankId=String(r.id);
  if(AppState.transactions.some(t=>t.monobankId===monobankId)) return null;
  const amount=Math.abs(r.amount)/100;
  if(!Number.isFinite(amount)||amount<=0) return null;
  const type=r.amount<0?'expense':'income';
  const currency=walletCurrency(walletId);
  const comment=(r.description||'').trim();
  // Keyword auto-rules (the same ones the manual add-transaction form
  // applies, js/finance.js's findMatchingRule()) get first say on
  // category; anything unmatched falls back to the same generic "Інше"
  // bucket a manual entry with no category picked would use.
  const rule=findMatchingRule(type, comment);
  const category=rule?rule.category:'Інше';
  return {
    id:newTransactionId(), createdAt:Date.now(), type, amount, currency,
    category, subcategory:null, tags:[], wallet:walletId, targetWallet:null,
    targetAmount:null, targetCurrency:null, date:monoDateStr(r.time), comment, monobankId,
  };
}

async function fetchMonobankStatementChunked(accountId, walletId, fromSec, toSec, token){
  const collected=[];
  let windowStart=fromSec, chunks=0;
  while(windowStart<toSec && chunks<MAX_SYNC_CHUNKS_PER_ACCOUNT){
    const windowEnd=Math.min(toSec, windowStart+MONOBANK_MAX_WINDOW_SEC);
    await paceMonobankRequest();
    const rows=await monobankApiRequest('statement', {account:accountId, from:windowStart, to:windowEnd}, token);
    (Array.isArray(rows)?rows:[]).forEach(r=>{
      const tx=buildTransactionFromMonobankEntry(r, walletId);
      if(tx) collected.push(tx);
    });
    windowStart=windowEnd;
    chunks++;
  }
  return collected;
}

const syncMonobankUI=async function(){
  if(!canEditActiveProfile()){ showToast(tr('shared_profile_readonly'),'xmark'); return; }
  const mono=AppState.integrations.monobank;
  if(!mono) return;
  const entries=Object.entries(mono.mapping);
  if(!entries.length){ showToast(tr('monobank_no_accounts'),'xmark'); return; }
  const btn=document.getElementById('monobank-sync-btn');
  const statusEl=document.getElementById('monobank-sync-status');
  if(btn) btn.disabled=true;
  const nowSec=Math.floor(Date.now()/1000);
  const fromSec=mono.lastSyncAt||(nowSec-DEFAULT_SYNC_LOOKBACK_SEC);
  let totalImported=0, hadError=false;
  try{
    for(let i=0;i<entries.length;i++){
      const [accountId, walletId]=entries[i];
      // Monobank's 60s-per-request rate limit means a multi-account sync
      // can genuinely take minutes — this status line is the only feedback
      // the user has that it's still working, not stuck.
      if(statusEl) statusEl.textContent=`${tr('monobank_syncing_prefix')} ${i+1}/${entries.length}…`;
      const newTxs=await fetchMonobankStatementChunked(accountId, walletId, fromSec, nowSec, mono.token);
      if(newTxs.length){
        await batchWriteTransactions(newTxs);
        AppState.transactions.unshift(...newTxs);
        totalImported+=newTxs.length;
      }
    }
    mono.lastSyncAt=nowSec;
  }catch(err){
    console.error(err);
    hadError=true;
    showToast(monobankErrorMessage(err),'xmark');
  }
  const txKey=lsKey('tx'); if(txKey) setCacheItem(txKey, JSON.stringify(AppState.transactions));
  saveConfigLocal(); scheduleSave();
  renderFinance(); renderFinanceChart();
  renderMonobankUI();
  if(btn) btn.disabled=false;
  if(statusEl) statusEl.textContent='';
  if(!hadError) showToast(`${tr('monobank_sync_done_prefix')} ${totalImported}`,'check');
};

const disconnectMonobankUI=async function(){
  if(!canEditActiveProfile()){ showToast(tr('shared_profile_readonly'),'xmark'); return; }
  if(!AppState.integrations.monobank) return;
  if(!(await uiConfirm(tr('monobank_disconnect_confirm'), {title:tr('monobank_disconnect_title'), okText:tr('common_delete'), danger:true}))) return;
  AppState.integrations.monobank=null;
  saveConfigLocal(); scheduleSave();
  renderMonobankUI();
  showToast(tr('monobank_disconnected'),'trash');
};

function renderMonobankUI(){
  const mono=AppState.integrations.monobank;
  const formEl=document.getElementById('monobank-form');
  const connectedEl=document.getElementById('monobank-connected');
  if(formEl) formEl.style.display=mono?'none':'';
  if(connectedEl) connectedEl.style.display=mono?'':'none';
  if(!mono) return;
  const nameEl=document.getElementById('monobank-client-name');
  if(nameEl) nameEl.textContent=mono.clientName||tr('monobank_connected_generic');
  const listEl=document.getElementById('monobank-accounts-list');
  if(listEl){
    listEl.innerHTML=mono.accounts.map(a=>{
      const wallet=AppState.wallets.find(w=>w.id===mono.mapping[a.id]);
      return `<div class="mgr-row" style="cursor:default">
        <div class="mgr-name-inline">${escapeHtml(a.label)}</div>
        <div style="font-size:13px;color:var(--muted2);flex:0 0 auto">${escapeHtml(wallet?wallet.name:'')}</div>
      </div>`;
    }).join('');
  }
  const lastSyncEl=document.getElementById('monobank-last-sync');
  if(lastSyncEl) lastSyncEl.textContent=mono.lastSyncAt ? new Date(mono.lastSyncAt*1000).toLocaleString(window.currentLang==='en'?'en-US':'uk-UA') : tr('monobank_never_synced');
}

const openMonobankManagerUI=function(){
  renderMonobankUI();
  const modal=document.getElementById('monobank-modal');
  if(modal) modal.style.display='flex';
};

// Top-level statements that DO something immediately are deferred into
// this function and called from app.js only after every module in the
// import graph has finished evaluating — see CLAUDE.md's "js/ module
// layout" point 2.
export function __init_monobank__(){
const CLICK_ACTIONS={
  'open-monobank-manager': ()=>openMonobankManagerUI(),
  'connect-monobank': ()=>connectMonobankUI(),
  'sync-monobank': ()=>syncMonobankUI(),
  'disconnect-monobank': ()=>disconnectMonobankUI(),
};
document.addEventListener('click', e=>{
  const el=e.target.closest('[data-action]');
  if(el && CLICK_ACTIONS[el.dataset.action]) CLICK_ACTIONS[el.dataset.action](el.dataset, e);
}, true);
}
