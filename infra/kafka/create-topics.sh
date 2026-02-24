#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="localhost:9092"
KAFKA_TOPICS="/opt/kafka/bin/kafka-topics.sh"

topics=(
  "trades.lifecycle"
  "price.updates"
  "risk.results"
)

for topic in "${topics[@]}"; do
  echo "Creating topic: $topic"
  $KAFKA_TOPICS \
    --bootstrap-server "$BOOTSTRAP" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1
done

echo "Topics created:"
$KAFKA_TOPICS --bootstrap-server "$BOOTSTRAP" --list
