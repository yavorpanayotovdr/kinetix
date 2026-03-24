import type { Page, Route } from '@playwright/test'

export interface PositionFixture {
  bookId: string
  instrumentId: string
  displayName?: string
  instrumentType?: string
  assetClass: string
  quantity: string
  averageCost: { amount: string; currency: string }
  marketPrice: { amount: string; currency: string }
  marketValue: { amount: string; currency: string }
  unrealizedPnl: { amount: string; currency: string }
}

export const TEST_BOOKS = [{ bookId: 'port-1' }]

export const TEST_POSITIONS: PositionFixture[] = [
  {
    bookId: 'port-1',
    instrumentId: 'AAPL',
    displayName: 'Apple Inc',
    instrumentType: 'STOCK',
    assetClass: 'EQUITY',
    quantity: '100',
    averageCost: { amount: '150.00', currency: 'USD' },
    marketPrice: { amount: '155.00', currency: 'USD' },
    marketValue: { amount: '15500.00', currency: 'USD' },
    unrealizedPnl: { amount: '500.00', currency: 'USD' },
  },
  {
    bookId: 'port-1',
    instrumentId: 'GOOGL',
    displayName: 'Alphabet Inc',
    instrumentType: 'STOCK',
    assetClass: 'EQUITY',
    quantity: '50',
    averageCost: { amount: '2800.00', currency: 'USD' },
    marketPrice: { amount: '2850.00', currency: 'USD' },
    marketValue: { amount: '142500.00', currency: 'USD' },
    unrealizedPnl: { amount: '2500.00', currency: 'USD' },
  },
  {
    bookId: 'port-1',
    instrumentId: 'EUR_USD',
    displayName: 'Euro/US Dollar',
    instrumentType: 'FX_SPOT',
    assetClass: 'FX',
    quantity: '10000',
    averageCost: { amount: '1.0800', currency: 'USD' },
    marketPrice: { amount: '1.0850', currency: 'USD' },
    marketValue: { amount: '10850.00', currency: 'USD' },
    unrealizedPnl: { amount: '50.00', currency: 'USD' },
  },
]

export interface TradeFixture {
  tradeId: string
  bookId: string
  instrumentId: string
  assetClass: string
  side: 'BUY' | 'SELL'
  quantity: string
  price: { amount: string; currency: string }
  tradedAt: string
}

export const TEST_TRADES: TradeFixture[] = [
  {
    tradeId: 'trade-1',
    bookId: 'port-1',
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    side: 'BUY',
    quantity: '100',
    price: { amount: '150.00', currency: 'USD' },
    tradedAt: '2025-01-15T10:30:00Z',
  },
  {
    tradeId: 'trade-2',
    bookId: 'port-1',
    instrumentId: 'GOOGL',
    assetClass: 'EQUITY',
    side: 'BUY',
    quantity: '50',
    price: { amount: '2800.00', currency: 'USD' },
    tradedAt: '2025-01-15T11:00:00Z',
  },
  {
    tradeId: 'trade-3',
    bookId: 'port-1',
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
  await page.route('**/api/v1/divisions', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  await page.route('**/api/v1/desks', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  await page.route('**/api/v1/firm/summary*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        bookId: 'firm',
        baseCurrency: 'USD',
        totalNav: { amount: '168850.00', currency: 'USD' },
        totalUnrealizedPnl: { amount: '3050.00', currency: 'USD' },
        currencyBreakdown: [],
      }),
    })
  })

  await page.route('**/api/v1/books', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(TEST_BOOKS),
    })
  })

  await page.route('**/api/v1/books/*/positions', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(TEST_POSITIONS),
    })
  })

  await page.route('**/api/v1/books/*/trades', (route: Route) => {
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

  // Intraday P&L series endpoint — return empty snapshots by default
  await page.route('**/api/v1/risk/pnl/intraday/**', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ bookId: 'port-1', snapshots: [] }),
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

  await page.route('**/api/v1/books/*/summary*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        bookId: 'port-1',
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
      bookId: 'port-1',
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
  await page.unroute('**/api/v1/books/*/positions')
  await page.route('**/api/v1/books/*/positions', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(positions),
    })
  })
  return positions
}

/**
 * Generates N trade fixtures with unique trade IDs.
 */
