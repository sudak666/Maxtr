#!/bin/bash
set -euo pipefail

# Only relevant for Claude Code on the web (ephemeral containers) — a local
# dev machine is expected to use its own `firebase login`.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

if ! command -v firebase >/dev/null 2>&1; then
  npm install -g firebase-tools >/dev/null 2>&1
fi

# Materialize the claude-deploy@maxtr-c238f service account key from the
# FIREBASE_DEPLOY_SA_KEY environment variable (set once in this Claude Code
# Environment's variables, never committed to the repo) so firebase-tools
# can deploy non-interactively. See SETUP.md for what this account is for.
if [ -n "${FIREBASE_DEPLOY_SA_KEY:-}" ]; then
  mkdir -p "$HOME/.config/gcloud-keys"
  key_path="$HOME/.config/gcloud-keys/maxtr-c238f-deploy.json"
  printf '%s' "$FIREBASE_DEPLOY_SA_KEY" > "$key_path"
  chmod 600 "$key_path"
  echo "export GOOGLE_APPLICATION_CREDENTIALS=\"$key_path\"" >> "$CLAUDE_ENV_FILE"
fi
