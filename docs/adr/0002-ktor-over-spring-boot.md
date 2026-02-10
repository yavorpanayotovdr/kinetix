# ADR-0002: Use Ktor Over Spring Boot for Kotlin Services

## Status
Accepted

## Context
We need a web/service framework for the Kotlin backend services. The main contenders are Spring Boot 3.x (most popular JVM framework), Ktor 3.4 (JetBrains Kotlin-native framework), and Micronaut (compile-time DI, GraalVM-friendly).

## Decision
Use Ktor 3.4 for all Kotlin services.

## Consequences

### Positive
- Kotlin-native, coroutine-first — structured concurrency is a first-class citizen, not bolted on
- Lightweight: faster startup, lower memory footprint than Spring Boot
- No annotation magic — explicit DSL-based configuration is easier to reason about and debug
- Aligns with the rest of the Kotlin-native stack (Exposed, Koin, kotlinx.serialization)
- Plugin architecture allows pulling in only what each service needs

### Negative
- Smaller community and ecosystem compared to Spring Boot
- Fewer ready-made integrations (e.g., Spring Data, Spring Security) — we build or configure more ourselves
- Hiring: more developers are familiar with Spring Boot

### Alternatives Considered
- **Spring Boot 3.x**: Mature, massive ecosystem, but heavyweight for this use case. Annotation-driven model is less idiomatic in Kotlin. Coroutine support exists but is secondary to the reactive (WebFlux) model.
- **Micronaut 4.x**: Compile-time DI is appealing, but the framework is less Kotlin-idiomatic than Ktor and its ecosystem is smaller.
