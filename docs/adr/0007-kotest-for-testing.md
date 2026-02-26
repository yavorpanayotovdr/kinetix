# ADR-0007: Use Kotest Over JUnit 5 for Kotlin Testing

## Status
Accepted

## Context
TDD is a first-class requirement. We need a testing framework for Kotlin that supports unit, integration, acceptance, and property-based testing with minimal boilerplate. Options: JUnit 5, Kotest, Spek.

## Decision
Use Kotest 5.9 as the primary testing framework for all Kotlin modules. Use MockK for mocking. Use Testcontainers for integration tests.

## Consequences

### Positive
- Kotlin-native: DSL-based test definitions, no annotations
- Multiple spec styles: `FunSpec` for unit tests, `BehaviorSpec` (Given/When/Then) for acceptance tests — avoids needing Cucumber
- Built-in property-based testing (`kotest-property`) for numerical edge cases (critical in financial calculations)
- First-class coroutine support — tests can use `suspend` functions naturally
- Rich assertion library (`shouldBe`, `shouldContain`, `eventually` for async assertions)
- Testcontainers extension (`kotest-extensions-testcontainers`) for lifecycle management

### Negative
- Less familiar to developers coming from Java/JUnit
- IDE support is good but not as mature as JUnit's (Kotest IntelliJ plugin required)
- Some third-party libraries assume JUnit — occasional adapter friction

### Test Organization
- `*Test` suffix for unit tests → run by `./gradlew test`
- `*IntegrationTest` suffix → run by `./gradlew integrationTest`
- `*End2EndTest` suffix → run by `./gradlew end2EndTest`
- Separation enforced by Gradle task filters in `kinetix.kotlin-testing.gradle.kts`

### Alternatives Considered
- **JUnit 5**: Industry standard, excellent IDE support, but verbose in Kotlin. No built-in BDD specs — would need Cucumber (extra `.feature` file layer). No built-in property testing — would need jqwik.
- **Spek**: Kotlin-native spec framework but less actively maintained than Kotest and lacks property testing.