export function generateTrades(count: number): TradeFixture[] {
  const sides: ('BUY' | 'SELL')[] = ['BUY', 'SELL']
  return Array.from({ length: count }, (_, i) => {
    const ticker = i < TICKERS.length ? TICKERS[i] : `INST_${String(i).padStart(3, '0')}`
    const price = 50 + (i * 7) % 300
    const qty = 10 + (i * 3) % 500
    // Spread trades over a range of timestamps (1 minute apart)
    const baseTime = new Date('2025-01-15T09:00:00Z').getTime()
    const tradedAt = new Date(baseTime + i * 60_000).toISOString()
    return {
      tradeId: `trade-gen-${i}`,
      bookId: 'port-1',
      instrumentId: ticker,
      assetClass: ASSET_CLASSES[i % ASSET_CLASSES.length],
      side: sides[i % sides.length],
      quantity: String(qty),
      price: { amount: price.toFixed(2), currency: 'USD' },
      tradedAt,
    }
  })
}

/**
 * Overrides the trades endpoint to return `count` generated trades.
 * Call this AFTER mockAllApiRoutes (Playwright uses the first matching route handler).
 */
export async function mockManyTrades(page: Page, count: number): Promise<TradeFixture[]> {
  const trades = generateTrades(count)
  await page.unroute('**/api/v1/books/*/trades')
  await page.route('**/api/v1/books/*/trades', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(trades),
    })
  })
  return trades
}

// ---------------------------------------------------------------------------
// Position risk fixture data
// ---------------------------------------------------------------------------

export interface PositionRiskFixture {
  instrumentId: string
  assetClass: string
  marketValue: string
  delta: string | null
  gamma: string | null
  vega: string | null
  theta: string | null
  rho: string | null
  varContribution: string
  esContribution: string
  percentageOfTotal: string
}

export const TEST_POSITION_RISK: PositionRiskFixture[] = [
  {
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    marketValue: '15500.00',
    delta: '1550.25',
    gamma: '12.50',
    vega: '320.00',
    theta: '-45.00',
    rho: '15.00',
    varContribution: '485.50',
    esContribution: '620.00',
    percentageOfTotal: '42.50',
  },
  {
    instrumentId: 'GOOGL',
    assetClass: 'EQUITY',
    marketValue: '142500.00',
    delta: '7125.00',
    gamma: '45.00',
    vega: '890.00',
    theta: '-80.00',
    rho: '27.00',
    varContribution: '520.75',
    esContribution: '680.00',
    percentageOfTotal: '45.60',
  },
  {
    instrumentId: 'EUR_USD',
    assetClass: 'FX',
    marketValue: '10850.00',
    delta: null,
    gamma: null,
    vega: null,
    theta: null,
    rho: null,
    varContribution: '135.75',
    esContribution: '175.00',
    percentageOfTotal: '11.90',
  },
]

// ---------------------------------------------------------------------------
// Mixed P&L fixture (positive, negative, zero)
// ---------------------------------------------------------------------------

export const TEST_POSITIONS_MIXED_PNL: PositionFixture[] = [
  {
    bookId: 'port-1',
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    quantity: '100',
    averageCost: { amount: '150.00', currency: 'USD' },
    marketPrice: { amount: '155.00', currency: 'USD' },
    marketValue: { amount: '15500.00', currency: 'USD' },
    unrealizedPnl: { amount: '500.00', currency: 'USD' },
  },
  {
    bookId: 'port-1',
    instrumentId: 'TSLA',
    assetClass: 'EQUITY',
    quantity: '30',
    averageCost: { amount: '800.00', currency: 'USD' },
    marketPrice: { amount: '600.00', currency: 'USD' },
    marketValue: { amount: '18000.00', currency: 'USD' },
    unrealizedPnl: { amount: '-6000.00', currency: 'USD' },
  },
  {
    bookId: 'port-1',
    instrumentId: 'MSFT',
    assetClass: 'EQUITY',
    quantity: '50',
    averageCost: { amount: '300.00', currency: 'USD' },
    marketPrice: { amount: '300.00', currency: 'USD' },
    marketValue: { amount: '15000.00', currency: 'USD' },
    unrealizedPnl: { amount: '0.00', currency: 'USD' },
  },
]

// ---------------------------------------------------------------------------
// Multi-currency portfolio summary
// ---------------------------------------------------------------------------

export const TEST_PORTFOLIO_SUMMARY_MULTI_CURRENCY = {
  bookId: 'port-1',
  baseCurrency: 'USD',
  totalNav: { amount: '250000.00', currency: 'USD' },
  totalUnrealizedPnl: { amount: '12500.00', currency: 'USD' },
  currencyBreakdown: [
    {
      currency: 'USD',
      localValue: { amount: '168850.00', currency: 'USD' },
      baseValue: { amount: '168850.00', currency: 'USD' },
      fxRate: '1.0000',
    },
    {
      currency: 'EUR',
      localValue: { amount: '75000.00', currency: 'EUR' },
      baseValue: { amount: '81150.00', currency: 'USD' },
      fxRate: '1.0820',
    },
  ],
}

