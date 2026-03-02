#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/.dev-pids"
LOG_DIR="$ROOT_DIR/.dev-logs"

# ── Dev-mode guard ───────────────────────────────────────────────────────────

if [[ "${KINETIX_DEV_MODE:-}" != "true" ]]; then
  echo "ERROR: dev-nuke.sh can only be run in dev mode."
  echo ""
  echo "  export KINETIX_DEV_MODE=true"
  echo "  ./dev-nuke.sh"
  echo ""
  echo "This safeguard prevents accidental data loss in non-dev environments."
  exit 1
fi

# ── Confirmation ─────────────────────────────────────────────────────────────

echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║                    KINETIX DEV NUKE                            ║"
echo "║                                                                ║"
echo "║  This will PERMANENTLY DESTROY all dev environment state:      ║"
echo "║                                                                ║"
echo "║  DATABASES (PostgreSQL / TimescaleDB)                          ║"
echo "║    - All 11 service databases (positions, prices, trades...)   ║"
echo "║    - Keycloak database (users, roles, sessions)                ║"
echo "║    - All Flyway migration history                              ║"
echo "║                                                                ║"
echo "║  KAFKA                                                         ║"
echo "║    - All topics and consumer offsets                           ║"
echo "║    - All messages (trades, prices, risk results, DLQs)         ║"
echo "║                                                                ║"
echo "║  REDIS                                                         ║"
echo "║    - All cached data (VaR cache, sessions)                     ║"
echo "║                                                                ║"
echo "║  OBSERVABILITY                                                 ║"
echo "║    - Prometheus metrics and time-series data                   ║"
echo "║    - Grafana dashboards, users, and preferences                ║"
echo "║    - Loki log indexes and stored logs                          ║"
echo "║    - Tempo trace data                                          ║"
echo "║                                                                ║"
echo "║  LOCAL FILES                                                   ║"
echo "║    - Application log files (.dev-logs/)                        ║"
echo "║    - PID tracking file (.dev-pids)                             ║"
echo "║                                                                ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""
read -r -p "Type 'nuke' to confirm: " confirmation

if [[ "$confirmation" != "nuke" ]]; then
  echo "Aborted."
  exit 0
fi

echo ""

# ── Stop application services ────────────────────────────────────────────────

if [[ -f "$PID_FILE" ]]; then
  echo "==> Stopping application services..."
  while read -r pid name; do
    if kill -0 "$pid" 2>/dev/null; then
      pkill -P "$pid" 2>/dev/null || true
      kill "$pid" 2>/dev/null || true
      echo "    Stopped $name (pid $pid)"
    else
      echo "    $name (pid $pid) already stopped"
    fi
  done < "$PID_FILE"
fi

# ── Tear down Docker stacks and destroy volumes ─────────────────────────────

echo "==> Tearing down auth stack + volumes..."
docker compose -f "$ROOT_DIR/infra/docker-compose.auth.yml" down -v 2>/dev/null || true

echo "==> Tearing down observability stack + volumes..."
docker compose -f "$ROOT_DIR/infra/docker-compose.observability.yml" down -v 2>/dev/null || true

echo "==> Tearing down infrastructure + volumes..."
docker compose -f "$ROOT_DIR/infra/docker-compose.infra.yml" down -v 2>/dev/null || true

# ── Clean up local files ─────────────────────────────────────────────────────

echo "==> Removing local state files..."
rm -rf "$LOG_DIR"
rm -f "$PID_FILE"
echo "    Removed .dev-logs/ and .dev-pids"

# ── Done ─────────────────────────────────────────────────────────────────────

echo ""
echo "All dev state destroyed. Run ./dev-up.sh to start fresh."
