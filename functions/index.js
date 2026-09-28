/**
 * Scheduled push-notification sweep for Rytm.
 *
 * The client already does the same three checks (daily reminder, budget
 * exceeded, upcoming recurring payment) locally via local Notifications,
 * but those only fire while the app is open in a browser tab. This
 * function re-runs the same logic server-side on a schedule and sends a
 * real FCM push, so reminders arrive even with the app fully closed.
 *
 * Dedup state (what's already been sent) is stored on each user's
 * push_tokens/{uid} doc, separate from the client's own localStorage
 * dedup keys used for the in-app local notifications.
 *
 * The actual per-user/per-profile sweep logic lives in lib/sweep.js, split
 * out (same reasoning as lib/pure.js) so it's callable from a test with a
 * fake db/sendPush and no real Firestore/FCM credentials — this file just
 * wires up the real Firestore/Messaging clients and the scheduled trigger.
 *
 * Deploy with: firebase deploy --only functions
 * (requires the project to be on the Blaze plan and firebase-tools logged
 * in with access to the project — see ../SETUP.md).
 *
 * Redeployed 2026-07-11 with no logic change, specifically to force
 * firebase-tools past its "no changes detected" skip and re-run the Cloud
 * Scheduler trigger reconciliation step: a direct API check that day found
 * zero Cloud Scheduler jobs in us-central1 for this project, meaning this
 * function's hourly trigger had likely never been (re)created since the
 * claude-deploy@ service account lost (and only that day regained)
 * roles/cloudscheduler.admin — see CLAUDE.md's "Known gaps" for the full
 * incident. The function itself was ACTIVE and serving the whole time;
 * only the thing that's supposed to call it hourly was missing.
 */
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { onRequest, onCall, HttpsError } = require('firebase-functions/v2/https');
const functionsV1 = require('firebase-functions/v1');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { getAuth } = require('firebase-admin/auth');
const logger = require('firebase-functions/logger');
const { mapWithConcurrency } = require('./lib/pure');
const { sweepToken } = require('./lib/sweep');
const backup = require('./lib/backup');

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

// registration-token-not-registered means the token is permanently dead
// (app uninstalled, browser data cleared, push permission revoked) — FCM
// itself will never accept it again, so the caller should stop retrying it
// and delete the push_tokens doc rather than re-attempting (and re-logging
// the same failure) every hour indefinitely.
const TOKEN_INVALID_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
]);
// Per-notification-type themed icon, shown in the notification tray
// instead of the one fixed icon-192.png every push used before. Same-origin
// root-relative paths (see sw.js's STATIC_ASSETS for why these are
// precached alongside every other same-origin asset) — resolved by the
// service worker against its own scope when it calls showNotification(),
// see sw.js's onBackgroundMessage handler for the display side of this.
const NOTIF_ICONS = {
  daily: '/notif-icon-daily.png',
  budget: '/notif-icon-budget.png',
  recurring: '/notif-icon-recurring.png',
  debt: '/notif-icon-debt.png',
};
async function sendPush(token, title, body, type) {
  try {
    const icon = NOTIF_ICONS[type] || '/icon-192.png';
    await messaging.send({ token, notification: { title, body }, webpush: { fcmOptions: { link: '/' }, notification: { icon } } });
    return { ok: true, invalid: false };
  } catch (err) {
    logger.warn('push send failed', { code: err.code, message: err.message });
    return { ok: false, invalid: TOKEN_INVALID_CODES.has(err.code) };
  }
}

// Capped rather than unbounded: an uncapped Promise.allSettled over every
// push_tokens doc fires every user's Firestore reads + FCM send at once,
// which is fine at a handful of users but an uncontrolled burst against
// Firestore/FCM once the account count grows.
const SWEEP_CONCURRENCY = 25;

exports.notificationSweep = onSchedule('every 60 minutes', async () => {
  const tokensSnap = await db.collection('push_tokens').get();
  if (tokensSnap.empty) return;

  const now = new Date();
  const results = await mapWithConcurrency(tokensSnap.docs, SWEEP_CONCURRENCY, (tokenDoc) => sweepToken(db, sendPush, tokenDoc, now, (msg, meta) => logger.info(msg, meta)));
  results.forEach((r, i) => {
    if (r.status === 'rejected') {
      logger.error('sweepToken failed', { uid: tokensSnap.docs[i].id, error: r.reason?.message || String(r.reason) });
    }
  });
});