// ---------------------------------------------------------------------------
// What-If analysis response
// ---------------------------------------------------------------------------

export const TEST_WHATIF_RESPONSE = {
  baseVaR: '1142.00',
  baseExpectedShortfall: '1502.90',
  baseGreeks: {
    bookId: 'port-1',
    assetClassGreeks: [
      { assetClass: 'EQUITY', delta: '8675.25', gamma: '57.50', vega: '1210.00' },
    ],
    theta: '-125.50',
    rho: '42.30',
    calculatedAt: '2025-01-15T12:00:00Z',
  },
  basePositionRisk: [
    {
      instrumentId: 'AAPL',
      assetClass: 'EQUITY',
      marketValue: '15500.00',
      delta: '1550.25',
      gamma: '12.50',
      vega: '320.00',
      theta: '-45.00',
      rho: '15.00',
      varContribution: '485.50',
      esContribution: '620.00',
      percentageOfTotal: '42.50',
    },
  ],
  hypotheticalVaR: '1380.50',
  hypotheticalExpectedShortfall: '1820.00',
  hypotheticalGreeks: {
    bookId: 'port-1',
    assetClassGreeks: [
      { assetClass: 'EQUITY', delta: '13175.25', gamma: '82.50', vega: '1710.00' },
    ],
    theta: '-180.75',
    rho: '65.10',
    calculatedAt: '2025-01-15T12:00:00Z',
  },
  hypotheticalPositionRisk: [
    {
      instrumentId: 'AAPL',
      assetClass: 'EQUITY',
      marketValue: '15500.00',
      delta: '1550.25',
      gamma: '12.50',
      vega: '320.00',
      theta: '-45.00',
      rho: '15.00',
      varContribution: '485.50',
      esContribution: '620.00',
      percentageOfTotal: '35.20',
    },
    {
      instrumentId: 'SPY',
      assetClass: 'EQUITY',
      marketValue: '45000.00',
      delta: '4500.00',
      gamma: '25.00',
      vega: '500.00',
      theta: '-55.25',
      rho: '22.80',
      varContribution: '895.00',
      esContribution: '1200.00',
      percentageOfTotal: '64.80',
    },
  ],
  varChange: '238.50',
  esChange: '317.10',
  calculatedAt: '2025-01-15T12:00:00Z',
}

// ---------------------------------------------------------------------------
// Mock helpers -- call AFTER mockAllApiRoutes
// ---------------------------------------------------------------------------

/**
 * Overrides the position risk endpoint to return the given risk data.
 * Call AFTER mockAllApiRoutes.
 */
export async function mockPositionRisk(page: Page, risk: PositionRiskFixture[]): Promise<void> {
  await page.unroute('**/api/v1/risk/positions/*')
  await page.route('**/api/v1/risk/positions/*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(risk),
    })
  })
}

/**
 * Overrides the positions endpoint to return the given positions.
 * Call AFTER mockAllApiRoutes.
 */
export async function mockPositions(page: Page, positions: PositionFixture[]): Promise<void> {
  await page.unroute('**/api/v1/books/*/positions')
  await page.route('**/api/v1/books/*/positions', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(positions),
    })
  })
}

/**
 * Overrides the portfolio summary endpoint to return the given summary.
 * Call AFTER mockAllApiRoutes.
 */
export async function mockPortfolioSummary(page: Page, summary: object): Promise<void> {
  await page.unroute('**/api/v1/books/*/summary*')
  await page.route('**/api/v1/books/*/summary*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(summary),
    })
  })
  // Also override firm-level summary (used at firm hierarchy level)
  await page.unroute('**/api/v1/firm/summary*')
  await page.route('**/api/v1/firm/summary*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(summary),
    })
  })
}

/**
 * Adds a route handler for the What-If POST endpoint.
 * Call AFTER mockAllApiRoutes.
 */
export async function mockWhatIfAnalysis(page: Page, response: object): Promise<void> {
  await page.route('**/api/v1/risk/what-if/*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(response),
    })
  })
}

/**
 * Generates deterministic position risk data for an array of positions.
 * Useful for sorting and pagination tests that need many positions with risk.
 */
export function generatePositionRisk(positions: PositionFixture[]): PositionRiskFixture[] {
  return positions.map((pos, i) => {
    const isFx = pos.assetClass === 'FX'
    return {
      instrumentId: pos.instrumentId,
      assetClass: pos.assetClass,
      marketValue: pos.marketValue.amount,
      delta: isFx ? null : String((100 + i * 50).toFixed(2)),
      gamma: isFx ? null : String((1 + i * 0.5).toFixed(2)),
      vega: isFx ? null : String((10 + i * 3).toFixed(2)),
      theta: isFx ? null : String((-5 - i).toFixed(2)),
      rho: isFx ? null : String((2 + i * 0.3).toFixed(2)),
      varContribution: String((50 + i * 10).toFixed(2)),
      esContribution: String((65 + i * 13).toFixed(2)),
      percentageOfTotal: (100 / positions.length).toFixed(2),
    }
  })
}

