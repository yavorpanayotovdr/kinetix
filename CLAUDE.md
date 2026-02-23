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

## Project Conventions

- **Kotlin tests** use Kotest `FunSpec` with `shouldBe` / `shouldThrow` matchers and MockK for mocking.
- **Python tests** use pytest.
- **UI tests** use Vitest.
- **Integration tests** are named `*IntegrationTest` and run via `./gradlew integrationTest`.
- **Acceptance tests** are named `*AcceptanceTest` and run via `./gradlew acceptanceTest`.
- Regular `./gradlew test` excludes both integration and acceptance tests.
