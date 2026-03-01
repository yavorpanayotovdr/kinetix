import type { Page, Route } from '@playwright/test'

export interface PositionFixture {
  portfolioId: string
  instrumentId: string
  assetClass: string
  quantity: string
  averageCost: { amount: string; currency: string }
  marketPrice: { amount: string; currency: string }
  marketValue: { amount: string; currency: string }
  unrealizedPnl: { amount: string; currency: string }
}

export const TEST_PORTFOLIOS = [{ portfolioId: 'port-1' }]

export const TEST_POSITIONS: PositionFixture[] = [
  {
    portfolioId: 'port-1',
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    quantity: '100',
    averageCost: { amount: '150.00', currency: 'USD' },
    marketPrice: { amount: '155.00', currency: 'USD' },
    marketValue: { amount: '15500.00', currency: 'USD' },
    unrealizedPnl: { amount: '500.00', currency: 'USD' },
  },
  {
    portfolioId: 'port-1',
    instrumentId: 'GOOGL',
    assetClass: 'EQUITY',
    quantity: '50',
    averageCost: { amount: '2800.00', currency: 'USD' },
    marketPrice: { amount: '2850.00', currency: 'USD' },
    marketValue: { amount: '142500.00', currency: 'USD' },
    unrealizedPnl: { amount: '2500.00', currency: 'USD' },
  },
  {
    portfolioId: 'port-1',
    instrumentId: 'EUR_USD',
    assetClass: 'FX',
    quantity: '10000',
    averageCost: { amount: '1.0800', currency: 'USD' },
    marketPrice: { amount: '1.0850', currency: 'USD' },
    marketValue: { amount: '10850.00', currency: 'USD' },
    unrealizedPnl: { amount: '50.00', currency: 'USD' },
  },
]

export const TEST_TRADES = [
  {
    tradeId: 'trade-1',
    portfolioId: 'port-1',
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    side: 'BUY',
    quantity: '100',
    price: { amount: '150.00', currency: 'USD' },
    tradedAt: '2025-01-15T10:30:00Z',
  },
  {
    tradeId: 'trade-2',
    portfolioId: 'port-1',
    instrumentId: 'GOOGL',
    assetClass: 'EQUITY',
    side: 'BUY',
    quantity: '50',
    price: { amount: '2800.00', currency: 'USD' },
    tradedAt: '2025-01-15T11:00:00Z',
  },
  {
    tradeId: 'trade-3',
    portfolioId: 'port-1',
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    side: 'SELL',
    quantity: '25',
    price: { amount: '155.00', currency: 'USD' },
    tradedAt: '2025-01-15T14:00:00Z',
  },
]

export const DATA_QUALITY_STATUS = {
  overall: 'OK',
  checks: [
    {
      name: 'Price Staleness',
      status: 'OK',
      message: 'All prices updated within 5 minutes',
      lastChecked: '2025-01-15T10:00:00Z',
    },
  ],
}

/**
 * Sets up route handlers to mock all API endpoints the app calls on startup.
 * This allows Playwright tests to run without a real backend.
 */
export async function mockAllApiRoutes(page: Page): Promise<void> {
  await page.route('**/api/v1/portfolios', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(TEST_PORTFOLIOS),
    })
  })

  await page.route('**/api/v1/portfolios/*/positions', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(TEST_POSITIONS),
    })
  })

  await page.route('**/api/v1/portfolios/*/trades', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(TEST_TRADES),
    })
  })

  await page.route('**/api/v1/data-quality/status', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(DATA_QUALITY_STATUS),
    })
  })

  await page.route('**/api/v1/notifications/rules', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  await page.route('**/api/v1/notifications/alerts*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  await page.route('**/api/v1/system/health', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'HEALTHY', services: [] }),
    })
  })

  // Position risk endpoint must return an array
  await page.route('**/api/v1/risk/positions/*', (route: Route) => {
    route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  // VaR endpoint -- return 404 (no VaR data available)
  await page.route('**/api/v1/risk/var/*', (route: Route) => {
    route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify(null),
    })
  })

  // Stress test scenarios
  await page.route('**/api/v1/risk/stress/scenarios', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  // Catch-all for remaining risk endpoints
  await page.route('**/api/v1/risk/**', (route: Route) => {
    route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify(null),
    })
  })

  await page.route('**/api/v1/portfolios/*/summary*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        portfolioId: 'port-1',
        baseCurrency: 'USD',
        totalNav: { amount: '168850.00', currency: 'USD' },
        totalUnrealizedPnl: { amount: '3050.00', currency: 'USD' },
        currencyBreakdown: [],
      }),
    })
  })

  // Block WebSocket connections by default -- individual tests can override this
  await page.route('**/ws/prices', (route: Route) => {
    route.abort()
  })
}
