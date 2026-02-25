# Kinetix Evolution Report

## 1. Project Timeline

**10 Feb 2026 — The Vision**
The project began with a single, ambitious prompt: *"this project will be for building a complete, modern, insightful and AI powered risk management system for big financial institutions, e.g. investment banks and large hedge funds."* The backend would be Kotlin, Python would handle ML/AI, and everything would follow strict TDD. An early session crash immediately exposed the risk of losing context — the user insisted all progress, architecture decisions, and TODOs be persisted on disc. This moment set the tone for the entire project: documentation and traceability became non-negotiable. The first commit initialised a Gradle monorepo with a version catalog, convention plugins, and the module structure. Proto definitions and shared domain primitives followed.

**11–12 Feb 2026 — Scaffolding the Services**
All 7 Ktor services were scaffolded with health endpoints (TDD), and the Python risk engine was set up with `uv` and `pytest`. The architecture was taking shape: Gateway, Position Service, Price Service, Risk Orchestrator, Rates Service, Audit Service, Regulatory Service, Notification Service, and the Python Risk Engine communicating over gRPC.

**20 Feb 2026 — The Big Build Day**
This was the most intense day — **27 commits** landed. The React UI was scaffolded with Vite, Tailwind, and Vitest. Docker Compose infrastructure (PostgreSQL, Kafka, Redis) was added. Then, increment by increment, the position domain model, persistence with Exposed ORM and Flyway, trade booking, Kafka event publishing, gateway REST endpoints, audit service, and acceptance tests were all built. Market data ingestion, WebSocket live price broadcasting, and a basic position grid UI with live P&L followed. By midnight, the VaR dashboard (gauge, trend chart, component breakdown) and a full VaR calculation acceptance test were in place.

**21 Feb 2026 — Observability, ML, Stress Testing, Regulatory, Notifications, Security**
The pace continued unrelentingly. Prometheus metrics endpoints were added to every service. The full observability stack landed: OpenTelemetry Collector, Prometheus, Grafana, Loki, Tempo, with provisioned dashboards (Risk Overview, System Health, Market Data) and alert rules (VaR breach, slow calc, stale data, Kafka lag). The ML pipeline was built: LSTM volatility predictor, credit default model (GBT), anomaly detector (Isolation Forest) — all with gRPC endpoints and acceptance tests. Stress testing followed (GFC 2008, COVID 2020, Taper Tantrum scenarios), then FRTB regulatory reporting (SBM, DRC, RRAO with CSV/XBRL output). Notifications with configurable alert rules, delivery channels (in-app, email, webhook), and risk result Kafka consumers were added. The day ended with JWT authentication via Keycloak, RBAC, circuit breakers, rate limiting, and Gatling load tests. A GitHub Actions CI pipeline was designed with a full test pyramid.

**22 Feb 2026 — Polish and Production Readiness**
Dev data seeders were built for a realistic local experience. The VaR calculator was wired for automatic calculation on startup. Tab navigation and portfolio switching were connected. AWS EKS deployment infrastructure (Dockerfiles, Helm, Terraform) was added. The user tested the live UI for the first time and began an intense series of UX refinements: formatting, portfolio summaries, alerts, dark fintech theme, and a component library with lucide-react icons. The System tab with service health and observability links was born.

**23 Feb 2026 — Logging & Operational Maturity**
Structured logging to Grafana Loki via OTel Collector was implemented. The Service Logs dashboard was created. A `dev-restart.sh` script was added. An OTLP logging pipeline integration test was written. The market data dependencies discovery feature was designed — understanding what data is needed before calculating risk.

**24 Feb 2026 — Architecture Refinements & New Services**
The price service was renamed from "market-data-service" to reflect its true scope. DTOs were refactored into individual files under `routes/dtos` sub-packages. `CLAUDE.md` was created with TDD/BDD guidelines, code organisation rules, and single responsibility principles. Four new market data services were built: Rates Service, Reference Data Service, Volatility Service, Correlation Service. The Calculation Runs (later Valuation Jobs) feature was added with pipeline visualization. An intense UX iteration cycle began: expandable sections, inline JSON, copy-to-clipboard, search and filtering.

