# ADR-0004: Use Apache Kafka for Asynchronous Messaging

## Status
Accepted

## Context
Services need asynchronous communication for event-driven workflows: market data distribution, trade lifecycle events, risk calculation results, and audit trail ingestion. Options: Apache Kafka, RabbitMQ, Redis Streams, NATS.

## Decision
Use Apache Kafka 4.0 in KRaft mode (no ZooKeeper dependency).

## Consequences

### Positive
- Durable, replayable event streams — critical for audit trail reconstruction and reprocessing failed calculations
- High throughput for market data tick distribution (millions of messages/second)
- Built-in partitioning for parallel consumption
- Consumer groups allow independent scaling of consumers per topic
- KRaft mode eliminates ZooKeeper operational overhead

### Negative
- Higher operational complexity than RabbitMQ
- Higher latency for individual messages compared to RabbitMQ (milliseconds vs microseconds) — acceptable for our use cases
- Heavier resource footprint in local dev

### Kafka Topics
- `price.updates` — Price updates (consumers: risk-orchestrator, position-service)
- `trades.lifecycle` — Trade events (consumers: risk-orchestrator, audit-service)
- `risk.results` — Completed risk calculations (consumers: regulatory-service, notification-service, gateway)

### Alternatives Considered
- **RabbitMQ**: Lower latency per message, simpler operations, but lacks replay capability. Audit trail and reprocessing require durable streams.
- **Redis Streams**: Lightweight but limited in durability guarantees and consumer group management at scale.
- **NATS**: Fast but lacks the durability and ecosystem maturity of Kafka for financial workloads.
