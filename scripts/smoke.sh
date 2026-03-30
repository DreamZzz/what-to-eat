#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "[smoke] backend compile"
(cd "$ROOT_DIR/backend" && mvn -q -Dmaven.test.skip=true package)

if [[ -d "$ROOT_DIR/frontend/node_modules" ]]; then
  echo "[smoke] frontend lint"
  (cd "$ROOT_DIR/frontend" && npm run lint)
  echo "[smoke] frontend test"
  (cd "$ROOT_DIR/frontend" && npm test -- --runInBand)
else
  echo "[smoke] frontend dependencies missing, skipped lint/test"
fi
