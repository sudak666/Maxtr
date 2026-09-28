// @ts-check
// Cloud backups: server-side snapshots of one profile's data (shifts/
// finance/debt docs + the finance doc's transactions subcollection), so an
// accidental profile reset, a bad CSV import or a mistaken edit can be
// rolled back to a known date.
//
// Layout (owner's own tree only — shared-profile members never get backups
// of someone else's data, see CLAUDE.md "Cloud backups"):
//   users/{uid}/backups/{backupId}                metadata (client-readable)
//   users/{uid}/backups/{backupId}/chunks/{n}     gzip(JSON) payload slices
// Clients can only READ the metadata docs (firestore.rules); every write
// goes through this module via the Admin SDK (daily schedule + the
// `backups` callable in index.js).
//
// Same "no firebase-admin import" rule as sweep.js: `db` is passed in and
// only the few Firestore members used here are touched, so
// tests/functions-backup.mjs can drive it with an in-memory fake.

const zlib = require('zlib');
const crypto = require('crypto');

const DCOL = 'max_tracker';
const DATA_DOCS = /** @type {const} */ (['shifts', 'finance', 'debt']);
// Firestore's hard doc limit is 1 MiB; leave headroom for the other fields.
const CHUNK_BYTES = 900 * 1024;
const BATCH_OPS = 400;
const KEEP_RECENT = 7;
const KEEP_WEEKLY = 4;
const WEEK_MS = 7 * 24 * 3600 * 1000;
const WEEKLY_MAX_AGE_MS = 5 * WEEK_MS;
const ORPHAN_MAX_AGE_MS = 30 * 24 * 3600 * 1000;
const PROFILE_ID_RE = /^[A-Za-z0-9_]{1,64}$/;

/** @typedef {'daily'|'manual'|'pre_reset'|'pre_import'|'pre_restore'} BackupReason */
/**
 * @typedef {Object} BackupMeta
 * @property {string} profileId
 * @property {number} createdAt
 * @property {BackupReason} reason
 * @property {number} txCount
 * @property {number} bytes
 * @property {number} chunks
 * @property {string} hash
 * @property {number} v
 */
/** @typedef {{id: string, meta: BackupMeta}} BackupEntry */
/** @typedef {(ms: number) => any} FromMillis */

/** @param {string} name @param {string} profileId */
function docName(name, profileId) {
  return profileId === 'default' ? name : `${name}@${profileId}`;
}

/** @param {string} uid @param {string} profileId */
function txPath(uid, profileId) {
  return `users/${uid}/${DCOL}/${docName('finance', profileId)}/transactions`;
}

// Firestore Timestamps (Admin SDK) don't survive JSON on their own — encode
// them as {__ts: millis} and revive on restore. Everything else the app
// stores is plain JSON (numbers, strings, arrays, maps, null).
/** @param {string} _k @param {any} v */
function replacer(_k, v) {
  if (v && typeof v === 'object' && typeof v.toMillis === 'function') return { __ts: v.toMillis() };
  return v;
}

/** @param {any} v @param {FromMillis} fromMillis @returns {any} */
function revive(v, fromMillis) {
  if (Array.isArray(v)) return v.map((x) => revive(x, fromMillis));
  if (v && typeof v === 'object') {
    const keys = Object.keys(v);
    if (keys.length === 1 && keys[0] === '__ts' && typeof v.__ts === 'number') return fromMillis(v.__ts);
    /** @type {Record<string, any>} */
    const out = {};
    for (const k of keys) out[k] = revive(v[k], fromMillis);
    return out;
  }
  return v;
}

/**
 * Reads everything a backup covers for one profile.
 * @param {any} db @param {string} uid @param {string} profileId
 * @returns {Promise<{docs: Record<string, any>, tx: {id: string, data: any}[]}>}
 */
