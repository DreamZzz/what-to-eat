#!/usr/bin/env bash

set -euo pipefail

echo "[frontend-check] node: $(node -v)"
echo "[frontend-check] npm: $(npm -v)"

if [[ -f package.json ]]; then
  echo "[frontend-check] package.json found"
else
  echo "[frontend-check] package.json missing"
  exit 1
fi

if [[ -f src/app/navigation/AppNavigator.js ]]; then
  echo "[frontend-check] app navigation found"
else
  echo "[frontend-check] app navigation missing"
  exit 1
fi

if [[ -f src/shared/api/client.js ]]; then
  echo "[frontend-check] shared api client found"
else
  echo "[frontend-check] shared api client missing"
  exit 1
fi
