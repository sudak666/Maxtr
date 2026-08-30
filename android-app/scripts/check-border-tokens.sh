#!/usr/bin/env bash
# Guards against the exact regression fixed in PR #476 (see CHANGELOG.md /
# CLAUDE.md's "Border/outline consistency" note): a `.border()`/`BorderStroke()`
# call reaching for `colorScheme.onSurfaceVariant` -- the app's muted-BODY-TEXT
# color, never meant for chrome -- instead of the actual purpose-built border
# tokens (`colorScheme.outline`, a real hairline tone since Theme.kt's
# DarkHairline/LightHairline change; or the even-fainter `outlineVariant`).
# `onSurfaceVariant` showing up in a border call is exactly how 3 separate
# fields/cards drifted back to a too-bold border after PR #472's original
# contrast fix, each patched independently before finally being reconciled
# in PR #476. `colorScheme.outline`/`outlineVariant` and any accent/semantic
# color (selection state, category color, press feedback) are correct,
# intended border colors and are unaffected -- only `onSurfaceVariant` is
# flagged.
#
# Run from android-app/: ./scripts/check-border-tokens.sh
set -euo pipefail
cd "$(dirname "$0")/.."

violations=0

while IFS= read -r -d '' file; do
  case "$file" in
    */ui/theme/Theme.kt) continue ;; # the one file allowed to define these tokens
  esac
  if ! perl -0777 -ne '
    my $viol = 0;
    # Two shapes of the same bug: a raw Modifier.border()/BorderStroke()
    # call, or an OutlinedTextField unfocusedBorderColor override -- both
    # are how the 3 PR #476 regressions actually looked in the diff.
    while (/\.border\(|BorderStroke\(|unfocusedBorderColor/g) {
      my $start = pos($_);
      my $window = substr($_, $start, 220);
      if ($window =~ /colorScheme\.onSurfaceVariant\b/) {
        $viol = 1;
        last;
      }
    }
    exit($viol ? 1 : 0);
  ' "$file"; then
    echo "::error file=$file::colorScheme.onSurfaceVariant (muted body-text brightness) used directly in a .border()/BorderStroke() call. This is exactly the bug fixed in PR #476 (3 separate fields/cards drifted this way after PR #472). Use colorScheme.outline (the app's real hairline border tone) or outlineVariant instead."
    violations=1
  fi
done < <(find app/src/main/java -name "*.kt" -print0)

if [[ "$violations" -eq 1 ]]; then
  exit 1
fi
echo "Border-token consistency check passed."
