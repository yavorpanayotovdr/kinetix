# ADR-0001: Use Monorepo Structure

## Status
Accepted

## Context
Kinetix is a multi-module system with Kotlin services, a Python risk engine, a React frontend, shared Protocol Buffer definitions, and infrastructure configuration. We need to decide between a monorepo (single repository) and polyrepo (one repository per service).

## Decision
Use a single monorepo for all components.

## Consequences

### Positive
- Atomic commits across service boundaries (e.g., proto changes + Kotlin + Python in one commit)
- Shared build logic via Gradle convention plugins — no copy-paste across repos
- Single CI/CD pipeline to maintain
- Easier developer onboarding — clone once, build everything
- Shared `.proto` files generate stubs for both Kotlin and Python from the same source

### Negative
- Repository size will grow over time
- Need disciplined module boundaries to avoid coupling
- Build times may increase (mitigated by Gradle build cache and task avoidance)

### Neutral
- Gradle multi-module project handles Kotlin services naturally
- Python and UI modules live alongside but have independent build tools (uv, npm)
