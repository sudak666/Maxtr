// One-time, non-destructive backfill: copies each account's transactions
// from finance.data (an array on the big finance doc) into a new
// `transactions` subcollection under that same doc, WITHOUT touching or
// deleting the original array. See ../MIGRATION_PLAN_transactions.md for
// the full history — the migration itself is now DONE (a direct cutover,
// not the originally-planned phased rollout this script was written for)
// and js/color-picker.js's fbLoadNow() now does this same copy
// automatically, once per account/profile, the next time it loads. This
// script is optional from here on: still useful for pre-migrating many
// accounts in bulk ahead of time rather than waiting on each one's next
// app load, but no longer a required step.
//
// This is a manual operator script, not wired into any automated
// deploy/CI — run it yourself with real Firebase Admin credentials:
//
//   cd scripts && npm install firebase-admin
//   GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json node backfill-transactions.mjs --dry-run
//   # review the output, then, if it looks right:
//   GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json node backfill-transactions.mjs
//
// Flags:
//   --dry-run       Count and print what would be written; writes nothing.
//                    This is the default if no flags are given at all.
//   --apply         Actually perform the writes (required to write anything).
//   --uid=<uid>     Limit to a single account, for a safe test run first.
//
// Idempotent: transaction ids are the same Date.now()-based ids already
// used in the array, so re-running after a partial failure just
// overwrites the same subcollection doc ids rather than duplicating.
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

const args = process.argv.slice(2);
const APPLY = args.includes('--apply');
const uidFilter = args.find((a) => a.startsWith('--uid='))?.split('=')[1];

if (!APPLY) {
  console.log('[dry-run mode] pass --apply to actually write. Nothing will be written this run.\n');
}

initializeApp({ credential: applicationDefault() });
const db = getFirestore();

const DCOL = 'max_tracker';

// Mirrors the client's userDoc()/financeDocName() convention: default
// profile keeps the unsuffixed doc name, others are "finance@<profileId>".
function financeDocNameFor(profileId) {
  return profileId && profileId !== 'default' ? `finance@${profileId}` : 'finance';
}

async function profileIdsFor(uid) {
  const metaSnap = await db.doc(`users/${uid}/${DCOL}/profiles_meta`).get();
  if (metaSnap.exists && Array.isArray(metaSnap.data().list) && metaSnap.data().list.length) {
    return metaSnap.data().list.map((p) => p.id).filter(Boolean);
  }
  return ['default'];
}

async function backfillOneFinanceDoc(uid, financeDocName) {
  const financeRef = db.doc(`users/${uid}/${DCOL}/${financeDocName}`);
  const financeSnap = await financeRef.get();
  if (!financeSnap.exists) return { skipped: true };

  const transactions = Array.isArray(financeSnap.data().data) ? financeSnap.data().data : [];
  if (!transactions.length) return { count: 0 };

  const withId = transactions.filter((t) => t && t.id != null);
  const missingId = transactions.length - withId.length;
  if (missingId) {
    console.warn(`  ! ${uid}/${financeDocName}: ${missingId} transaction(s) with no id — skipped, needs manual review`);
  }

  if (!APPLY) {
    return { count: withId.length };
  }

  // Batched writes, 400 at a time (Firestore batch limit is 500; leaving
  // headroom) — a large history shouldn't blow past the batch write limit.
  const subcol = financeRef.collection('transactions');
  let written = 0;
  for (let i = 0; i < withId.length; i += 400) {
    const batch = db.batch();
    const chunk = withId.slice(i, i + 400);
    for (const tx of chunk) {
      batch.set(subcol.doc(String(tx.id)), tx);
    }
    await batch.commit();
    written += chunk.length;
  }

  // Verify: subcollection doc count matches what we intended to write.
  const verifySnap = await subcol.count().get();
  const actualCount = verifySnap.data().count;
  if (actualCount < written) {
    console.error(`  ! ${uid}/${financeDocName}: wrote ${written} but subcollection only has ${actualCount} — investigate before trusting this migration`);
  }

  return { count: written };
}

async function main() {
  const uids = uidFilter ? [uidFilter] : await listAllUids();
  console.log(`Processing ${uids.length} account(s)${uidFilter ? ' (filtered by --uid)' : ''}...\n`);

  let totalTx = 0;
  let totalAccounts = 0;
  for (const uid of uids) {
    const profileIds = await profileIdsFor(uid);
    for (const profileId of profileIds) {
      const docName = financeDocNameFor(profileId);
      const result = await backfillOneFinanceDoc(uid, docName);
      if (result.skipped) continue;
      if (result.count > 0) {
        console.log(`  ${uid}/${docName}: ${result.count} transaction(s)${APPLY ? ' written' : ' would be written'}`);
        totalTx += result.count;
        totalAccounts++;
      }
    }
  }

  console.log(`\n${APPLY ? 'Wrote' : 'Would write'} ${totalTx} transaction(s) across ${totalAccounts} finance doc(s).`);
  if (!APPLY) console.log('Re-run with --apply to actually perform the writes.');
}

// Firestore Admin SDK has no direct "list all users" call scoped to this
// collection layout — accounts are discovered via Firebase Auth, not
// Firestore, since uids aren't enumerable from a collection group query
// without knowing the parent doc names in advance. listUsers() paginates
// in batches of up to 1000.
async function listAllUids() {
  const { getAuth } = await import('firebase-admin/auth');
  const auth = getAuth();
  const uids = [];
  let pageToken;
  do {
    const page = await auth.listUsers(1000, pageToken);
    uids.push(...page.users.map((u) => u.uid));
    pageToken = page.pageToken;
  } while (pageToken);
  return uids;
}

main().catch((err) => {
  console.error('Backfill failed:', err);
  process.exitCode = 1;
});
