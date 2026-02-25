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

echo "==> Ensuring databases exist..."
docker exec kinetix-postgres psql -U kinetix -f /docker-entrypoint-initdb.d/01-create-databases.sql 2>/dev/null
echo "    Databases ready."

# ── Phase 2: Kafka topics ───────────────────────────────────────────────────

echo "==> Creating Kafka topics..."
topics=("trades.lifecycle" "price.updates" "risk.results" "rates.yield-curves" "rates.risk-free" "rates.forwards" "reference-data.dividends" "reference-data.credit-spreads" "volatility.surfaces" "correlation.matrices")
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

export KINETIX_DEV_MODE=true
export OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
export OTEL_LOGS_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=none   # keep Micrometer/Prometheus scrape
export OTEL_TRACES_EXPORTER=none    # not enabled yet

# Build all Kotlin services in a single Gradle invocation
echo "==> Building all Kotlin services..."
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

# Launch Kotlin services via installDist binaries (no Gradle daemon overhead)
KOTLIN_SERVICES="gateway:8080 position-service:8081 price-service:8082 risk-orchestrator:8083 audit-service:8084 regulatory-service:8085 notification-service:8086 rates-service:8088 reference-data-service:8089 volatility-service:8090 correlation-service:8091"

for entry in $KOTLIN_SERVICES; do
  module="${entry%%:*}"
  port="${entry##*:}"
  echo "==> Starting $module on port $port..."
  OTEL_SERVICE_NAME="$module" \
    "$ROOT_DIR/$module/build/install/$module/bin/$module" -port="$port" \
    > "$LOG_DIR/${module}.log" 2>&1 &
  echo "$! $module" >> "$PID_FILE"
done

# Python risk engine
echo "==> Starting risk-engine..."
(cd "$ROOT_DIR/risk-engine" && PYTHONPATH="$ROOT_DIR/risk-engine/src/kinetix_risk/proto:${PYTHONPATH:-}" uv run python -m kinetix_risk.server) \
  > "$LOG_DIR/risk-engine.log" 2>&1 &
echo "$! risk-engine" >> "$PID_FILE"

# Wait for gateway before starting UI (avoids proxy errors on first load)
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
printf "  %-22s %s\n" "Price Service"        "http://localhost:8082"
printf "  %-22s %s\n" "Risk Orchestrator"    "http://localhost:8083"
printf "  %-22s %s\n" "Audit Service"        "http://localhost:8084"
printf "  %-22s %s\n" "Regulatory Service"   "http://localhost:8085"
printf "  %-22s %s\n" "Notification Service" "http://localhost:8086"
printf "  %-22s %s\n" "Rates Service"        "http://localhost:8088"
printf "  %-22s %s\n" "Reference Data Svc"   "http://localhost:8089"
printf "  %-22s %s\n" "Volatility Service"   "http://localhost:8090"
printf "  %-22s %s\n" "Correlation Service"  "http://localhost:8091"
printf "  %-22s %s\n" "Risk Engine (gRPC)"   "localhost:50051"
printf "  %-22s %s\n" "UI"                   "http://localhost:5173"
printf "  %-22s %s\n" "Grafana"              "http://localhost:3000"
printf "  %-22s %s\n" "Prometheus"           "http://localhost:9090"
printf "  %-22s %s\n" "Keycloak"             "http://localhost:8180"
echo ""
echo "  Logs: tail -f $LOG_DIR/<service>.log"
echo "  Stop: ./dev-down.sh"
echo ""
