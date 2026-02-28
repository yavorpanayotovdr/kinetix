#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/.dev-pids"
LOG_DIR="$ROOT_DIR/.dev-logs"

usage() {
  echo "Usage: ./dev-restart.sh [service ...]"
  echo ""
  echo "Restart one or more application services (or all if none specified)."
  echo "Infrastructure is auto-started if not already healthy."
  echo ""
  echo "Services:"
  echo "  gateway  position-service  price-service  risk-orchestrator"
  echo "  audit-service  regulatory-service  notification-service"
  echo "  rates-service  reference-data-service  volatility-service"
  echo "  correlation-service  risk-engine  ui"
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

# ── Infrastructure pre-flight check ─────────────────────────────────────────

ensure_infra() {
  if ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker daemon is not running. Please start Docker and try again."
    exit 1
  fi

  local infra_ok=true
  for container in kinetix-postgres kinetix-kafka kinetix-redis; do
    if ! docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null | grep -qx healthy; then
      infra_ok=false
      break
    fi
  done

  if [[ "$infra_ok" == false ]]; then
    echo "==> Infrastructure not ready, starting containers..."

    docker compose -f "$ROOT_DIR/infra/docker-compose.infra.yml" up -d --wait
    echo "    Postgres, Kafka, Redis ready."

    echo "==> Ensuring databases exist..."
    docker exec kinetix-postgres psql -U kinetix -f /docker-entrypoint-initdb.d/01-create-databases.sql 2>/dev/null
    echo "    Databases ready."

    docker compose -f "$ROOT_DIR/infra/docker-compose.observability.yml" up -d --wait
    echo "    Prometheus, Grafana, Loki, Tempo, OTel Collector ready."

    docker compose -f "$ROOT_DIR/infra/docker-compose.auth.yml" up -d --wait
    echo "    Keycloak ready."

    echo "==> Ensuring Kafka topics exist..."
    local topics=("trades.lifecycle" "price.updates" "risk.results" "rates.yield-curves" "rates.risk-free" "rates.forwards" "reference-data.dividends" "reference-data.credit-spreads" "volatility.surfaces" "correlation.matrices")
    for topic in "${topics[@]}"; do
      docker exec kinetix-kafka /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server localhost:9092 \
        --create --if-not-exists \
        --topic "$topic" \
        --partitions 3 \
        --replication-factor 1 >/dev/null 2>&1
    done
    echo "    Kafka topics ready."
  fi
}

ensure_infra

# ── Service definitions ──────────────────────────────────────────────────────

# service:port pairs for Kotlin services
GRADLE_SERVICES="gateway:8080 position-service:8081 price-service:8082 risk-orchestrator:8083 audit-service:8084 regulatory-service:8085 notification-service:8086 rates-service:8088 reference-data-service:8089 volatility-service:8090 correlation-service:8091"
ALL_SERVICES="gateway position-service price-service risk-orchestrator audit-service regulatory-service notification-service rates-service reference-data-service volatility-service correlation-service risk-engine ui"

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

# ── Wait for ports to be released ────────────────────────────────────────────

wait_for_port_free() {
  local port="$1"
  local max_wait=50  # 50 * 100ms = 5s
  local i=0
  while lsof -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; do
    i=$((i + 1))
    if [[ $i -ge $max_wait ]]; then
      echo "    WARNING: Port $port still in use after 5s"
      return 0
    fi
    sleep 0.1
  done
}

for target in "${TARGETS[@]}"; do
  port=$(port_for_service "$target" 2>/dev/null || true)
  if [[ -n "$port" ]]; then
    wait_for_port_free "$port"
  fi
done

# ── Rebuild targeted Kotlin services ─────────────────────────────────────────

COMPILE_TASKS=()
INSTALL_TASKS=()
for target in "${TARGETS[@]}"; do
  port=$(port_for_service "$target" 2>/dev/null || true)
  if [[ -n "$port" ]]; then
    COMPILE_TASKS+=(":${target}:compileKotlin")
    INSTALL_TASKS+=(":${target}:installDist")
  fi
done

if [[ ${#INSTALL_TASKS[@]} -gt 0 ]]; then
  echo "==> Compiling and repackaging: ${INSTALL_TASKS[*]}"
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" "${COMPILE_TASKS[@]}" "${INSTALL_TASKS[@]}" --no-build-cache
fi

# ── Launch services ──────────────────────────────────────────────────────────

export KINETIX_DEV_MODE=true
export SIMULATION_DELAYS=true
export OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_LOGS_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=none
export OTEL_TRACES_EXPORTER=none

mkdir -p "$LOG_DIR"

start_ui=false

for target in "${TARGETS[@]}"; do
  # Kotlin services — launch via installDist binary
  port=$(port_for_service "$target" 2>/dev/null || true)
  if [[ -n "$port" ]]; then
    echo "==> Starting $target on port $port..."
    OTEL_SERVICE_NAME="$target" \
      "$ROOT_DIR/$target/build/install/$target/bin/$target" -port="$port" \
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
    if [[ $retries -ge 120 ]]; then
      echo "    WARNING: Gateway not healthy after 60s, starting UI anyway"
      break
    fi
    sleep 0.5
  done
  if [[ $retries -lt 120 ]]; then
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
