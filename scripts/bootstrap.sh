#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "[bootstrap] sync contract models"
(cd "$ROOT_DIR" && ./scripts/sync-models.sh)

echo "[bootstrap] backend package compile"
(cd "$ROOT_DIR/backend" && mvn -q -Dmaven.test.skip=true package)

echo "[bootstrap] frontend npm install"
(cd "$ROOT_DIR/frontend" && npm install)

if [[ "$(uname -s)" == "Darwin" ]]; then
  echo "[bootstrap] frontend bundler and pods"
  (cd "$ROOT_DIR/frontend" && bundle install)
  (cd "$ROOT_DIR/frontend" && bundle exec pod install)
fi

echo "[bootstrap] done"