// Server-side proxy for PrivatBank's cash-exchange-rate API.
//
// The client used to call api.privatbank.ua directly, which always failed —
// that host never sends CORS headers for our origin. A public CORS-relay
// (api.allorigins.win) was tried as a fallback, but turned out unreliable
// for this specific endpoint too (observed intermittent 500s from the
// relay itself, likely PrivatBank rate-limiting/blocking the relay's IP —
// see CLAUDE.md). Proxying through our own Cloud Function avoids CORS
// entirely (this is a server-to-server fetch, not a browser one) and is
// under our own control instead of a third party's.
//
// Exposed to the client via a Hosting rewrite (see firebase.json:
// "/api/privat-rates" -> this function) so the browser sees it as a
// same-origin request — no CORS headers needed on the response at all.
const PRIVAT_RATES_URL = 'https://api.privatbank.ua/p24api/pubinfo?json&exchange&coursid=5';

exports.privatRates = onRequest({ cors: true }, async (req, res) => {
  try {
    const upstream = await fetch(PRIVAT_RATES_URL);
    if (!upstream.ok) {
      res.status(502).json({ error: `PrivatBank HTTP ${upstream.status}` });
      return;
    }
    const data = await upstream.json();
    res.set('Cache-Control', 'public, max-age=300');
    res.status(200).json(data);
  } catch (err) {
    logger.warn('privatRates proxy failed', { message: err.message });
    res.status(502).json({ error: 'PrivatBank fetch failed' });
  }
});


// ── Cloud backups (see lib/backup.js and CLAUDE.md "Cloud backups") ──
// Daily snapshot of every account's own profiles. users/{uid} parent docs
// don't exist (only subcollections do), so listDocuments() — which also
// returns "missing" parents — is the way to enumerate accounts.
const BACKUP_CONCURRENCY = 10;
exports.dailyBackup = onSchedule({ schedule: 'every day 03:30', timeZone: 'Europe/Kyiv', timeoutSeconds: 540, memory: '512MiB' }, async () => {
  const refs = await db.collection('users').listDocuments();
  const now = Date.now();
  const results = await mapWithConcurrency(refs, BACKUP_CONCURRENCY, (ref) => backup.backupAccount(db, ref.id, now));
  let failed = 0;
  results.forEach((r, i) => {
    if (r.status === 'rejected') {
      failed++;
      logger.error('backupAccount failed', { uid: refs[i].id, error: r.reason?.message || String(r.reason) });
    }
  });
  logger.info('dailyBackup done', { accounts: refs.length, failed });
});

// Client entry point (Android via the Functions SDK, PWA via fetch to the
// /api/backups Hosting rewrite — same callable protocol). Only ever acts
// on the caller's own tree, so shared-profile members can't touch the
// owner's backups by construction.
const CLIENT_REASONS = new Set(['manual', 'pre_reset', 'pre_import']);
const MANUAL_MIN_INTERVAL_MS = 20 * 1000;
exports.backups = onCall({ timeoutSeconds: 300, memory: '512MiB' }, async (req) => {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'sign in required');
  const data = req.data || {};
  const now = Date.now();
  try {
    if (data.action === 'create') {
      const profileId = String(data.profileId || 'default');
      const reason = CLIENT_REASONS.has(data.reason) ? data.reason : 'manual';
      if (!(await backup.ownProfileIds(db, uid)).includes(profileId)) throw new HttpsError('permission-denied', 'not your profile');
      const existing = await backup.listBackups(db, uid);
      if (reason === 'manual' && existing.some((e) => e.meta.reason === 'manual' && now - e.meta.createdAt < MANUAL_MIN_INTERVAL_MS)) {
        throw new HttpsError('resource-exhausted', 'too many backups');
      }
      return await backup.createBackup(db, uid, profileId, reason, now, existing);
    }
    if (data.action === 'restore') {
      return await backup.restoreBackup(db, uid, data.backupId, now, (ms) => Timestamp.fromMillis(ms));
    }
    throw new HttpsError('invalid-argument', 'unknown action');
  } catch (err) {
    if (err instanceof HttpsError) throw err;
    if (err instanceof backup.BackupError) throw new HttpsError(/** @type {any} */ (err.code), err.message);
    logger.error('backups callable failed', { uid, action: data.action, error: err.message });
    throw new HttpsError('internal', 'backup failed');
  }
});

// Account deletion: clients delete their own max_tracker data, but backups
// are server-written and client-undeletable, so they go here.
exports.deleteBackupsOnAccountDelete = functionsV1.auth.user().onDelete(async (user) => {
  await db.recursiveDelete(db.collection(`users/${user.uid}/backups`));
});
