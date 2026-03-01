#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
KAFKA_TOPICS="${KAFKA_TOPICS_CMD:-/opt/kafka/bin/kafka-topics.sh}"
REPLICATION="${REPLICATION_FACTOR:-1}"

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
create_topic "risk.anomalies"    3

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

# ── Dead-letter queues ───────────────────────────────────────────────
create_topic "trades.lifecycle.dlq"  1
create_topic "price.updates.dlq"    1
create_topic "risk.results.dlq"     1
create_topic "risk.anomalies.dlq"   1

echo ""
echo "Topics created:"
$KAFKA_TOPICS --bootstrap-server "$BOOTSTRAP" --list
