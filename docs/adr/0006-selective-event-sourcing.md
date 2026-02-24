# ADR-0006: Selective Event Sourcing for Trade Lifecycle

## Status
Accepted

## Context
Financial systems require auditability and the ability to reconstruct historical state. Event sourcing (storing every state change as an immutable event) is a natural fit for some domains but adds complexity. We need to decide where to apply it.

## Decision
Apply event sourcing selectively:
- **position-service** (trade lifecycle): Every trade mutation stored as an immutable event. Current position is a projection.
- **audit-service**: Append-only event store consuming from all Kafka topics.
- **All other services**: Standard CRUD with PostgreSQL.

## Consequences

### Positive
- Full audit trail for trades — regulatory requirement for Basel III/IV
- Point-in-time position reconstruction ("what was our exposure at 3:45 PM on March 15?")
- Event replay enables reprocessing and debugging
- Simpler services where event sourcing doesn't add value (price, regulatory, notification)

### Negative
- Two persistence patterns in the codebase (event-sourced vs CRUD) — developers need to understand both
- Event-sourced projections add complexity (building current state from events, handling projection failures)
- Schema evolution for events requires careful versioning

### Alternatives Considered
- **Full event sourcing everywhere**: Maximum auditability but disproportionate complexity for services like notification or price-service where CRUD is sufficient.
- **No event sourcing (CRUD everywhere + audit table)**: Simpler but loses the ability to reconstruct point-in-time state from events. Audit becomes a secondary concern bolted on rather than a core data model.
