// @ts-check
// Cloud backups (PWA side) — see CLAUDE.md "Cloud backups" and
// functions/lib/backup.js. Lists the active own profile's backups straight
// from Firestore (owner-readable metadata) and creates/restores through the
// `backups` callable, spoken over plain fetch (callable protocol) so no
// extra Firebase SDK module is needed: same-origin /api/backups rewrite on
// Hosting, the function's own URL on the GitHub Pages mirror.
import { AppState } from './state.js';
import { applyWidgetVisibility, collection, db, getDocs, renderPremiumUI } from './core.js';
import { fbLoadNow, renderProfilesUI } from './color-picker.js';
import { lsKey } from './firebase-sync.js';
import { renderProfileUI } from './goals-profile.js';
import { renderNotifUI } from './notifications.js';
import { showToast, uiConfirm } from './ui-widgets.js';

/** @typedef {{id: string, profileId: string, createdAt: number, reason: string, txCount: number}} BackupInfo */

const HOSTING_HOSTS = ['maxtr-c238f.web.app', 'maxtr-c238f.firebaseapp.com'];
function backupsEndpoint(){
  return HOSTING_HOSTS.includes(location.hostname)
    ? '/api/backups'
    : 'https://us-central1-maxtr-c238f.cloudfunctions.net/backups';
}

class BackupCallError extends Error {
  /** @param {string} status @param {string} message */
  constructor(status, message){ super(message); this.status = status; }
}

/** @param {Record<string, unknown>} data @returns {Promise<any>} */
async function callBackups(data){
  const user = AppState.currentUser;
  if(!user) throw new BackupCallError('UNAUTHENTICATED', 'signed out');
  const token = await user.getIdToken();
  const res = await fetch(backupsEndpoint(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ data }),
  });
  const body = await res.json().catch(() => ({}));
  if(!res.ok || body.error) throw new BackupCallError(body.error?.status || String(res.status), body.error?.message || 'backup call failed');
  return body.result;
}

/** @returns {Promise<BackupInfo[]>} */
async function loadBackups(){
  if(!AppState.currentUser) return [];
  const snap = await getDocs(collection(db, 'users', AppState.currentUser.uid, 'backups'));
  /** @type {BackupInfo[]} */
  const list = [];
  snap.forEach((/** @type {import('firebase/firestore').QueryDocumentSnapshot} */ d) => {
    const m = d.data();
    if(m.profileId === AppState.activeProfileId && typeof m.createdAt === 'number') {
      list.push({ id: d.id, profileId: m.profileId, createdAt: m.createdAt, reason: m.reason || 'daily', txCount: m.txCount || 0 });
    }
  });
  return list.sort((a, b) => b.createdAt - a.createdAt);
}

/** @param {number} ms */
function formatBackupDate(ms){
  return new Date(ms).toLocaleString(window.currentLang === 'en' ? 'en-GB' : 'uk-UA', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });
}

/** @param {string} reason */
function reasonLabel(reason){
  const key = { manual: 'backups_reason_manual', pre_reset: 'backups_reason_pre_reset', pre_import: 'backups_reason_pre_import', pre_restore: 'backups_reason_pre_restore' }[reason];
  return tr(key || 'backups_reason_daily');
}

/** @param {number} n */
function txCountLabel(n){
  if(window.currentLang === 'en') return `${n} ${n === 1 ? 'transaction' : 'transactions'}`;
  const m10 = n % 10, m100 = n % 100;
  const word = (m10 === 1 && m100 !== 11) ? 'операція' : (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) ? 'операції' : 'операцій';
  return `${n} ${word}`;
}

/** @type {BackupInfo[]} */
let lastList = [];
let busy = false;

async function renderBackupsList(){
  const box = document.getElementById('backups-list');
  if(!box) return;
  box.innerHTML = `<div class="chk-desc" style="text-align:center;padding:12px 0">${tr('backups_loading')}</div>`;
  try {
    lastList = await loadBackups();
  } catch(e) {
    console.error(e);
    box.innerHTML = `<div class="chk-desc" style="text-align:center;padding:12px 0">${tr('backups_failed')}</div>`;
    return;
  }
  if(!lastList.length){
    box.innerHTML = `<div class="chk-desc" style="text-align:center;padding:12px 0">${tr('backups_empty')}</div>`;
    return;
  }
  box.innerHTML = '';
  lastList.forEach(b => {
    const row = document.createElement('div');
    row.className = 'mgr-row';
    row.innerHTML = `
      <span class="icon-badge icon-badge-sm" style="--badge-color:var(--green)">${window.Icon('cloud')}</span>
      <div class="settings-row-text" style="min-width:0;flex:1">
        <div class="settings-row-title">${formatBackupDate(b.createdAt)}</div>
        <div class="settings-row-sub">${reasonLabel(b.reason)} · ${txCountLabel(b.txCount)}</div>
      </div>
      <button class="btn btn-ghost" style="padding:6px 12px;font-size:13px;flex:0 0 auto" data-action="restore-backup" data-id="${b.id}">${tr('backups_restore')}</button>
    `;
    box.appendChild(row);
  });
}

