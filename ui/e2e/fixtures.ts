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

  // Note: Playwright's page.route() does NOT intercept WebSocket connections.
  // To mock WebSocket behaviour, tests must use page.addInitScript() to replace
  // the browser's WebSocket constructor before the page loads.
  // By default (without mocking), the app's WebSocket will connect to whatever
  // the Vite dev server proxies to -- which may succeed or fail depending on
  // whether a real backend is running.
}

const ASSET_CLASSES = ['EQUITY', 'FX', 'BOND', 'COMMODITY', 'OPTION']
const TICKERS = [
  'AAPL', 'GOOGL', 'MSFT', 'AMZN', 'META', 'TSLA', 'NVDA', 'JPM',
  'V', 'JNJ', 'WMT', 'PG', 'UNH', 'HD', 'MA', 'DIS', 'PYPL', 'BAC',
  'INTC', 'VZ', 'KO', 'PEP', 'NFLX', 'ADBE', 'CRM', 'CMCSA', 'NKE',
  'T', 'MRK', 'ABT', 'XOM', 'CVX', 'LLY', 'TMO', 'AVGO', 'COST',
  'MDT', 'DHR', 'ACN', 'NEE', 'TXN', 'LIN', 'PM', 'HON', 'UPS',
  'ORCL', 'MS', 'BMY', 'QCOM', 'RTX', 'SBUX', 'BLK', 'AMGN', 'GE',
  'CAT', 'DE', 'LOW', 'ISRG', 'GS', 'AXP', 'SYK', 'MDLZ', 'EL',
  'BKNG', 'TGT', 'ADP', 'CI', 'MO', 'PLD', 'ZTS', 'CB', 'GILD',
  'SPGI', 'BDX', 'DUK', 'SO', 'CL', 'ICE', 'CSX', 'MMC', 'SHW',
  'CME', 'PNC', 'TFC', 'USB', 'AON', 'APD', 'NSC', 'FIS', 'EMR',
  'ECL', 'WM', 'ITW', 'EW', 'D', 'HUM', 'MCO', 'ETN', 'PSA', 'F',
  'GM', 'ATVI', 'REGN', 'KLAC', 'SLB', 'MPC', 'PSX', 'VLO', 'OXY',
  'AIG', 'MET', 'PRU', 'ALL', 'TRV', 'WBA', 'KHC', 'CTVA', 'DOW',
  'DD', 'BIIB', 'VRTX', 'MRNA', 'DXCM', 'IDXX', 'ALGN', 'ILMN',
  'ENPH', 'ODFL', 'CTAS', 'CDNS', 'SNPS', 'MCHP', 'FTNT', 'PANW',
]

/**
 * Generates N position fixtures with unique instrument IDs.
 */
export function generatePositions(count: number): PositionFixture[] {
  return Array.from({ length: count }, (_, i) => {
    const ticker = i < TICKERS.length ? TICKERS[i] : `INST_${String(i).padStart(3, '0')}`
    const price = 50 + (i * 7) % 300
    const qty = 10 + (i * 3) % 500
    const cost = price - (i % 10)
    const mv = price * qty
    const pnl = (price - cost) * qty
    return {
      portfolioId: 'port-1',
      instrumentId: ticker,
      assetClass: ASSET_CLASSES[i % ASSET_CLASSES.length],
      quantity: String(qty),
      averageCost: { amount: cost.toFixed(2), currency: 'USD' },
      marketPrice: { amount: price.toFixed(2), currency: 'USD' },
      marketValue: { amount: mv.toFixed(2), currency: 'USD' },
      unrealizedPnl: { amount: pnl.toFixed(2), currency: 'USD' },
    }
  })
}

/**
 * Overrides the positions endpoint to return `count` generated positions.
 * Call this AFTER mockAllApiRoutes (Playwright uses the first matching route handler).
 */
export async function mockManyPositions(page: Page, count: number): Promise<PositionFixture[]> {
  const positions = generatePositions(count)
  // Unroute the default positions handler and add a new one
  await page.unroute('**/api/v1/portfolios/*/positions')
  await page.route('**/api/v1/portfolios/*/positions', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(positions),
    })
  })
  return positions
}

export interface AlertRuleFixture {
  id: string
  name: string
  type: string
  threshold: number
  operator: string
  severity: string
  channels: string[]
  enabled: boolean
}

/**
 * Sets up mock routes for alert rule CRUD operations.
 * Maintains an in-memory array of rules that the tests can populate.
 * Call AFTER mockAllApiRoutes so that the new handlers override the defaults.
 */
export async function mockAlertRuleCrud(
  page: Page,
  initialRules: AlertRuleFixture[] = [],
): Promise<void> {
  let rules = [...initialRules]
  let nextId = initialRules.length + 1

  // Unroute default rules and alerts handlers so we can replace them
  await page.unroute('**/api/v1/notifications/rules')
  await page.unroute('**/api/v1/notifications/alerts*')

  await page.route('**/api/v1/notifications/alerts*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  await page.route('**/api/v1/notifications/rules', (route: Route) => {
    const method = route.request().method()
    if (method === 'GET') {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(rules),
      })
    } else if (method === 'POST') {
      const body = route.request().postDataJSON()
      const newRule: AlertRuleFixture = {
        id: `rule-${nextId++}`,
        name: body.name,
        type: body.type,
        threshold: body.threshold,
        operator: body.operator,
        severity: body.severity,
        channels: body.channels,
        enabled: true,
      }
      rules.push(newRule)
      route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(newRule),
      })
    } else {
      route.fallback()
    }
  })

  await page.route('**/api/v1/notifications/rules/*', (route: Route) => {
    const method = route.request().method()
    if (method === 'DELETE') {
      const url = route.request().url()
      const ruleId = url.split('/').pop()!
      rules = rules.filter((r) => r.id !== ruleId)
      route.fulfill({ status: 204 })
    } else {
      route.fallback()
    }
  })
}