// ---------------------------------------------------------------------------
// Alert event fixture
// ---------------------------------------------------------------------------

export interface AlertEventFixture {
  id: string
  ruleId: string
  ruleName: string
  type: string
  severity: string
  message: string
  currentValue: number
  threshold: number
  bookId: string
  triggeredAt: string
  status: string
}

// ---------------------------------------------------------------------------
// Risk Tab fixture data
// ---------------------------------------------------------------------------

export const TEST_VAR_RESULT = {
  bookId: 'port-1',
  varValue: '125000.50',
  expectedShortfall: '187500.75',
  confidenceLevel: 'CL_95',
  calculationType: 'PARAMETRIC',
  componentBreakdown: [
    { assetClass: 'EQUITY', varContribution: '80000.30', percentageOfTotal: '64' },
    { assetClass: 'FX', varContribution: '55000.20', percentageOfTotal: '44' },
  ],
  calculatedAt: '2025-01-15T12:00:00Z',
  greeks: {
    bookId: 'port-1',
    assetClassGreeks: [
      { assetClass: 'EQUITY', delta: '1500.00', gamma: '25.00', vega: '800.00' },
      { assetClass: 'FX', delta: '500.00', gamma: '10.00', vega: '200.00' },
    ],
    theta: '-350.00',
    rho: '120.00',
    calculatedAt: '2025-01-15T12:00:00Z',
  },
  pvValue: '5000000.00',
  computedOutputs: ['VAR', 'EXPECTED_SHORTFALL', 'GREEKS', 'PV'],
}

export const TEST_HISTORICAL_VAR_RESULT = {
  bookId: 'port-1',
  varValue: '98000.25',
  expectedShortfall: '147000.38',
  confidenceLevel: 'CL_95',
  calculationType: 'PARAMETRIC',
  componentBreakdown: [
    { assetClass: 'EQUITY', varContribution: '63000.15', percentageOfTotal: '64' },
    { assetClass: 'FX', varContribution: '35000.10', percentageOfTotal: '36' },
  ],
  calculatedAt: '2025-01-14T18:00:00Z',
  greeks: {
    bookId: 'port-1',
    assetClassGreeks: [
      { assetClass: 'EQUITY', delta: '1200.00', gamma: '20.00', vega: '650.00' },
      { assetClass: 'FX', delta: '400.00', gamma: '8.00', vega: '150.00' },
    ],
    theta: '-280.00',
    rho: '95.00',
    calculatedAt: '2025-01-14T18:00:00Z',
  },
  pvValue: '4500000.00',
  computedOutputs: ['VAR', 'EXPECTED_SHORTFALL', 'GREEKS', 'PV'],
  valuationDate: '2025-01-14',
}

export const TEST_HISTORICAL_POSITION_RISK: PositionRiskFixture[] = [
  { instrumentId: 'AAPL', assetClass: 'EQUITY', marketValue: '14800.00', delta: '148.00', gamma: '2.30', vega: '42.00', theta: '-11.50', rho: '7.50', varContribution: '4200.00', esContribution: '6300.00', percentageOfTotal: '38.00' },
  { instrumentId: 'EUR_USD', assetClass: 'FX', marketValue: '10200.00', delta: '102.00', gamma: null, vega: null, theta: null, rho: '14.00', varContribution: '2800.00', esContribution: '4200.00', percentageOfTotal: '25.00' },
]

export const TEST_POSITION_RISK_FULL: PositionRiskFixture[] = [
  { instrumentId: 'AAPL', assetClass: 'EQUITY', marketValue: '15500.00', delta: '155.00', gamma: '2.50', vega: '45.00', theta: '-12.50', rho: '8.00', varContribution: '5000.00', esContribution: '7500.00', percentageOfTotal: '35.00' },
  { instrumentId: 'EUR_USD', assetClass: 'FX', marketValue: '10850.00', delta: '108.50', gamma: null, vega: null, theta: null, rho: '15.00', varContribution: '3000.00', esContribution: '4500.00', percentageOfTotal: '21.00' },
  { instrumentId: 'GOOGL', assetClass: 'EQUITY', marketValue: '142500.00', delta: '1425.00', gamma: '15.00', vega: '350.00', theta: '-42.00', rho: '95.00', varContribution: '2000.00', esContribution: '3000.00', percentageOfTotal: '14.00' },
]

