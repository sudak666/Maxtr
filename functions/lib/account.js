// @ts-check
'use strict';

/**
 * Deletes everything this app stores for one account, server-side.
 *
 * Clients used to do this themselves, and missed most of it: only the
 * default profile's three docs (other profiles, profiles_meta, push token,
 * error log, invites stayed), all transactions in ONE batch (Firestore caps a
 * batch at 500 writes, so a bigger history made deletion impossible), and
 * the data went BEFORE the Auth account, so a failed re-login left an
 * account with its data already gone.
 *
 * @param {any} db firebase-admin Firestore
 * @param {string} uid
 */
async function deleteAccountData(db, uid) {
  // Shared profiles this account joined: take it off the owners' member
  // lists so the owner no longer sees a ghost member.
  const meta = await db.doc(`users/${uid}/max_tracker/profiles_meta`).get();
  const list = (meta.exists && Array.isArray(meta.get('list'))) ? meta.get('list') : [];
  for (const p of list) {
    if (!p || p.kind !== 'shared' || typeof p.ownerUid !== 'string' || typeof p.id !== 'string') continue;
    const ref = db.doc(`users/${p.ownerUid}/max_tracker/shared_members@${p.id}`);
    const snap = await ref.get();
    if (!snap.exists) continue;
    const members = (snap.get('members') || []).filter((/** @type {string} */ m) => m !== uid);
    const roles = { ...(snap.get('roles') || {}) };
    delete roles[uid];
    await ref.set({ members, roles, updatedAt: Date.now() }, { merge: true });
  }

  // Every profile, every subcollection (transactions, backups + chunks).
  await db.recursiveDelete(db.doc(`users/${uid}`));

  const invites = await db.collection('profile_invites').where('ownerUid', '==', uid).get();
  await Promise.all([
    ...invites.docs.map((/** @type {any} */ d) => d.ref.delete()),
    db.doc(`push_tokens/${uid}`).delete(),
    db.doc(`error_reports/${uid}`).delete(),
  ]);
}

module.exports = { deleteAccountData };
