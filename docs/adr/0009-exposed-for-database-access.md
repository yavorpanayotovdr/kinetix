# ADR-0009: Use Exposed for Database Access

## Status
Accepted

## Context
Kotlin services need a database access layer for PostgreSQL and TimescaleDB. Options: Exposed (JetBrains), jOOQ, Hibernate/JPA, plain JDBC with coroutines.

## Decision
Use Exposed 0.57 (JetBrains Kotlin SQL framework) with its DSL API.

## Consequences

### Positive
- Kotlin-native: maintained by JetBrains, designed for Kotlin idioms
- Type-safe SQL DSL — compile-time query validation without annotation processing
- Lightweight: no proxy objects, no lazy loading traps, no session management complexity
- Aligns with the Kotlin-native stack (Ktor, Koin, kotlinx.serialization)
- Supports both DSL (for complex queries) and DAO (for simple CRUD) APIs

### Negative
- Smaller community than Hibernate or jOOQ
- Less powerful for very complex SQL compared to jOOQ (which generates from schema)
- No built-in migration tooling (mitigated by using Flyway separately)

### Alternatives Considered
- **Hibernate/JPA**: Industry standard ORM, but heavyweight. Proxy objects, lazy loading exceptions, and session management are error-prone. Annotation-driven model is less idiomatic in Kotlin.
- **jOOQ**: Excellent for complex SQL, generates type-safe code from database schema. But it's Java-first, and the code generation step adds build complexity. Better suited for legacy database integration.
- **Plain JDBC**: Maximum control but excessive boilerplate for common operations.
