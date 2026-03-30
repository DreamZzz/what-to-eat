#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

required_commands=(node npm mvn java psql)

for cmd in "${required_commands[@]}"; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[check-env] missing command: $cmd"
    exit 1
  fi
done

echo "[check-env] commands ok"

if [[ ! -f "$ROOT_DIR/backend/.env.example" ]]; then
  echo "[check-env] missing backend/.env.example"
  exit 1
fi

if [[ ! -f "$ROOT_DIR/frontend/.env.example" ]]; then
  echo "[check-env] missing frontend/.env.example"
  exit 1
fi

echo "[check-env] env templates ok"

if [[ ! -f "$ROOT_DIR/contracts/model-sync.config.json" ]]; then
  echo "[check-env] missing contracts/model-sync.config.json"
  exit 1
fi

echo "[check-env] model sync config ok"