function openBackupsManager(){
  if(!AppState.currentUser) return;
  if(AppState.activeProfileOwnerUid){ showToast(tr('backups_shared_unavailable'), 'warning'); return; }
  const modal = document.getElementById('backups-modal');
  if(modal) modal.style.display = 'flex';
  renderBackupsList();
}

/** @param {unknown} e */
function backupErrorText(e){
  return (e instanceof BackupCallError && e.status === 'RESOURCE_EXHAUSTED') ? tr('backups_rate_limited') : tr('backups_failed');
}

async function createBackupNow(){
  if(busy || !AppState.currentUser || AppState.activeProfileOwnerUid) return;
  busy = true;
  const btn = /** @type {HTMLButtonElement | null} */ (document.getElementById('backups-create-btn'));
  if(btn) btn.disabled = true;
  try {
    const r = await callBackups({ action: 'create', profileId: AppState.activeProfileId, reason: 'manual' });
    const key = r?.status === 'unchanged' ? 'backups_unchanged' : r?.status === 'empty' ? 'backups_nothing' : 'backups_created';
    showToast(tr(key), 'cloud');
    await renderBackupsList();
  } catch(e) {
    console.error(e);
    showToast(backupErrorText(e), 'xmark');
  } finally {
    busy = false;
    if(btn) btn.disabled = false;
  }
}

/** @param {string} id */
async function restoreBackupUI(id){
  const b = lastList.find(x => x.id === id);
  if(!b || busy) return;
  const ok = await uiConfirm(tr('backups_restore_body').replace('{date}', formatBackupDate(b.createdAt)), { title: tr('backups_restore_title'), okText: tr('backups_restore') });
  if(!ok) return;
  busy = true;
  showToast(tr('backups_restoring'), 'cloud');
  try {
    await callBackups({ action: 'restore', backupId: id });
  } catch(e) {
    console.error(e);
    showToast(backupErrorText(e), 'xmark');
    busy = false;
    return;
  }
  // Same reload path as resetProfileData(): drop this profile's local cache
  // so nothing stale is served or pushed back, then re-read from Firestore.
  clearTimeout(AppState.fbTimer ?? undefined);
  ['shifts','tx','recurring','debt','cfg'].forEach(n => { const k = lsKey(n); if(k) localStorage.removeItem(k); });
  await fbLoadNow();
  renderProfileUI(); renderPremiumUI(); applyWidgetVisibility(); renderNotifUI(); renderProfilesUI();
  busy = false;
  showToast(tr('backups_restored'), 'cloud');
  await renderBackupsList();
}

/**
 * Best-effort snapshot before a destructive bulk action (reset, CSV import).
 * Never blocks the action: offline or a server hiccup just skips it.
 * @param {'pre_reset'|'pre_import'} reason
 */
export async function safetyBackup(reason){
  if(!AppState.currentUser || AppState.activeProfileOwnerUid) return;
  try {
    await Promise.race([
      callBackups({ action: 'create', profileId: AppState.activeProfileId, reason }),
      new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 20000)),
    ]);
  } catch(e) {
    console.warn('safety backup skipped', reason, e);
  }
}

/** @type {Record<string, (ds: DOMStringMap) => void>} */
const CLICK_ACTIONS = {
  'open-backups-manager': () => openBackupsManager(),
  'create-backup-now': () => { createBackupNow(); },
  'restore-backup': ds => { restoreBackupUI(ds.id || ''); },
};

export function __init_backups__(){
  // Capture phase, same reason as color-picker.js's listener (modal cards
  // stop bubbling clicks).
  document.addEventListener('click', e => {
    const el = /** @type {HTMLElement | null} */ (/** @type {Element} */ (e.target).closest('[data-action]'));
    const action = el && el.dataset.action;
    if(action && CLICK_ACTIONS[action]) CLICK_ACTIONS[action](el.dataset);
  }, true);
}
