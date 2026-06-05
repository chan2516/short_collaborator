#!/bin/bash
# Server-side deploy script — called by GitHub Actions or manually.
#
# Manual usage:
#   sudo ./deploy.sh /path/to/url-shortener-1.0.0.jar

set -euo pipefail

NEW_JAR="${1:?Usage: deploy.sh <path-to-new-jar>}"
APP_DIR="/opt/url-shortener"
JAR_NAME="url-shortener-1.0.0.jar"
SERVICE_NAME="url-shortener"
BACKUP_DIR="${APP_DIR}/backups"

echo "==> Deploying $(basename "$NEW_JAR")..."

mkdir -p "$BACKUP_DIR"

if [ -f "${APP_DIR}/${JAR_NAME}" ]; then
  TIMESTAMP=$(date +%Y%m%d_%H%M%S)
  cp "${APP_DIR}/${JAR_NAME}" "${BACKUP_DIR}/${TIMESTAMP}_${JAR_NAME}"
  echo "    Backed up previous JAR to ${BACKUP_DIR}/${TIMESTAMP}_${JAR_NAME}"
fi

cp "$NEW_JAR" "${APP_DIR}/${JAR_NAME}"
chown urlshortener:urlshortener "${APP_DIR}/${JAR_NAME}"

systemctl restart "$SERVICE_NAME"

echo "==> Waiting for service to start..."
sleep 5

if systemctl is-active --quiet "$SERVICE_NAME"; then
  echo "==> Deploy successful! Service is running."
  curl -sf http://localhost:8080/ > /dev/null && echo "    Health check: OK" || echo "    Health check: WARN (app may still be starting)"
else
  echo "==> ERROR: Service failed to start. Rolling back..."
  LATEST_BACKUP=$(ls -t "${BACKUP_DIR}"/*_"${JAR_NAME}" 2>/dev/null | head -1)
  if [ -n "$LATEST_BACKUP" ]; then
    cp "$LATEST_BACKUP" "${APP_DIR}/${JAR_NAME}"
    systemctl restart "$SERVICE_NAME"
    echo "    Rolled back to: $LATEST_BACKUP"
  fi
  exit 1
fi
