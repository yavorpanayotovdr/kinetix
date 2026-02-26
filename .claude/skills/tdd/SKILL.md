---
name: tdd
description: Enforces the red-green-refactor TDD cycle for implementing features and fixing bugs.
argument-hint: [feature or bug description]
disable-model-invocation: true
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, Task
---

# TDD: Red-Green-Refactor

You are implementing the following feature or fix using strict Test-Driven Development:

> $ARGUMENTS

Follow these steps **in order**. Do not skip ahead.

## Step 1 -- RED: Write a failing test

- Decide the right test level:
  - **Unit test** for pure business logic (default choice).
  - **Integration test** (`*IntegrationTest`) for infrastructure boundaries -- DB, Kafka, gRPC. Use Testcontainers for external dependencies.
  - **End-to-end test** (`*End2EndTest`) for end-to-end flows across services.

- Place the test in the matching `src/test/kotlin` (unit) or `src/integrationTest/kotlin` (integration) source set.

- Use **Kotest FunSpec** with `shouldBe` / `shouldThrow` matchers:
  ```kotlin
  class SomeFeatureTest : FunSpec({
      test("rejects a trade when the position limit is exceeded") {
          // arrange
          // act
          // assert with shouldBe / shouldThrow
      }
  })
  ```

- **Python** tests (risk-engine): use pytest with plain `assert`.

- Name the test as a **behavioural specification** -- describe *what* the system does, not how:
  - Good: `"calculates VaR using Monte Carlo with 10 000 simulations"`
  - Bad: `"testVaR"` or `"test Monte Carlo"`

- Use **MockK** (`mockk`, `every`, `verify`) to stub collaborators. Never mock the class under test.

## Step 2 -- Confirm RED

Run the test and verify it **fails for the right reason** (assertion failure, not a compile error).

```bash
# Unit tests
./gradlew :<module>:test --tests "<fully.qualified.TestClass>"

# Integration tests
./gradlew :<module>:integrationTest --tests "<fully.qualified.TestClass>"

# End-to-end tests
./gradlew :acceptance-tests:end2EndTest --tests "<fully.qualified.TestClass>"

# Python (risk-engine)
cd risk-engine && uv run pytest tests/<test_file>::<test_name> -v
```

If the test fails for the wrong reason (e.g. missing class), create just enough skeleton code (empty class / interface) to get the correct assertion failure, then re-run.

## Step 3 -- GREEN: Write the minimal implementation

- Write **only** the code needed to make the failing test pass. No extras.
- Follow project conventions: one type per file, DTOs in a `dtos` sub-package, services focused on behaviour.
- Depend on abstractions (interfaces) at module boundaries.

## Step 4 -- Confirm GREEN

Run the same test command from Step 2. The test **must pass**. If it does not, fix the implementation (not the test) and re-run.

Also run the full module test suite to check for regressions:

```bash
./gradlew :<module>:test
```

## Step 5 -- REFACTOR (if needed)

- Improve readability, remove duplication, extract helpers -- but **do not change behaviour**.
- Re-run the tests after every refactoring change to keep them green.
- If no refactoring is needed, say so and move on.

## Reminders

- **Never skip the red step.** A test that has never been seen failing may not be testing anything.
- **Keep tests fast and independent.** Each test sets up its own state.
- **Test behaviour, not implementation.** If a refactor breaks a test, the test was too coupled to internals.
- After finishing, briefly summarise what was tested and implemented.
