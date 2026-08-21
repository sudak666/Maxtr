package ua.rytm.app.data

// The default profile ID — mirrors js/state.js's default `activeProfileId`
// ('default'). Every doc-name/local-storage-key helper across the PWA
// (userDoc()/lsKey() in js/firebase-sync.js) special-cases this exact string
// to mean "no suffix", confirmed by reading both functions.
const val DEFAULT_PROFILE_ID = "default"

// Mirrors js/firebase-sync.js's userDoc(name): the default profile's docs
// keep their bare base name (zero migration for the very first profile
// every account already has); any other profile's docs get an `@<profileId>`
// suffix. Every *SyncRepository's doc-ref builder goes through this so a
// profile switch is "which doc name" and nothing else — no per-repository
// special-casing.
fun profileDocName(base: String, profileId: String): String =
    if (profileId == DEFAULT_PROFILE_ID) base else "$base@$profileId"
