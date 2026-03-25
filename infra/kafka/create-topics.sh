#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
KAFKA_TOPICS="${KAFKA_TOPICS_CMD:-/opt/kafka/bin/kafka-topics.sh}"
REPLICATION="${REPLICATION_FACTOR:-1}"

# Wait for broker to be ready before creating topics.
wait_for_broker() {
  local max_attempts=30
  local attempt=1
  while [ $attempt -le $max_attempts ]; do
    if $KAFKA_TOPICS --bootstrap-server "$BOOTSTRAP" --list >/dev/null 2>&1; then
      echo "Broker ready after $attempt attempt(s)"
      return 0
    fi
    echo "Waiting for broker (attempt $attempt/$max_attempts)..."
    sleep 2
    attempt=$((attempt + 1))
  done
  echo "ERROR: Broker not ready after $max_attempts attempts"
  exit 1
}

wait_for_broker

# Create a topic with the given name and partition count.
create_topic() {
  local name="$1"
  local partitions="$2"
  echo "Creating topic: $name (partitions=$partitions, replication=$REPLICATION)"
  $KAFKA_TOPICS \
    --bootstrap-server "$BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$name" \
    --partitions "$partitions" \
    --replication-factor "$REPLICATION"
}

# ── Core topics ──────────────────────────────────────────────────────
create_topic "trades.lifecycle"   3
create_topic "price.updates"     6
create_topic "risk.results"      3

# ── Risk ─────────────────────────────────────────────────────────────
create_topic "risk.anomalies"      3
create_topic "risk.audit"          3
create_topic "risk.pnl.intraday"   3
create_topic "risk.regime.changes" 1

# ── Rates ────────────────────────────────────────────────────────────
create_topic "rates.yield-curves" 3
create_topic "rates.risk-free"    3
create_topic "rates.forwards"    3

# ── Reference data ───────────────────────────────────────────────────
create_topic "reference-data.dividends"      3
create_topic "reference-data.credit-spreads" 3

# ── Market data ──────────────────────────────────────────────────────
create_topic "volatility.surfaces"   3
create_topic "correlation.matrices"  3

# ── Governance audit ─────────────────────────────────────────────────
create_topic "governance.audit"     3

# ── Dead-letter queues (same REPLICATION_FACTOR as regular topics) ────
create_topic "trades.lifecycle.dlq"  1
create_topic "price.updates.dlq"    1
create_topic "risk.results.dlq"     1
create_topic "risk.anomalies.dlq"   1
create_topic "governance.audit.dlq" 1

echo ""
echo "Topics created:"
$KAFKA_TOPICS --bootstrap-server "$BOOTSTRAP" --list