export const TEST_JOB_HISTORY = {
  items: [
    {
      jobId: 'job-1',
      bookId: 'port-1',
      triggerType: 'ON_DEMAND',
      status: 'COMPLETED',
      startedAt: '2025-01-15T12:00:00Z',
      completedAt: '2025-01-15T12:00:05Z',
      durationMs: 5000,
      calculationType: 'PARAMETRIC',
      confidenceLevel: 'CL_95',
      varValue: 125000.50,
      expectedShortfall: 187500.75,
      pvValue: 5000000.00,
      delta: null,
      gamma: null,
      vega: null,
      theta: null,
      rho: null,
      runLabel: null,
      promotedAt: null,
      promotedBy: null,
      manifestId: 'manifest-abc-123',
    },
  ],
  totalCount: 1,
}

export const TEST_ALERTS: AlertEventFixture[] = [
  {
    id: 'alert-1',
    ruleId: 'r1',
    ruleName: 'VAR_BREACH',
    type: 'VAR_BREACH',
    severity: 'CRITICAL',
    message: 'VaR exceeded limit',
    currentValue: 125000,
    threshold: 100000,
    bookId: 'port-1',
    triggeredAt: new Date(Date.now() - 5 * 60_000).toISOString(),
    status: 'TRIGGERED',
  },
  {
    id: 'alert-2',
    ruleId: 'r1',
    ruleName: 'VAR_BREACH',
    type: 'VAR_BREACH',
    severity: 'WARNING',
    message: 'VaR approaching limit',
    currentValue: 95000,
    threshold: 100000,
    bookId: 'port-1',
    triggeredAt: new Date(Date.now() - 30 * 60_000).toISOString(),
    status: 'TRIGGERED',
  },
]

export const TEST_VAR_LIMIT_RULE = [
  {
    id: 'rule-1',
    name: 'VaR Breach',
    type: 'VAR_BREACH',
    threshold: 200000,
    operator: 'GREATER_THAN',
    severity: 'CRITICAL',
    channels: ['UI'],
    enabled: true,
  },
]

export const TEST_JOB_DETAIL = {
  jobId: 'job-1',
  bookId: 'port-1',
  triggerType: 'ON_DEMAND',
  status: 'COMPLETED',
  startedAt: '2025-01-15T12:00:00Z',
  completedAt: '2025-01-15T12:00:05Z',
  durationMs: 5000,
  calculationType: 'PARAMETRIC',
  confidenceLevel: 'CL_95',
  varValue: 125000.50,
  expectedShortfall: 187500.75,
  pvValue: 5000000.00,
  runLabel: null,
  promotedAt: null,
  promotedBy: null,
  manifestId: 'manifest-abc-123',
  phases: [
    {
      name: 'FETCH_POSITIONS',
      status: 'COMPLETED',
      startedAt: '2025-01-15T12:00:00Z',
      completedAt: '2025-01-15T12:00:01Z',
      durationMs: 1000,
      details: { positionCount: '5' },
      error: null,
    },
    {
      name: 'FETCH_MARKET_DATA',
      status: 'COMPLETED',
      startedAt: '2025-01-15T12:00:01Z',
      completedAt: '2025-01-15T12:00:02Z',
      durationMs: 1000,
      details: {},
      error: null,
    },
    {
      name: 'VALUATION',
      status: 'COMPLETED',
      startedAt: '2025-01-15T12:00:02Z',
      completedAt: '2025-01-15T12:00:05Z',
      durationMs: 3000,
      details: {},
      error: null,
    },
  ],
  error: null,
  valuationDate: '2025-01-15',
}

export const TEST_RUN_MANIFEST = {
  manifestId: 'manifest-abc-123',
  jobId: 'job-1',
  bookId: 'port-1',
  valuationDate: '2025-01-15',
  capturedAt: '2025-01-15T12:00:02Z',
  modelVersion: '1.4.2-abc9876',
  calculationType: 'PARAMETRIC',
  confidenceLevel: 'CL_95',
  timeHorizonDays: 1,
  numSimulations: 10000,
  monteCarloSeed: 0,
  positionCount: 5,
  positionDigest: 'a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2',
  marketDataDigest: 'b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3',
  inputDigest: 'c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4',
  status: 'COMPLETE',
  varValue: 125000.50,
  expectedShortfall: 187500.75,
  outputDigest: 'd4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5',
}

