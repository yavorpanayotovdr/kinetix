# CLAUDE.md

Kinetix is a multi-service financial risk management platform: Kotlin/Ktor microservices, a Python risk engine, and a React/TypeScript UI.

## Project Structure

```
kinetix/
  common/                  # Shared Kotlin library (DTOs, Kafka, HTTP utils)
  proto/                   # Protobuf definitions (gRPC contracts)
  gateway/                 # API gateway — aggregates backend services for the UI
  position-service/        # Trade booking, position management, P&L, limits
  price-service/           # Market data ingestion and price history
  rates-service/           # Interest rate curves
  volatility-service/      # Volatility surfaces
  correlation-service/     # Correlation matrices
  reference-data-service/  # Instrument and counterparty reference data
  risk-engine/             # Python — VaR, Greeks, Monte Carlo, stress testing (gRPC)
  risk-orchestrator/       # Orchestrates risk calculations across services
  regulatory-service/      # Model governance, backtesting, submissions
  notification-service/    # WebSocket push to the UI
  audit-service/           # Hash-chained audit trail
  ui/                      # React + TypeScript + Vite frontend
  end2end-tests/           # Kotlin E2E tests against running services
  schema-tests/            # Kafka event schema compatibility tests
  deploy/                  # Helm charts, infrastructure config
```

## Build & Run

```bash
# Kotlin services
./gradlew build                              # Build all
./gradlew test                               # Unit tests only
./gradlew acceptanceTest                     # Acceptance tests (*AcceptanceTest)
./gradlew integrationTest                    # Integration tests (*IntegrationTest)
./gradlew :end2end-tests:end2EndTest         # End-to-end tests (*End2EndTest)

# Risk engine (Python)
cd risk-engine && uv run pytest              # All tests
cd risk-engine && uv run pytest -m unit      # Unit only
cd risk-engine && uv run pytest -m integration  # Integration only

# UI
cd ui && npm run dev                         # Dev server
cd ui && npm run test                        # Vitest unit tests
cd ui && npx playwright test                 # Playwright browser tests
cd ui && npx playwright test --ui            # Playwright UI mode
```

## Testing Philosophy

Follow TDD (Test-Driven Development) and BDD (Behaviour-Driven Development) practices:

- **Write tests first.** Before implementing a feature or fixing a bug, write a failing test that describes the expected behaviour. Then write the minimal code to make it pass.
- **Red-Green-Refactor.** Start with a failing test (red), make it pass (green), then refactor while keeping tests green.
- **Design for testability.** Keep components loosely coupled, use dependency injection, and prefer pure functions where possible so that code is easy to test in isolation.
- **Test behaviour, not implementation.** Tests should describe *what* the system does, not *how* it does it. Avoid coupling tests to internal details that may change.
- **Name tests descriptively.** Test names should read as specifications — e.g. `"rejects a trade when the position limit is exceeded"` rather than `"testTradeLimit"`.
- **Cover the right levels.** Use unit tests for business logic, integration tests for infrastructure boundaries (DB, Kafka, gRPC), and acceptance tests for end-to-end flows.
- **Every meaningful UI feature needs browser-level E2E coverage.** Do not consider a new tab, panel, dialog, or significant interactive workflow complete without Playwright tests.
- **Keep tests fast and independent.** Each test should be self-contained, set up its own state, and not depend on execution order.
- **Run tests after every change.** After making changes, run the full test suite for every affected module — not just new tests. Do not consider work complete until all tests pass.
- **Every change needs test coverage.** New functionality must have tests that verify it works. Bug fixes must have a test that reproduces the bug before the fix. Refactors must not reduce existing test coverage. If changing behaviour that existing tests cover, update those tests to match — do not leave them failing.

## Project Conventions

- **Kotlin tests** use Kotest `FunSpec` with `shouldBe` / `shouldThrow` matchers and MockK for mocking.
- **Python tests** use pytest with `@pytest.mark.unit` / `@pytest.mark.integration` / `@pytest.mark.performance` markers.
- **UI unit tests** use Vitest.
- **UI browser tests** use Playwright and live in `ui/e2e/`. Mock API routes using the patterns in `ui/e2e/fixtures.ts` and test user-visible behaviour: empty states, data rendering, user interactions, validation, and error paths.
- **Acceptance tests** are named `*AcceptanceTest` and run via `./gradlew acceptanceTest`. These are contract and behaviour tests that live in each service module.
- **Integration tests** are named `*IntegrationTest` and run via `./gradlew integrationTest`.
- **End-to-end tests** are named `*End2EndTest` and run via `./gradlew :end2end-tests:end2EndTest`.
- Regular `./gradlew test` excludes acceptance, integration, and end-to-end tests.