**25 Feb 2026 — Valuation Jobs UX Perfection & Dashboard Links**
Tooltips for VaR and ES were refined. Time range filtering was added with server-side support. A zoomable timechart was introduced. Stress Testing was moved to a new Scenarios tab. Auto-polling replaced manual refresh. Click-to-zoom on bars, market data highlighting, per-position dependency grouping, pagination — feature after feature was polished. The Prices service Grafana link was inlined into the service health card. Custom Claude Code skills were created (TDD, architecture reference, evolution report).

---

## 2. Initial Vision vs Current State

**Original goal:** A "complete, modern, insightful and AI-powered risk management system for big financial institutions."

**How it evolved:**
- The initial plan had ~10 increments. All were completed in roughly 2 weeks.
- AI/ML was originally vaguely described as "leveraging AI for risk compute" — it became concrete with LSTM volatility prediction, gradient-boosted credit scoring, and isolation forest anomaly detection.
- The UI was initially an afterthought ("also the UI is quite important and is not for the future") — it became a major focus area consuming nearly half the development effort with detailed UX iterations.
- The scope expanded from core risk calculation to include: regulatory reporting (FRTB), stress testing with historical scenarios, notification alerting, authentication/RBAC, and a comprehensive observability stack.
- The "Valuation Jobs" feature (calculation pipeline visualization) was not in the original plan at all — it emerged organically from the desire to understand what the risk engine does and what data it needs.

**Key pivots:**
- "market-data-service" was renamed to "price-service" when it became clear it only handled prices, and separate services (rates, volatility, reference-data, correlation) were created for other market data types.
- The monolithic `MarketDataFetcher` was split into `DependenciesDiscoverer` + `MarketDataFetcher` following single-responsibility principles.
- Calculation Runs → Calculation Jobs → Valuation Jobs — naming evolved as the mental model crystallised.

---

## 3. Technical Evolution

### Stack Changes

| Technology | When Added | Purpose |
|---|---|---|
| Kotlin 2.1.20 / Ktor | Day 1 | Backend microservices |
| Python 3.12 / uv | Day 3 | ML models and gRPC risk engine |
| React / Vite / Tailwind | Day 10 | Frontend SPA |
| PostgreSQL 17 (TimescaleDB) | Day 10 | Per-service databases |
| Kafka (KRaft) | Day 10 | Event-driven communication |
| Redis | Day 10 | Price caching layer |
| gRPC / Protobuf | Day 1 | Risk Engine ↔ Orchestrator communication |
| Keycloak | Day 11 | Authentication & RBAC |
| Prometheus / Grafana | Day 11 | Metrics & dashboards |
| Loki / Tempo | Day 11 | Log aggregation & distributed tracing |
| OpenTelemetry Collector | Day 11 | Telemetry pipeline |
| Gatling | Day 11 | Load testing |
| Terraform / Helm | Day 12 | AWS EKS deployment |
| lucide-react | Day 12 | Icon library (replaced generic icons) |

**No technologies were removed or swapped** — the stack was chosen deliberately and remained stable.

### Architecture Shifts

1. **Market Data Service → Price Service + 4 specialized services** (24 Feb): The single market-data-service was found to only handle prices. Four new services (Rates, Reference Data, Volatility, Correlation) were created to properly model the full market data landscape.

2. **MarketDataFetcher decomposition** (24 Feb): The user noticed this class was doing both dependency discovery and data fetching — it was split into `DependenciesDiscoverer` and `MarketDataFetcher`.

3. **DTO extraction** (24 Feb): DTOs were initially inlined in route files. They were refactored into individual files under `routes/dtos` sub-packages, and this was codified as a project convention in `CLAUDE.md`.

4. **UI tab restructuring** (25 Feb): Stress Testing was moved from the Risk tab to a new Scenarios tab. Portfolio Greeks remained on Risk tab after careful consideration of where it belongs conceptually.