async function readProfile(db, uid, profileId) {
  const [snaps, txSnap] = await Promise.all([
    Promise.all(DATA_DOCS.map((n) => db.doc(`users/${uid}/${DCOL}/${docName(n, profileId)}`).get())),
    db.collection(txPath(uid, profileId)).get(),
  ]);
  /** @type {Record<string, any>} */
  const docs = {};
  DATA_DOCS.forEach((n, i) => { if (snaps[i].exists) docs[n] = snaps[i].data(); });
  const tx = txSnap.docs
    .map((/** @type {any} */ d) => ({ id: d.id, data: d.data() }))
    .sort((/** @type {{id: string}} */ a, /** @type {{id: string}} */ b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  return { docs, tx };
}

// Hash over the content minus volatile sync fields, so a device merely
// re-saving identical data (bumping updatedAt) doesn't defeat the
// "skip if unchanged" check.
/** @param {{docs: Record<string, any>, tx: any[]}} snapshot */
function contentHash(snapshot) {
  /** @type {Record<string, any>} */
  const docs = {};
  for (const [k, v] of Object.entries(snapshot.docs)) {
    const { updatedAt, at, ...rest } = v || {};
    docs[k] = rest;
  }
  return crypto.createHash('sha256').update(JSON.stringify({ docs, tx: snapshot.tx }, replacer)).digest('hex');
}

/** @param {any} db @param {string} uid @returns {Promise<BackupEntry[]>} */
async function listBackups(db, uid) {
  const snap = await db.collection(`users/${uid}/backups`).get();
  return snap.docs.map((/** @type {any} */ d) => ({ id: d.id, meta: d.data() }));
}

/** @param {any} db @param {string} uid @param {string} id */
async function deleteBackup(db, uid, id) {
  const chunks = await db.collection(`users/${uid}/backups/${id}/chunks`).get();
  await Promise.all(chunks.docs.map((/** @type {any} */ d) => d.ref.delete()));
  await db.doc(`users/${uid}/backups/${id}`).delete();
}

/**
 * Retention for one profile's backups: the KEEP_RECENT newest, plus the
 * newest per calendar week (by createdAt) for up to KEEP_WEEKLY older weeks
 * within WEEKLY_MAX_AGE_MS. Returns the ids to delete.
 * @param {BackupEntry[]} entries one profile's backups
 * @param {number} now
 * @returns {string[]}
 */
function idsToPrune(entries, now) {
  const sorted = [...entries].sort((a, b) => b.meta.createdAt - a.meta.createdAt);
  const keep = new Set(sorted.slice(0, KEEP_RECENT).map((e) => e.id));
  const weeks = new Set();
  for (const e of sorted.slice(KEEP_RECENT)) {
    if (now - e.meta.createdAt > WEEKLY_MAX_AGE_MS) continue;
    const week = Math.floor(e.meta.createdAt / WEEK_MS);
    if (weeks.has(week) || weeks.size >= KEEP_WEEKLY) continue;
    weeks.add(week);
    keep.add(e.id);
  }
  return sorted.filter((e) => !keep.has(e.id)).map((e) => e.id);
}

/**
 * Own (non-shared) profile ids of an account; 'default' always included.
 * @param {any} db @param {string} uid @returns {Promise<string[]>}
 */
async function ownProfileIds(db, uid) {
  const snap = await db.doc(`users/${uid}/${DCOL}/profiles_meta`).get();
  const list = (snap.exists && Array.isArray(snap.data().list)) ? snap.data().list : [];
  const ids = new Set(['default']);
  for (const p of list) {
    if (p && typeof p.id === 'string' && p.kind !== 'shared' && PROFILE_ID_RE.test(p.id)) ids.add(p.id);
  }
  return [...ids];
}

/**
 * Snapshot one profile. Skips (returns the latest existing backup) when the
 * content is identical to that profile's newest backup, and skips entirely
 * for a profile with no data at all.
 * @param {any} db @param {string} uid @param {string} profileId
 * @param {BackupReason} reason @param {number} now
 * @param {BackupEntry[]} [existing] pre-fetched listBackups() result
 * @returns {Promise<{status: 'created'|'unchanged'|'empty', id?: string}>}
 */
async function createBackup(db, uid, profileId, reason, now, existing) {
  const snapshot = await readProfile(db, uid, profileId);
  if (Object.keys(snapshot.docs).length === 0 && snapshot.tx.length === 0) return { status: 'empty' };
  const hash = contentHash(snapshot);
  const all = existing || await listBackups(db, uid);
  const mine = all.filter((e) => e.meta.profileId === profileId);
  const latest = mine.reduce((/** @type {BackupEntry|null} */ best, e) => (!best || e.meta.createdAt > best.meta.createdAt ? e : best), null);
  if (latest && latest.meta.hash === hash) return { status: 'unchanged', id: latest.id };

  const gz = zlib.gzipSync(Buffer.from(JSON.stringify(snapshot, replacer), 'utf8'));
  const id = `${profileId}_${now}`;
  const chunkCount = Math.max(1, Math.ceil(gz.length / CHUNK_BYTES));
  for (let i = 0; i < chunkCount; i++) {
    await db.doc(`users/${uid}/backups/${id}/chunks/${i}`).set({ i, data: gz.subarray(i * CHUNK_BYTES, (i + 1) * CHUNK_BYTES) });
  }
  /** @type {BackupMeta} */
  const meta = { profileId, createdAt: now, reason, txCount: snapshot.tx.length, bytes: gz.length, chunks: chunkCount, hash, v: 1 };
  // Metadata last: a half-written backup (crash mid-chunks) is never listed.
  await db.doc(`users/${uid}/backups/${id}`).set(meta);

  const pruneIds = idsToPrune([...mine, { id, meta }], now);
  for (const pid of pruneIds) await deleteBackup(db, uid, pid);
  return { status: 'created', id };
}

/**
 * Daily pass for one account: back up every own profile, then drop backups
 * of profiles that no longer exist once they're older than a month.
 * @param {any} db @param {string} uid @param {number} now
 */
async function backupAccount(db, uid, now) {
  const [profileIds, existing] = await Promise.all([ownProfileIds(db, uid), listBackups(db, uid)]);
  /** @type {Record<string, string>} */
  const results = {};
  for (const pid of profileIds) {
    results[pid] = (await createBackup(db, uid, pid, 'daily', now, existing)).status;
  }
  const live = new Set(profileIds);
  for (const e of existing) {
    if (!live.has(e.meta.profileId) && now - e.meta.createdAt > ORPHAN_MAX_AGE_MS) await deleteBackup(db, uid, e.id);
  }
  return results;
}

/**
 * Restores a backup over its profile's current data. A 'pre_restore'
 * backup of the current state is taken first, so a restore is itself
 * undoable. Not atomic across batches — the pre_restore snapshot is the
 * safety net if it dies half-way.
 * @param {any} db @param {string} uid @param {string} backupId
 * @param {number} now @param {FromMillis} fromMillis
 */
async function restoreBackup(db, uid, backupId, now, fromMillis) {
  if (typeof backupId !== 'string' || !/^[A-Za-z0-9_]{1,100}$/.test(backupId)) throw new BackupError('invalid-argument', 'bad backup id');
  const metaSnap = await db.doc(`users/${uid}/backups/${backupId}`).get();
  if (!metaSnap.exists) throw new BackupError('not-found', 'backup not found');
  /** @type {BackupMeta} */
  const meta = metaSnap.data();
  const profileId = meta.profileId;
  if (!(await ownProfileIds(db, uid)).includes(profileId)) throw new BackupError('failed-precondition', 'profile no longer exists');

  const chunkSnap = await db.collection(`users/${uid}/backups/${backupId}/chunks`).get();
  const parts = chunkSnap.docs.map((/** @type {any} */ d) => d.data()).sort((/** @type {any} */ a, /** @type {any} */ b) => a.i - b.i);
  if (parts.length !== meta.chunks) throw new BackupError('data-loss', 'backup is incomplete');
  const raw = zlib.gunzipSync(Buffer.concat(parts.map((/** @type {any} */ p) => Buffer.from(p.data))));
  /** @type {{docs: Record<string, any>, tx: {id: string, data: any}[]}} */
  const snapshot = JSON.parse(raw.toString('utf8'));
  if (contentHash(snapshot) !== meta.hash) throw new BackupError('data-loss', 'backup checksum mismatch');

  const safety = await createBackup(db, uid, profileId, 'pre_restore', now);

  for (const n of DATA_DOCS) {
    const ref = db.doc(`users/${uid}/${DCOL}/${docName(n, profileId)}`);
    if (snapshot.docs[n]) {
      // Fresh updatedAt so every client's optimistic-concurrency check sees
      // the restored doc as the newest version.
      await ref.set({ ...revive(snapshot.docs[n], fromMillis), updatedAt: now });
    } else {
      await ref.delete();
    }
  }

  const current = await db.collection(txPath(uid, profileId)).get();
  const keepIds = new Set(snapshot.tx.map((t) => t.id));
  /** @type {{op: 'set'|'delete', path: string, data?: any}[]} */
  const ops = [];
  current.docs.forEach((/** @type {any} */ d) => { if (!keepIds.has(d.id)) ops.push({ op: 'delete', path: `${txPath(uid, profileId)}/${d.id}` }); });
  snapshot.tx.forEach((t) => ops.push({ op: 'set', path: `${txPath(uid, profileId)}/${t.id}`, data: revive(t.data, fromMillis) }));
  for (let i = 0; i < ops.length; i += BATCH_OPS) {
    const batch = db.batch();
    for (const o of ops.slice(i, i + BATCH_OPS)) {
      if (o.op === 'delete') batch.delete(db.doc(o.path));
      else batch.set(db.doc(o.path), o.data);
    }
    await batch.commit();
  }
  return { profileId, txCount: snapshot.tx.length, safetyBackupId: safety.id || null };
}

class BackupError extends Error {
  /** @param {string} code @param {string} message */
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

module.exports = {
  createBackup, backupAccount, restoreBackup, listBackups, deleteBackup, ownProfileIds, idsToPrune, contentHash,
  BackupError, KEEP_RECENT, KEEP_WEEKLY,
};
