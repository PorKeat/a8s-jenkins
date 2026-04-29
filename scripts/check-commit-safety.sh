#!/usr/bin/env bash
set -euo pipefail

if command -v rg >/dev/null 2>&1; then
  SEARCH_CMD=(rg -n)
else
  SEARCH_CMD=(grep -En)
fi

TRACKED_FILES=$(git ls-files)

if [ -z "${TRACKED_FILES}" ]; then
  echo "No tracked files found."
  exit 0
fi

if "${SEARCH_CMD[@]}" 'sqa_[A-Za-z0-9]+' ${TRACKED_FILES} >/dev/null; then
  echo "Tracked files contain a live SonarQube token. Move it to config-secret.yml or redact it."
  exit 1
fi

if "${SEARCH_CMD[@]}" 'BEGIN RSA PRIVATE KEY|MIIJ[A-Za-z0-9+/=]+|b3BlbnNzaC1rZXktdjE[A-Za-z0-9+/=]*' ${TRACKED_FILES} >/dev/null; then
  echo "Tracked files contain a live SSH private key. Move it to config-secret.yml or redact it."
  exit 1
fi

if "${SEARCH_CMD[@]}" 'github_pat_[A-Za-z0-9_]+' ${TRACKED_FILES} >/dev/null; then
  echo "Tracked files contain a live GitHub PAT. Move it to config-secret.yml or redact it."
  exit 1
fi

echo "Tracked files are safe to commit. Keep live secrets only in config-secret.yml."
