# Android parity QA matrix

Verified target: `Pixel_10_Pro_XL`, Android API 37.1, Compose BOM 2026.08.00.

| Area | Automated evidence |
|---|---|
| Light/dark + uk/en | SHA-256 screenshot goldens at fixed 360x640 viewport (`OnboardingGoldenTest`) |
| Compact/large font | 320x568 at font scale 1.3 (`ResponsiveLayoutTest`) |
| Landscape | 640x360 (`ResponsiveLayoutTest`) |
| Accessibility | merged checkbox roles, disabled viewer semantics, ≥48dp targets, WCAG AA palette contrast |
| Reduced motion | animated progress vs immediate snap under `LocalReducedMotion` |
| Swipe | exact 30dp threshold/60dp reveal contracts; editable/viewer Shopping and Debt interaction tests |
| Loading/error/offline | deterministic production state components (`ScreenStateFixtureTest`) |
| Calendar | Monday-first, leap year, weekend, empty assignments, timezone/DST independence, multi-shift selection, all four patterns |
| PWA source parity | test reads current `index.html` and compares palette, geometry and interaction timing contracts |
| Localization | exact key/plural parity between `values` and `values-en`; no missing resource fallback |

Golden hashes intentionally target this fixed emulator configuration. Other API levels use semantic, geometry, resource and source-contract tests rather than invalid cross-renderer pixel hashes.
