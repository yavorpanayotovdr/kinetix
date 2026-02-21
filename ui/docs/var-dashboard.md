# VaR Dashboard (Increment 3.4)

## Context

The risk engine (3.1) calculates VaR, the orchestrator (3.2) coordinates it, and the gateway (3.3) exposes `GET/POST /api/v1/risk/var/{portfolioId}`. This module adds a VaR dashboard to the React UI above the existing PositionGrid.

## Key Design Decisions

1. **No new dependencies** — pure SVG + Tailwind CSS for all visualizations (gauge, trend line, breakdown bar), matching the existing zero-dependency approach.
2. **Time series via client-side accumulation** — there is no history API, so `useVaR` polls `GET /risk/var/{portfolioId}` every 30s and accumulates results in-memory (capped at 60 entries). The trend chart plots these accumulated values.
3. **Independent loading/error** — VaR dashboard manages its own loading/error state via `useVaR` hook, so a VaR failure never blocks the position grid.

## Components

- **VaRGauge** — SVG semicircular gauge showing VaR value with color coding + ES display
- **VaRDashboard** — Composite: gauge + component breakdown bar + trend chart + metadata + Recalculate button
- **useVaR** — Hook: fetches on mount, polls every 30s, deduplicates history by `calculatedAt`

## API Functions

- `fetchVaR(portfolioId)` — GET, returns null on 404
- `triggerVaRCalculation(portfolioId, request?)` — POST