export const TEST_REPLAY_RESPONSE_MATCH = {
  manifest: TEST_RUN_MANIFEST,
  replayVarValue: 125000.50,
  replayExpectedShortfall: 187500.75,
  replayModelVersion: '1.4.2-abc9876',
  inputDigestMatch: true,
  originalInputDigest: TEST_RUN_MANIFEST.inputDigest,
  replayInputDigest: TEST_RUN_MANIFEST.inputDigest,
  originalVarValue: 125000.50,
  originalExpectedShortfall: 187500.75,
}

export const TEST_REPLAY_RESPONSE_MISMATCH = {
  manifest: TEST_RUN_MANIFEST,
  replayVarValue: 124850.25,
  replayExpectedShortfall: 187000.00,
  replayModelVersion: '1.4.3-def1234',
  inputDigestMatch: false,
  originalInputDigest: TEST_RUN_MANIFEST.inputDigest,
  replayInputDigest: 'e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6',
  originalVarValue: 125000.50,
  originalExpectedShortfall: 187500.75,
}

export const TEST_PNL_ATTRIBUTION = {
  totalPnl: '15250.00',
  deltaPnl: '8500.00',
  gammaPnl: '2200.00',
  vegaPnl: '1800.00',
  thetaPnl: '-1500.00',
  rhoPnl: '750.00',
  unexplainedPnl: '3500.00',
}

// ---------------------------------------------------------------------------
// Risk Tab route overrides
// ---------------------------------------------------------------------------

export interface MockRiskTabOptions {
  varResult?: object | null
  varStatus?: number
  historicalVarResult?: object | null
  positionRisk?: object[] | null
  positionRiskStatus?: number
  historicalPositionRisk?: object[] | null
  jobHistory?: object | null
  jobDetail?: object | null
  rules?: object[]
  alerts?: object[]
  sodStatus?: object | null
  pnlAttribution?: object | null
  stressScenarios?: string[]
  stressResult?: object | null
  postVarResult?: object | null
  postVarDelay?: number
  manifestResponse?: object | null
  replayResponse?: object | null
  replayStatus?: number
}

/**
 * Overrides the default risk-related API mocks registered by `mockAllApiRoutes`.
 * Call this AFTER `mockAllApiRoutes`. Unroutes the catch-all handlers and registers
 * specific ones based on the options provided.
 */
