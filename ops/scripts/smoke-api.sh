#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:8080}"
BASE_URL="${BASE_URL%/}"

SMOKE_AUTH_USERNAME="${SMOKE_AUTH_USERNAME:-demo_admin}"
SMOKE_AUTH_PASSWORD="${SMOKE_AUTH_PASSWORD:-QuickStart123!}"
SMOKE_AUTH_TOKEN="${SMOKE_AUTH_TOKEN:-}"
SMOKE_VOICE_FILE="${SMOKE_VOICE_FILE:-}"
SMOKE_VOICE_LOCALE="${SMOKE_VOICE_LOCALE:-zh-CN}"

json_get() {
  local key="$1"
  node -e '
const fs = require("fs");
const key = process.argv[1];
const input = fs.readFileSync(0, "utf8");
try {
  const data = JSON.parse(input);
  const value = data && data[key];
  if (value === undefined || value === null) process.exit(2);
  process.stdout.write(String(value));
} catch (error) {
  process.exit(1);
}
' "$key"
}

login_and_get_token() {
  local response token

  response="$(curl -fsS -X POST "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "$(printf '{"username":"%s","password":"%s"}' "$SMOKE_AUTH_USERNAME" "$SMOKE_AUTH_PASSWORD")")"
  token="$(printf '%s' "$response" | json_get token)"
  if [ -z "$token" ]; then
    echo "[smoke-api] login succeeded but token is empty"
    exit 1
  fi
  printf '%s' "$token"
}

require_token() {
  if [ -n "$SMOKE_AUTH_TOKEN" ]; then
    printf '%s' "$SMOKE_AUTH_TOKEN"
    return 0
  fi

  login_and_get_token
}

AUTH_TOKEN="$(require_token)"

curl -fsS "$BASE_URL/api/auth/captcha" >/dev/null

curl -fsS -X POST "$BASE_URL/api/meals/recommendations" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "sourceText": "鸡胸肉、西兰花、米饭",
    "sourceMode": "TEXT",
    "dishCount": 2,
    "totalCalories": 900,
    "staple": "RICE",
    "locale": "zh-CN"
  }' >/dev/null

curl -fsS "$BASE_URL/api/meals/favorites?page=0&size=1" \
  -H "Authorization: Bearer $AUTH_TOKEN" >/dev/null

if [ -n "$SMOKE_VOICE_FILE" ]; then
  if [ ! -f "$SMOKE_VOICE_FILE" ]; then
    echo "[smoke-api] SMOKE_VOICE_FILE not found: $SMOKE_VOICE_FILE"
    exit 1
  fi

  curl -fsS -X POST "$BASE_URL/api/voice/transcriptions" \
    -H "Authorization: Bearer $AUTH_TOKEN" \
    -F "audio=@${SMOKE_VOICE_FILE}" \
    -F "locale=${SMOKE_VOICE_LOCALE}" >/dev/null
else
  echo "[smoke-api] skipping voice transcription smoke; set SMOKE_VOICE_FILE to test multipart upload"
fi

echo "[smoke-api] success for $BASE_URL"
