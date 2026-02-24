# Risk Orchestrator — VaR Workflow, Triggers, Kafka Integration

Stateless orchestration layer that coordinates VaR calculations across the Kinetix platform. Fetches positions, calls the Python risk-engine via gRPC, publishes results to Kafka, and is triggered by trade events, market data events, and scheduled intervals. Does not own a database.

## Modules

| Module | Purpose |
|--------|---------|
| `model/VaRResult.kt` | Domain types: `CalculationType`, `ConfidenceLevel`, `ComponentBreakdown`, `VaRResult`, `VaRCalculationRequest` |
| `client/PositionProvider.kt` | Interface for fetching positions by portfolio ID |
| `client/PositionServicePositionProvider.kt` | Implementation delegating to `PositionRepository` from `:position-service` |
| `client/RiskEngineClient.kt` | Interface for invoking the Python risk engine |
| `client/GrpcRiskEngineClient.kt` | Implementation wrapping `RiskCalculationServiceCoroutineStub` with proto mapping |
| `mapper/PositionMapper.kt` | Domain `Position` → proto `Position` for VaR requests |
| `mapper/VaRResultMapper.kt` | Proto `VaRResponse` → domain `VaRResult`, plus enum converters |
| `service/VaRCalculationService.kt` | Core orchestrator: fetches positions → calls gRPC → publishes results to Kafka |
| `kafka/TradeEventConsumer.kt` | Consumes `trades.lifecycle`, triggers VaR recalculation per portfolio |
| `kafka/MarketDataEventConsumer.kt` | Consumes `price.updates`, triggers VaR recalculation for affected portfolios |
| `kafka/KafkaRiskResultPublisher.kt` | Publishes `VaRResult` to `risk.results` topic (portfolio ID as partition key) |
| `kafka/RiskResultEvent.kt` | `@Serializable` DTO for Kafka transport |
| `schedule/ScheduledVaRCalculator.kt` | Periodic VaR recalculation loop with configurable interval |

## VaR calculation flow

1. **Trigger** arrives (trade event, market data event, scheduled tick, or on-demand request)
2. `VaRCalculationService.calculateVaR(request)` is called
3. Positions are fetched via `PositionProvider.getPositions(portfolioId)`
4. Positions are mapped to proto and sent to the Python risk-engine via `RiskEngineClient.calculateVaR()`
5. The proto `VaRResponse` is mapped back to a domain `VaRResult`
6. The result is published to `risk.results` Kafka topic via `RiskResultPublisher`

## Key design decisions

### Position access via interface, not direct DB dependency
The orchestrator defines a `PositionProvider` interface with an in-process implementation that depends on `:position-service`. This follows the same pattern used in gateway (`PositionServiceClient` interface) and keeps coupling loose.

### gRPC client via Kotlin coroutine stub
The `:proto` module generates `RiskCalculationServiceCoroutineStub`. The orchestrator wraps this behind a `RiskEngineClient` interface for testability. The concrete `GrpcRiskEngineClient` uses the coroutine stub for async invocation.

### Kafka consumers as triggers
Two Kafka consumers trigger VaR recalculation:
- `TradeEventConsumer` (topic: `trades.lifecycle`) — recalculates when positions change
- `MarketDataEventConsumer` (topic: `price.updates`) — recalculates when prices move

Both follow the established pattern: `coroutineContext.isActive` loop, `Dispatchers.IO` for blocking Kafka calls, JSON deserialization, error logging.

### Kafka publisher for results
`KafkaRiskResultPublisher` publishes `VaRResult` to `risk.results` topic with portfolio ID as partition key. Same pattern as `KafkaTradeEventPublisher` in position-service.

### Scheduled recalculation
`ScheduledVaRCalculator` uses `kotlinx.coroutines.delay` in a loop to trigger periodic VaR recalculation. Configurable interval (default 60s).

## Running

```bash
# Unit tests (20 tests)
./gradlew :risk-orchestrator:test

# Integration tests (6 tests, requires Docker)
./gradlew :risk-orchestrator:integrationTest

# Full build
./gradlew build
```
