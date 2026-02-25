#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> Building all Kotlin service distributions..."
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" \
  :gateway:installDist \
  :position-service:installDist \
  :price-service:installDist \
  :risk-orchestrator:installDist \
  :audit-service:installDist \
  :regulatory-service:installDist \
  :notification-service:installDist \
  :rates-service:installDist \
  :reference-data-service:installDist \
  :volatility-service:installDist \
  :correlation-service:installDist

echo "==> Build complete. Run ./dev-restart.sh to launch services."
