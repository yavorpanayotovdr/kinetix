# ADR-0010: Use React + Vite for Frontend

## Status
Accepted

## Context
The system needs a modern UI for risk dashboards, position management, and regulatory reporting. This is a single-page application (SPA) for internal users — not a public content site. Options: React + Vite, Next.js, Angular, Vue.

## Decision
Use React 19 with Vite 6, TypeScript, Tailwind CSS, and Radix UI primitives.

## Consequences

### Positive
- React 19 has the largest ecosystem for component libraries, charting, and data tables
- Vite provides fast dev server (HMR in milliseconds) and modern bundling
- SPA architecture is the right fit — no SSR complexity needed for an internal dashboard
- TradingView Lightweight Charts integrates natively for financial time-series
- TanStack Table handles virtualized grids for large position datasets
- Tailwind CSS avoids CSS-in-JS runtime overhead

### Negative
- No SSR — not suitable if requirements change to public-facing (unlikely for risk management)
- React ecosystem churn — library choices may need updating over time

### Key Libraries
- **Zustand** for state management (lightweight, no boilerplate)
- **TanStack Query** for server state (caching, refetching, optimistic updates)
- **TradingView Lightweight Charts** for financial charts
- **TanStack Table** for virtualized position/risk grids
- **Radix UI** for accessible component primitives
- **Playwright** for E2E acceptance tests

### Alternatives Considered
- **Next.js**: Adds SSR/SSG complexity that an internal SPA doesn't need. App Router introduces server components — unnecessary abstraction for a dashboard.
- **Angular**: Full framework with opinionated structure. Heavier, steeper learning curve, smaller community for greenfield projects in 2026.
- **Vue**: Viable alternative but smaller ecosystem for financial charting and data-heavy dashboards.
