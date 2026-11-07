# docs/

Reference material for people and agents working on Rytm. Nothing here is shipped: Firebase Hosting ignores `docs/**`, and the Vite build never reads it.

| Folder | What is inside |
|---|---|
| `changelog/` | `CHANGELOG.md` archive, one file per month (`2026-07.md` …). The current month stays in the root `CHANGELOG.md`. |
| `audits/` | Audit reports and their status tables: `AUDIT_2026-10.md`, `AUDIT_2026-11.md`, `AUDIT_2026-11-first-run.md`, `DESIGN_AUDIT_2026-08-24.md`. |
| `android/` | Android screen specs (`FINANCE_SCREEN_SPEC.md`, `SHIFTS_SCREEN_SPEC.md`) and `QA_MATRIX.md`. Code comments cite them by file name. |
| `history/` | Plans that are done and kept for their reasoning (`MIGRATION_PLAN_transactions.md`). |
| `icons/` | Offline icon libraries for drawing new glyphs: `lucide/` (stroke, native 24×24) and `material-symbols/` (filled). Each has its own README. |

The live state of the project lives at the top of [`../ANDROID_MIGRATION.md`](../ANDROID_MIGRATION.md) (session checkpoint) and in [`../CLAUDE.md`](../CLAUDE.md).