## Design Principles

- **Readability first.** Code is read far more often than it is written. Use clear, descriptive names for variables, functions, and classes. Keep functions short and focused. Prefer explicit, straightforward control flow over clever or terse constructs. Code should be understandable without needing comments to explain what it does.
- **Single responsibility.** Each class or function should have one reason to change. If a class is doing parsing, validation, persistence, and notification, split it up. A service orchestrates; a repository persists; a mapper converts — don't blend these roles.
- **Prefer small, composable units.** Favour multiple focused classes over one large class with many methods. When a new responsibility appears, introduce a new collaborator rather than growing an existing one.
- **Depend on abstractions.** Use interfaces at module boundaries (repositories, clients, publishers) so implementations can be swapped and tested independently.
- **Simplicity over complexity.** Choose the simplest solution that meets the current requirement. Avoid premature abstractions, speculative generality, and over-engineered designs. If a straightforward approach works, prefer it over a clever one. Add complexity only when a concrete need demands it — not because it might be useful someday.

## Code Organisation

### Kotlin services

- **One type per file.** Data classes, enums, sealed classes, and interfaces should each live in their own file rather than being inlined in the implementation class that uses them. This keeps files focused and easy to navigate.
- **DTOs live in a `dtos` sub-package, one per file.** For example, route DTOs go in `routes/dtos/VaRResultResponse.kt`, not grouped in a single `RiskDtos.kt`. Each file contains exactly one `@Serializable` data class. The same applies to events and domain types — e.g. `PriceEvent` in `PriceEvent.kt`, not inside `KafkaPricePublisher.kt`.
- **Keep implementation files focused on behaviour.** A service or route file should contain the logic, not a mix of logic and type definitions.

### Python risk engine

- Source lives in `risk-engine/src/kinetix_risk/`. Tests live in `risk-engine/tests/`.
- Modules are flat files (e.g. `greeks.py`, `monte_carlo.py`, `valuation.py`) — no deep package nesting.
- Use dataclasses or Pydantic models for structured data. Keep gRPC server wiring (`server.py`) separate from calculation logic.

## Architectural Decisions

- **Ask before changing architecture.** Before introducing a new service, module, library, messaging topic, database table, or API contract — or before significantly restructuring existing ones — explain the trade-offs and get my approval.
- **Act autonomously within existing boundaries.** Adding a class/file within an existing service, writing tests, refactoring internals, or adding a route to an existing API — follow established patterns without asking.

## Guardrails

- **Never delete, disable, or skip a test** (test file, test function, or test assertion) without my explicit permission. This includes marking tests as ignored, disabled, skipped, or xfail (e.g. `@Ignore`, `@Disabled`, `xconfig`, `pytest.mark.skip`, `test.skip`, `.todo`). If a test is failing, fix the code under test or fix the test — do not delete, skip, or suppress it to make the build pass. Always explain the failure and ask before removing or disabling any test.
- **Never force-push or rewrite published git history** without my explicit permission.
- **Never modify CI/CD pipeline files** without my approval.
- **Never add a new library/dependency** without my approval.
- **Never skip pre-commit hooks** (no `--no-verify`).
- **When stuck, explain the problem** and your proposed fix before silently retrying or working around it.

## Known Gotchas

- **Testcontainers in `common` module** — Docker connectivity fails because `common` is a library module missing Docker client deps on its classpath. Place integration tests in service modules instead.
- **Exposed + Kotest `shouldThrow`** — Exceptions thrown inside `newSuspendedTransaction` cannot be caught by `shouldThrow`. Workaround: move validation before the `transactional.run{}` block.
- **Risk engine PYTHONPATH** — The Dockerfile needs `PYTHONPATH=/app/src` because `uv sync` doesn't install the project in editable mode.

## Commit Practices

- **Commit frequently during implementation.** After completing each logical, working unit of change, create a commit. Do not wait until the entire task is finished to commit.
- **Each commit should be self-contained.** The codebase must build and tests must pass after every commit. Never commit half-finished work that breaks the build.
- **Don't batch unrelated changes.** Keep commits focused — one concern per commit. If a plan involves multiple steps, each step should typically be its own commit.

## Communication Style

- **Always end with a summary.** When a task is done, finish with a single short sentence summarising what was accomplished.
