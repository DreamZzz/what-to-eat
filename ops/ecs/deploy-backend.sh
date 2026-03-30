#!/usr/bin/env bash

set -euo pipefail

APP_ROOT="/opt/what-to-eat/backend"
RELEASE_DIR="$APP_ROOT/current"
JAR_PATH="$RELEASE_DIR/app.jar"
ENV_PATH="$APP_ROOT/shared/.env"
SERVICE_NAME="what-to-eat-backend"

if [ ! -f "$ENV_PATH" ]; then
  echo "Missing environment file: $ENV_PATH"
  exit 1
fi

mkdir -p "$RELEASE_DIR"

echo "Building backend jar..."
./mvnw -q -DskipTests package || mvn -q -DskipTests package

LATEST_JAR="$(ls -t target/*.jar | grep -v '\.original$' | head -n 1)"

if [ -z "$LATEST_JAR" ]; then
  echo "No jar produced in target/"
  exit 1
fi

cp "$LATEST_JAR" "$JAR_PATH"

echo "Restarting systemd service..."
sudo systemctl daemon-reload
sudo systemctl enable "$SERVICE_NAME"
sudo systemctl restart "$SERVICE_NAME"
sudo systemctl status "$SERVICE_NAME" --no-pager
