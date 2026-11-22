# Google Play store assets

Uploaded to Play Console → Rytm → default store listing (uk) on 2026-11-22.

- `play-icon-512.png` — store icon, full-bleed graphite square (Play applies its own mask), the launcher mark at ~62% width.
- `play-feature-1024x500.png` — feature graphic.
- `render.mjs` — regenerates both from the launcher vector (`android-app/.../drawable/ic_launcher_foreground.xml`); keep the paths in sync if the mark changes.
- `screenshots/` — phone screenshots, 1080×2160 (2:1), dark theme, Ukrainian, demo data typed through the app on a QA build in an emulator (AVD `Rytm_Store_Shots`, status bar in SystemUI demo mode: 10:00, full battery). Upload order = file name order.

Retake screenshots after a visible UI change. The demo account's credentials live only in the owner's local `android-app/qa-account.local.txt` (git-excluded), never in the repo.
