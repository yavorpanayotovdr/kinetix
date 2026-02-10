# ADR-0005: Use TimescaleDB for Time-Series Data

## Status
Accepted

## Context
Risk calculation results and market data history are time-series data requiring efficient time-range queries, aggregation, and retention policies. Options: TimescaleDB (PostgreSQL extension), QuestDB, InfluxDB, plain PostgreSQL with partitioning.

## Decision
Use TimescaleDB 2.17 as a PostgreSQL extension for time-series storage.

## Consequences

### Positive
- Single database technology to operate — TimescaleDB is a PostgreSQL extension, not a separate database. Same driver (`postgresql`), same SQL, same tooling (pgAdmin, Flyway, Exposed)
- Automatic time-based partitioning via hypertables
- Continuous aggregates for pre-computed rollups (hourly VaR averages, daily P&L)
- Compression for historical data (10-20x compression ratios)
- Full SQL support including JOINs with relational tables in the same database

### Negative
- Slightly lower raw ingestion throughput than purpose-built TSDBs like QuestDB
- Extension must be enabled in PostgreSQL — requires a custom Docker image or the official TimescaleDB image

### Stored Time-Series Data
- `market_data` — Price ticks, bid/ask, volume per instrument
- `risk_results` — VaR values, expected shortfall, component breakdown per portfolio/calculation type

### Alternatives Considered
- **QuestDB**: Faster for pure analytical queries and high-frequency ingestion, but introduces a separate database technology with its own query language, driver, and operational model. Not worth the complexity for our throughput needs.
- **InfluxDB**: Purpose-built TSDB but uses Flux query language (not SQL). Weaker JOIN capabilities.
- **Plain PostgreSQL partitioning**: Manual partition management is tedious. TimescaleDB automates this.
