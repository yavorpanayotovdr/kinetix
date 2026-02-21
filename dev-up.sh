#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/.dev-pids"
LOG_DIR="$ROOT_DIR/.dev-logs"

if [[ -f "$PID_FILE" ]]; then
  echo "ERROR: $PID_FILE already exists. Run ./dev-down.sh first."
  exit 1
fi

mkdir -p "$LOG_DIR"
> "$PID_FILE"

# ── Phase 1: Infrastructure ─────────────────────────────────────────────────

echo "==> Starting infrastructure..."
docker compose -f "$ROOT_DIR/infra/docker-compose.infra.yml" up -d --wait
echo "    Postgres, Kafka, Redis ready."

echo "==> Starting observability stack..."
docker compose -f "$ROOT_DIR/infra/docker-compose.observability.yml" up -d --wait
echo "    Prometheus, Grafana, Loki, Tempo, OTel Collector ready."

echo "==> Starting auth stack..."
docker compose -f "$ROOT_DIR/infra/docker-compose.auth.yml" up -d --wait
echo "    Keycloak ready."

# ── Phase 2: Kafka topics ───────────────────────────────────────────────────

echo "==> Creating Kafka topics..."
topics=("trades.lifecycle" "market.data.prices" "risk.results")
for topic in "${topics[@]}"; do
  docker exec kinetix-kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server localhost:9092 \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1 >/dev/null 2>&1
  echo "    Created topic: $topic"
done

# ── Phase 3: Application services ───────────────────────────────────────────

start_gradle_service() {
  local module="$1"
  echo "==> Starting $module..."
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" ":${module}:run" \
    > "$LOG_DIR/${module}.log" 2>&1 &
  echo "$! $module" >> "$PID_FILE"
}

# Kotlin services (dependency order)
start_gradle_service gateway
start_gradle_service position-service
start_gradle_service market-data-service
start_gradle_service risk-orchestrator
start_gradle_service audit-service
start_gradle_service regulatory-service
start_gradle_service notification-service

# Python risk engine
echo "==> Starting risk-engine..."
(cd "$ROOT_DIR/risk-engine" && uv run python -m kinetix_risk.server) \
  > "$LOG_DIR/risk-engine.log" 2>&1 &
echo "$! risk-engine" >> "$PID_FILE"

# React UI
echo "==> Starting ui..."
(cd "$ROOT_DIR/ui" && npm run dev) \
  > "$LOG_DIR/ui.log" 2>&1 &
echo "$! ui" >> "$PID_FILE"

# ── Phase 4: Summary ────────────────────────────────────────────────────────

echo ""
echo "=============================================="
echo "  Kinetix dev stack is starting up"
echo "=============================================="
echo ""
printf "  %-22s %s\n" "Service" "URL"
printf "  %-22s %s\n" "──────────────────────" "──────────────────────────"
printf "  %-22s %s\n" "Gateway API"          "http://localhost:8080"
printf "  %-22s %s\n" "Position Service"     "http://localhost:8081"
printf "  %-22s %s\n" "Market Data Service"  "http://localhost:8082"
printf "  %-22s %s\n" "Risk Orchestrator"    "http://localhost:8083"
printf "  %-22s %s\n" "Audit Service"        "http://localhost:8084"
printf "  %-22s %s\n" "Regulatory Service"   "http://localhost:8085"
printf "  %-22s %s\n" "Notification Service" "http://localhost:8086"
printf "  %-22s %s\n" "Risk Engine (gRPC)"   "localhost:50051"
printf "  %-22s %s\n" "UI"                   "http://localhost:5173"
printf "  %-22s %s\n" "Grafana"              "http://localhost:3000"
printf "  %-22s %s\n" "Prometheus"           "http://localhost:9090"
printf "  %-22s %s\n" "Keycloak"             "http://localhost:8180"
echo ""
echo "  Logs: tail -f $LOG_DIR/<service>.log"
echo "  Stop: ./dev-down.sh"
echo ""