### Key Integrations

- **Kafka event mesh**: 6 topics connecting Position → Risk → Notification → Regulatory services
- **gRPC for compute**: Risk Orchestrator → Python Risk Engine (VaR, Greeks, stress tests, ML predictions)
- **WebSocket**: Gateway → UI for real-time price broadcasting
- **Keycloak OIDC**: JWT-based authentication with 5 role types
- **OTel → Prometheus/Loki/Tempo**: Full observability pipeline
- **GitHub Actions**: CI with test pyramid (unit → integration → acceptance per module)

---

## 4. Problems & Solutions Log

| Problem | Solution | Date |
|---|---|---|
| Session crash lost all progress | Persisted plan.md, ADRs, and docs on disc | 10 Feb |
| Ktor Gradle plugin incompatible with Gradle 9 | Replaced with application plugin | 20 Feb |
| Proto module missing kotlinx-coroutines-core | Added explicit dependency | 20 Feb |
| Dev services not responding on correct ports | Added `-port` args to Ktor services, set PYTHONPATH | 21 Feb |
| UI not rendering at localhost:5173 | Added Vite dev server proxy for gateway API/WebSocket | 21 Feb |
| "Failed to fetch portfolios" in UI | Wired gateway to downstream services for local dev | 21 Feb |
| No VaR data on startup | Wired ScheduledVaRCalculator to run on startup | 22 Feb |
| System tab not rendering during position loading | Fixed conditional rendering to not block on position state | 22 Feb |
| Grafana dashboard UIDs didn't match links | Fixed URLs to match provisioned dashboard UIDs | 22 Feb |
| Logs not appearing in Grafana Loki | Fixed OTel SDK auto-init and deferred appender install; fixed Loki OTLP ingestion and template variable | 23 Feb |
| UI startup race condition | Added gateway health check wait in dev-restart | 23 Feb |
| dev-restart.sh bash 3.2 compatibility (macOS) | Fixed array syntax for macOS bash | 23 Feb |
| Port conflicts with 4 new services | Assigned unique ports and updated all dev scripts | 24 Feb |
| Services showing DOWN in health UI | Fixed missing database creation and fallback URLs | 24 Feb |
| Content flash when selecting calculation run | Fixed state management to prevent re-render flash | 24 Feb |
| Custom time range not filtering server-side | Added integration tests, fixed query parameter handling | 25 Feb |
| Tooltip overflow off-screen edge | Clamped tooltip positioning within container bounds | 25 Feb |
| Slow dev stack startup | Replaced per-service Gradle runs with single `installDist` | 25 Feb |
| `act()` warnings in React tests | Wrapped state updates properly | 25 Feb |
| Stale time window in job polling | Fixed to use current time window on each poll | 25 Feb |

---

## 5. Abandoned Approaches

- **Single market-data-service**: Initially all market data (prices, rates, vol surfaces, correlations) was handled by one service. This didn't align with single responsibility and was replaced by 5 specialized services.
- **Monolithic MarketDataFetcher**: Discovery + fetching in one class was tried and refactored.
- **DTOs in same file as routes**: Initially convenient but violated code organisation principles; extracted to individual files.
- **Native HTML `title` tooltips**: Tried for VaR/ES explanations but too limited; replaced with CSS hover tooltips.
- **"Calculation Runs" naming**: Renamed twice before settling on "Valuation Jobs".
- **Auto-expanding FETCH_POSITIONS**: Tried, then reverted — too noisy by default.
- **Manual refresh for Valuation Jobs**: Replaced with auto-polling.
- **Click-to-expand pipeline arrows**: Replaced with full-row clickability.
- **Green highlighting for successful market data fetches**: Removed as "too much" — only failures are now highlighted.
- **Job IDs in zoomable timechart bars**: Tried, then removed for cleaner visualization.
- **GitHub wiki for documentation**: Attempted but links kept breaking; docs live in-repo instead.

---

## 6. Current State Summary