export async function mockRiskTabRoutes(
  page: Page,
  opts: MockRiskTabOptions = {},
): Promise<void> {
  // Remove all existing handlers that we need to override
  await page.unroute('**/api/v1/risk/**')
  await page.unroute('**/api/v1/risk/positions/*')
  await page.unroute('**/api/v1/risk/var/*')
  await page.unroute('**/api/v1/risk/stress/scenarios')
  await page.unroute('**/api/v1/notifications/rules')
  await page.unroute('**/api/v1/notifications/alerts*')

  // NOTE: Playwright gives priority to the LAST registered matching route.
  // Register the catch-all FIRST so that more specific routes registered
  // afterward take precedence.

  // 1. Catch-all for remaining risk endpoints (lowest priority)
  await page.route('**/api/v1/risk/**', (route: Route) => {
    route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
  })

  // 2. Notifications alerts
  await page.route('**/api/v1/notifications/alerts*', (route: Route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.alerts ?? []) })
  })

  // 3. Notifications rules
  await page.route('**/api/v1/notifications/rules', (route: Route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.rules ?? []) })
  })

  // 4. Stress run (generic pattern before scenarios)
  await page.route('**/api/v1/risk/stress/*', (route: Route) => {
    if (route.request().method() === 'POST') {
      if (opts.stressResult) {
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.stressResult) })
      } else {
        route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
      }
    } else {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
    }
  })

  // 5. Stress scenarios (more specific, registered after generic stress)
  await page.route('**/api/v1/risk/stress/scenarios', (route: Route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.stressScenarios ?? []) })
  })

  // 6. P&L attribution GET (generic pattern first)
  await page.route('**/api/v1/risk/pnl-attribution/*', (route: Route) => {
    if (opts.pnlAttribution) {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.pnlAttribution) })
    } else {
      route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
    }
  })

  // 7. P&L attribution compute POST (more specific, registered after)
  await page.route('**/api/v1/risk/pnl-attribution/*/compute', (route: Route) => {
    if (opts.pnlAttribution) {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.pnlAttribution) })
    } else {
      route.fulfill({ status: 412, contentType: 'application/json', body: JSON.stringify({ message: 'No baseline' }) })
    }
  })

  // 8. SOD baseline status
  await page.route('**/api/v1/risk/sod-snapshot/*/status', (route: Route) => {
    if (opts.sodStatus === undefined || opts.sodStatus === null) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ exists: false, baselineDate: null, snapshotType: null, createdAt: null, sourceJobId: null, calculationType: null }),
      })
    } else {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.sodStatus) })
    }
  })

  // 8b. Replay POST
  await page.route('**/api/v1/risk/runs/*/replay', (route: Route) => {
    if (route.request().method() === 'POST') {
      const status = opts.replayStatus ?? 200
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(opts.replayResponse ?? null),
      })
    } else {
      route.fallback()
    }
  })

  // 8c. Manifest GET
  await page.route('**/api/v1/risk/runs/*/manifest', (route: Route) => {
    if (opts.manifestResponse) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(opts.manifestResponse),
      })
    } else {
      route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify(null),
      })
    }
  })

  // 9. Job history (list)
  await page.route('**/api/v1/risk/jobs/*', (route: Route) => {
    const data = opts.jobHistory ?? { items: [], totalCount: 0 }
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })
  })

  // 9b. Job detail (more specific — registered after so it takes priority)
  await page.route('**/api/v1/risk/jobs/detail/*', (route: Route) => {
    if (opts.jobDetail) {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.jobDetail) })
    } else {
      route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
    }
  })

  // 10. Position risk
  await page.route('**/api/v1/risk/positions/*', (route: Route) => {
    const url = route.request().url()
    const isHistorical = url.includes('valuationDate=')
    const data = isHistorical ? (opts.historicalPositionRisk ?? opts.positionRisk) : opts.positionRisk
    if (data === undefined || data === null) {
      route.fulfill({ status: opts.positionRiskStatus ?? 404, contentType: 'application/json', body: JSON.stringify([]) })
    } else {
      route.fulfill({
        status: opts.positionRiskStatus ?? 200,
        contentType: 'application/json',
        body: JSON.stringify(data),
      })
    }
  })

  // 11. VaR GET/POST (highest priority — registered last)
  await page.route('**/api/v1/risk/var/*', (route: Route) => {
    const method = route.request().method()
    if (method === 'POST') {
      const body = opts.postVarResult ?? opts.varResult ?? null
      const delay = opts.postVarDelay ?? 0
      if (delay > 0) {
        setTimeout(
          () => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) }),
          delay,
        )
      } else {
        route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
      }
    } else {
      const url = route.request().url()
      const isHistorical = url.includes('valuationDate=')
      const result = isHistorical ? (opts.historicalVarResult ?? opts.varResult) : opts.varResult
      if (result === undefined || result === null) {
        route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
      } else {
        route.fulfill({
          status: opts.varStatus ?? 200,
          contentType: 'application/json',
          body: JSON.stringify(result),
        })
      }
    }
  })

  // 12. Cross-book VaR — registered AFTER the generic var/* handler so it takes
  //     priority for /api/v1/risk/var/cross-book and /api/v1/risk/var/cross-book/*
  await page.route('**/api/v1/risk/var/cross-book', (route: Route) => {
    route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
  })
  await page.route('**/api/v1/risk/var/cross-book/*', (route: Route) => {
    route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
  })
}

// ---------------------------------------------------------------------------
// EOD Timeline fixture data and mock helpers
// ---------------------------------------------------------------------------

export interface EodTimelineEntryFixture {
  valuationDate: string
  jobId: string
  varValue: number | null
  expectedShortfall: number | null
  pvValue: number | null
  delta: number | null
  gamma: number | null
  vega: number | null
  theta: number | null
  rho: number | null
  promotedAt: string | null
  promotedBy: string | null
  varChange: number | null
  varChangePct: number | null
  esChange: number | null
  calculationType: string | null
  confidenceLevel: number | null
}

