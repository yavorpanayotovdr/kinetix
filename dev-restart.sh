#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/.dev-pids"
LOG_DIR="$ROOT_DIR/.dev-logs"

usage() {
  echo "Usage: ./dev-restart.sh [service ...]"
  echo ""
  echo "Restart one or more application services (or all if none specified)."
  echo "Infrastructure (Postgres, Kafka, Redis, etc.) is NOT restarted."
  echo ""
  echo "Services:"
  echo "  gateway  position-service  market-data-service  risk-orchestrator"
  echo "  audit-service  regulatory-service  notification-service"
  echo "  risk-engine  ui"
  echo ""
  echo "Examples:"
  echo "  ./dev-restart.sh                    # restart all application services"
  echo "  ./dev-restart.sh gateway            # restart only gateway"
  echo "  ./dev-restart.sh gateway ui         # restart gateway and ui"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ ! -f "$PID_FILE" ]]; then
  echo "ERROR: $PID_FILE not found. Is the dev stack running? Start it with ./dev-up.sh"
  exit 1
fi

# ── Service definitions ──────────────────────────────────────────────────────

# service:port pairs for Gradle services
GRADLE_SERVICES="gateway:8080 position-service:8081 market-data-service:8082 risk-orchestrator:8083 audit-service:8084 regulatory-service:8085 notification-service:8086"
ALL_SERVICES="gateway position-service market-data-service risk-orchestrator audit-service regulatory-service notification-service risk-engine ui"

port_for_service() {
  local svc="$1"
  for entry in $GRADLE_SERVICES; do
    if [[ "${entry%%:*}" == "$svc" ]]; then
      echo "${entry##*:}"
      return 0
    fi
  done
  return 1
}

# Determine which services to restart
if [[ $# -gt 0 ]]; then
  TARGETS=("$@")
  for svc in "${TARGETS[@]}"; do
    found=false
    for valid in $ALL_SERVICES; do
      [[ "$svc" == "$valid" ]] && found=true && break
    done
    if [[ "$found" == false ]]; then
      echo "ERROR: Unknown service '$svc'"
      usage
      exit 1
    fi
  done
else
  TARGETS=($ALL_SERVICES)
fi

# ── Stop selected services ───────────────────────────────────────────────────

echo "==> Stopping services: ${TARGETS[*]}"

REMAINING_PIDS=""
while read -r pid name; do
  should_stop=false
  for target in "${TARGETS[@]}"; do
    [[ "$name" == "$target" ]] && should_stop=true && break
  done

  if [[ "$should_stop" == true ]]; then
    if kill -0 "$pid" 2>/dev/null; then
      pkill -P "$pid" 2>/dev/null || true
      kill "$pid" 2>/dev/null || true
      echo "    Stopped $name (pid $pid)"
    else
      echo "    $name (pid $pid) already stopped"
    fi
  else
    REMAINING_PIDS+="$pid $name"$'\n'
  fi
done < "$PID_FILE"

# Write back the PIDs we didn't stop
printf "%s" "$REMAINING_PIDS" > "$PID_FILE"

# Brief pause for ports to be released
sleep 1

# ── Restart selected services ────────────────────────────────────────────────

export OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_LOGS_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=none
export OTEL_TRACES_EXPORTER=none

mkdir -p "$LOG_DIR"

start_ui=false

for target in "${TARGETS[@]}"; do
  # Gradle services
  port=$(port_for_service "$target" 2>/dev/null || true)
  if [[ -n "$port" ]]; then
    echo "==> Starting $target on port $port..."
    "$ROOT_DIR/gradlew" -p "$ROOT_DIR" ":${target}:run" --args="-port=$port" \
      > "$LOG_DIR/${target}.log" 2>&1 &
    echo "$! $target" >> "$PID_FILE"
    continue
  fi

  # Risk engine
  if [[ "$target" == "risk-engine" ]]; then
    echo "==> Starting risk-engine..."
    (cd "$ROOT_DIR/risk-engine" && PYTHONPATH="$ROOT_DIR/risk-engine/src/kinetix_risk/proto:${PYTHONPATH:-}" uv run python -m kinetix_risk.server) \
      > "$LOG_DIR/risk-engine.log" 2>&1 &
    echo "$! risk-engine" >> "$PID_FILE"
    continue
  fi

  # UI — deferred until gateway is healthy
  if [[ "$target" == "ui" ]]; then
    start_ui=true
    continue
  fi
done

# ── Wait for gateway before starting UI ──────────────────────────────────────

if [[ "$start_ui" == true ]]; then
  echo "==> Waiting for gateway to be healthy..."
  retries=0
  until curl -sf http://localhost:8080/api/v1/system/health >/dev/null 2>&1; do
    retries=$((retries + 1))
    if [[ $retries -ge 60 ]]; then
      echo "    WARNING: Gateway not healthy after 60s, starting UI anyway"
      break
    fi
    sleep 1
  done
  if [[ $retries -lt 60 ]]; then
    echo "    Gateway healthy"
  fi

  echo "==> Starting ui..."
  (cd "$ROOT_DIR/ui" && npm run dev) \
    > "$LOG_DIR/ui.log" 2>&1 &
  echo "$! ui" >> "$PID_FILE"
fi

echo ""
echo "==> Restarted: ${TARGETS[*]}"
echo "    Logs: tail -f $LOG_DIR/<service>.log"
echo ""
