#!/usr/bin/env bash
set -euo pipefail

if rg -n 'sqa_[A-Za-z0-9]+' config.yml >/dev/null; then
  echo "config.yml contains a live SonarQube token. Move it to config-secret.yml."
  exit 1
fi

if rg -n 'BEGIN RSA PRIVATE KEY|MIIJ[A-Za-z0-9+/=]+' config.yml >/dev/null; then
  echo "config.yml contains a live SSH private key. Move it to config-secret.yml."
  exit 1
fi

echo "config.yml is safe to commit. Keep live secrets only in config-secret.yml."
