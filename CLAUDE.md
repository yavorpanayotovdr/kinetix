# CLAUDE.md

## Testing Philosophy

Follow TDD (Test-Driven Development) and BDD (Behaviour-Driven Development) practices:

- **Write tests first.** Before implementing a feature or fixing a bug, write a failing test that describes the expected behaviour. Then write the minimal code to make it pass.
- **Red-Green-Refactor.** Start with a failing test (red), make it pass (green), then refactor while keeping tests green.
- **Design for testability.** Keep components loosely coupled, use dependency injection, and prefer pure functions where possible so that code is easy to test in isolation.
- **Test behaviour, not implementation.** Tests should describe *what* the system does, not *how* it does it. Avoid coupling tests to internal details that may change.
- **Name tests descriptively.** Test names should read as specifications — e.g. `"rejects a trade when the position limit is exceeded"` rather than `"testTradeLimit"`.
- **Cover the right levels.** Use unit tests for business logic, integration tests for infrastructure boundaries (DB, Kafka, gRPC), and acceptance tests for end-to-end flows.
- **Keep tests fast and independent.** Each test should be self-contained, set up its own state, and not depend on execution order.

## Design Principles

- **Single responsibility.** Each class or function should have one reason to change. If a class is doing parsing, validation, persistence, and notification, split it up. A service orchestrates; a repository persists; a mapper converts — don't blend these roles.
- **Prefer small, composable units.** Favour multiple focused classes over one large class with many methods. When a new responsibility appears, introduce a new collaborator rather than growing an existing one.
- **Depend on abstractions.** Use interfaces at module boundaries (repositories, clients, publishers) so implementations can be swapped and tested independently.

## Code Organisation

- **One type per file.** Data classes, enums, sealed classes, and interfaces should each live in their own file rather than being inlined in the implementation class that uses them. This keeps files focused and easy to navigate.
- **DTOs live in a `dtos` sub-package, one per file.** For example, route DTOs go in `routes/dtos/VaRResultResponse.kt`, not grouped in a single `RiskDtos.kt`. Each file contains exactly one `@Serializable` data class. The same applies to events and domain types — e.g. `PriceEvent` in `PriceEvent.kt`, not inside `KafkaPricePublisher.kt`.
- **Keep implementation files focused on behaviour.** A service or route file should contain the logic, not a mix of logic and type definitions.

## Project Conventions

- **Kotlin tests** use Kotest `FunSpec` with `shouldBe` / `shouldThrow` matchers and MockK for mocking.
- **Python tests** use pytest.
- **UI tests** use Vitest.
- **Integration tests** are named `*IntegrationTest` and run via `./gradlew integrationTest`.
- **Acceptance tests** are named `*AcceptanceTest` and run via `./gradlew acceptanceTest`.
- Regular `./gradlew test` excludes both integration and acceptance tests.