**What's working:**
- 9 microservices (Gateway, Position, Price, Risk Orchestrator, Notification, Rates, Reference Data, Volatility, Correlation) + Python Risk Engine
- Full VaR calculation pipeline: Historical, Parametric, Monte Carlo
- ML models: LSTM volatility, GBT credit scoring, Isolation Forest anomaly detection
- Stress testing with historical scenarios (GFC 2008, COVID 2020, etc.)
- FRTB regulatory reporting (SBM, DRC, RRAO) with CSV/XBRL output
- Configurable notification alerting (VaR breaches, P&L thresholds)
- JWT authentication via Keycloak with 5 role types
- Real-time price broadcasting via WebSocket
- Polished React UI with 4 tabs (Portfolio, Risk, Scenarios, System)
- Valuation Jobs with zoomable timechart, search, pagination, pipeline visualization
- Full observability: Prometheus metrics, Grafana dashboards, Loki logs, Tempo traces
- GitHub Actions CI with test pyramid and test summary
- Local dev stack (dev-up/dev-down/dev-restart) with data seeding
- AWS EKS deployment infrastructure (Helm, Terraform, Dockerfiles)
- 200 commits, ~555 conversation prompts across 92 sessions

**What's in progress:**
- Adding Grafana dashboard links for remaining services (beyond Prices) in the Service Health UI
- The Prices service has an inline Grafana link; the remaining 8 services need similar dashboards

**Known issues / tech debt:**
- Some Helm chart `.tgz` files are untracked in git
- The risk-engine `uv.lock` has unstaged changes
- GitHub wiki links were attempted but remain broken — documentation lives in-repo
- The VaR gauge component has uncommitted test changes

---

## 7. Session References

| Phase | Session ID | Date |
|---|---|---|
| Initial vision & architecture | `761fa3b1-d022-40d2-8114-db773fade956` | 10 Feb |
| Session crash & documentation lesson | `1dc557ac-55ef-49f1-8544-9b6b0edfc83d` | 10 Feb |
| Plan creation & first commits | `5d799de3-bc3b-4b53-b519-2ec311349aef` | 10 Feb |
| The big build day (increments 0.6–2.6) | `a1f204ba-1eb2-43dc-ad9b-2ea29160d344` | 20 Feb |
| Risk engine & ML models | `fb15f741-80b1-4332-bf6d-d0745fa45591` | 21 Feb |
| Stress testing | `6dec875c-bec0-48c1-8ef7-b465adc140d1` | 21 Feb |
| FRTB regulatory reporting | `2ef509d6-8f27-4b01-8f10-66af1be97238` | 21 Feb |
| Notifications & alerting | `fe62d76c-fe5b-4069-ae8f-997e2d5b8829` | 21 Feb |
| Security & production hardening | `f184b3b9-aae1-4775-889a-c6335bcf18c8` | 21 Feb |
| CI pipeline & dev tooling | `f6038258-4020-4b53-bedb-2d0d9ce2342c` | 21 Feb |
| UI wiring & first live test | `8593529a-3209-4614-a37c-3e5675c6e595` | 22 Feb |
| AWS deployment infrastructure | `31679e6d-6676-4af5-a771-44d129c70e32` | 22 Feb |
| Dark theme & UI modernization | `af4c7ad8-17fd-466a-946e-1b3c4c09e654` | 22 Feb |
| Loki logging pipeline | `6c1768a3-e292-4c41-a68b-3528fe4fbe48` | 23 Feb |
| CLAUDE.md & code conventions | `190f2753-2305-4b66-8942-431c4bba3c46` | 24 Feb |
| Market data services expansion | `ce27ebb4-3b1f-4e81-b71c-c2d483283652` | 24 Feb |
| Valuation Jobs UX iterations | `9eeb77be-97bb-42a0-ac8b-dfb3cd2f4953` | 24–25 Feb |
| Grafana dashboard links | `0ac35409-0a31-4d6e-bd07-2eadf76d6e4e` | 25 Feb |

---

*Report generated from 555 conversation entries across 92 sessions, cross-referenced with 200 git commits spanning 10–25 February 2026.*