export const TEST_EOD_TIMELINE_ENTRIES: EodTimelineEntryFixture[] = [
  {
    valuationDate: '2026-03-13',
    jobId: 'job-eod-13',
    varValue: 108000,
    expectedShortfall: 162000,
    pvValue: 5200000,
    delta: 0.52,
    gamma: 0.011,
    vega: 220,
    theta: -52,
    rho: 26,
    promotedAt: '2026-03-13T19:05:00Z',
    promotedBy: 'risk-manager',
    varChange: 8000,
    varChangePct: 8.0,
    esChange: 12000,
    calculationType: 'PARAMETRIC',
    confidenceLevel: 0.99,
  },
  {
    valuationDate: '2026-03-12',
    jobId: 'job-eod-12',
    varValue: 100000,
    expectedShortfall: 150000,
    pvValue: 5000000,
    delta: 0.5,
    gamma: 0.01,
    vega: 200,
    theta: -50,
    rho: 25,
    promotedAt: '2026-03-12T19:00:00Z',
    promotedBy: 'risk-manager',
    varChange: null,
    varChangePct: null,
    esChange: null,
    calculationType: 'PARAMETRIC',
    confidenceLevel: 0.99,
  },
  {
    valuationDate: '2026-03-11',
    jobId: 'job-eod-11',
    varValue: 95000,
    expectedShortfall: 142500,
    pvValue: 4900000,
    delta: 0.48,
    gamma: 0.009,
    vega: 190,
    theta: -48,
    rho: 24,
    promotedAt: '2026-03-11T18:55:00Z',
    promotedBy: 'risk-manager',
    varChange: -5000,
    varChangePct: -5.0,
    esChange: -7500,
    calculationType: 'PARAMETRIC',
    confidenceLevel: 0.99,
  },
  {
    // Missing EOD — no varValue
    valuationDate: '2026-03-10',
    jobId: '',
    varValue: null,
    expectedShortfall: null,
    pvValue: null,
    delta: null,
    gamma: null,
    vega: null,
    theta: null,
    rho: null,
    promotedAt: null,
    promotedBy: null,
    varChange: null,
    varChangePct: null,
    esChange: null,
    calculationType: null,
    confidenceLevel: null,
  },
  {
    valuationDate: '2026-03-07',
    jobId: 'job-eod-07',
    varValue: 102000,
    expectedShortfall: 153000,
    pvValue: 5050000,
    delta: 0.51,
    gamma: 0.010,
    vega: 205,
    theta: -51,
    rho: 25,
    promotedAt: '2026-03-07T19:10:00Z',
    promotedBy: 'risk-manager',
    varChange: 2000,
    varChangePct: 2.0,
    esChange: 3000,
    calculationType: 'PARAMETRIC',
    confidenceLevel: 0.99,
  },
]

export const TEST_EOD_TIMELINE_RESPONSE = {
  bookId: 'port-1',
  from: '2026-02-01',
  to: '2026-03-15',
  entries: TEST_EOD_TIMELINE_ENTRIES,
}

export const TEST_EOD_TIMELINE_EMPTY = {
  bookId: 'port-1',
  from: '2026-02-01',
  to: '2026-03-15',
  entries: [],
}

/**
 * Sets up mock routes for the EOD timeline endpoint.
 * Call AFTER mockAllApiRoutes — this must come before the catch-all risk/** handler.
 */
export async function mockEodTimelineRoutes(
  page: Page,
  response: object = TEST_EOD_TIMELINE_RESPONSE,
  status = 200,
): Promise<void> {
  await page.route('**/api/v1/risk/eod-timeline/**', (route: Route) => {
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(status === 200 ? response : { error: 'Internal server error' }),
    })
  })
}

// ---------------------------------------------------------------------------
// Existing types and helpers below
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Intraday P&L fixtures
// ---------------------------------------------------------------------------

export interface IntradayPnlSnapshotFixture {
  snapshotAt: string
  baseCurrency: string
  trigger: string
  totalPnl: string
  realisedPnl: string
  unrealisedPnl: string
  deltaPnl: string
  gammaPnl: string
  vegaPnl: string
  thetaPnl: string
  rhoPnl: string
  unexplainedPnl: string
  highWaterMark: string
}

export const TEST_INTRADAY_PNL_SNAPSHOTS: IntradayPnlSnapshotFixture[] = [
  {
    snapshotAt: '2026-03-24T09:30:00Z',
    baseCurrency: 'USD',
    trigger: 'position_change',
    totalPnl: '1000.00',
    realisedPnl: '200.00',
    unrealisedPnl: '800.00',
    deltaPnl: '800.00',
    gammaPnl: '50.00',
    vegaPnl: '30.00',
    thetaPnl: '-10.00',
    rhoPnl: '5.00',
    unexplainedPnl: '125.00',
    highWaterMark: '1200.00',
  },
  {
    snapshotAt: '2026-03-24T09:31:00Z',
    baseCurrency: 'USD',
    trigger: 'price_update',
    totalPnl: '1500.00',
    realisedPnl: '500.00',
    unrealisedPnl: '1000.00',
    deltaPnl: '1200.00',
    gammaPnl: '80.00',
    vegaPnl: '40.00',
    thetaPnl: '-15.00',
    rhoPnl: '7.00',
    unexplainedPnl: '188.00',
    highWaterMark: '1800.00',
  },
]

export async function mockIntradayPnlRoutes(
  page: Page,
  bookId: string,
  snapshots: IntradayPnlSnapshotFixture[],
): Promise<void> {
  await page.unroute(`**/api/v1/risk/pnl/intraday/${bookId}*`)
  await page.route(`**/api/v1/risk/pnl/intraday/${bookId}*`, (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ bookId, snapshots }),
    })
  })
}
