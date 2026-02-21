#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/.dev-pids"
LOG_DIR="$ROOT_DIR/.dev-logs"

# ── Kill application processes ───────────────────────────────────────────────

if [[ -f "$PID_FILE" ]]; then
  echo "==> Stopping application services..."
  while read -r pid name; do
    if kill -0 "$pid" 2>/dev/null; then
      # Kill child processes first, then the parent
      pkill -P "$pid" 2>/dev/null || true
      kill "$pid" 2>/dev/null || true
      echo "    Stopped $name (pid $pid)"
    else
      echo "    $name (pid $pid) already stopped"
    fi
  done < "$PID_FILE"
  rm -f "$PID_FILE"
else
  echo "==> No PID file found, skipping application services."
fi

# ── Tear down Docker Compose stacks (reverse order) ─────────────────────────

echo "==> Stopping auth stack..."
docker compose -f "$ROOT_DIR/infra/docker-compose.auth.yml" down

echo "==> Stopping observability stack..."
docker compose -f "$ROOT_DIR/infra/docker-compose.observability.yml" down

echo "==> Stopping infrastructure..."
docker compose -f "$ROOT_DIR/infra/docker-compose.infra.yml" down

# ── Cleanup ──────────────────────────────────────────────────────────────────

rm -rf "$LOG_DIR"

echo ""
echo "Kinetix dev stack stopped."
