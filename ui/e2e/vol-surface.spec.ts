import { test, expect, type Page, type Route } from '@playwright/test'
import { mockAllApiRoutes } from './fixtures'

const VOL_SURFACE = {
  instrumentId: 'AAPL',
  asOfDate: '2026-03-25T10:00:00Z',
  source: 'BLOOMBERG',
  points: [
    { strike: 140, maturityDays: 30, impliedVol: 0.32 },
    { strike: 150, maturityDays: 30, impliedVol: 0.28 },
    { strike: 160, maturityDays: 30, impliedVol: 0.25 },
    { strike: 140, maturityDays: 90, impliedVol: 0.34 },
    { strike: 150, maturityDays: 90, impliedVol: 0.30 },
    { strike: 160, maturityDays: 90, impliedVol: 0.27 },
    { strike: 140, maturityDays: 180, impliedVol: 0.36 },
    { strike: 150, maturityDays: 180, impliedVol: 0.32 },
    { strike: 160, maturityDays: 180, impliedVol: 0.29 },
  ],
}

const VOL_SURFACE_DIFF = {
  instrumentId: 'AAPL',
  baseDate: '2026-03-25T10:00:00Z',
  compareDate: '2026-03-24T10:00:00Z',
  diffs: [
    { strike: 140, maturityDays: 30, baseVol: 0.32, compareVol: 0.31, diff: 0.01 },
    { strike: 150, maturityDays: 30, baseVol: 0.28, compareVol: 0.27, diff: 0.01 },
    { strike: 160, maturityDays: 30, baseVol: 0.25, compareVol: 0.24, diff: 0.01 },
  ],
}

const TEST_POSITION_RISK_AAPL = [
  {
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    marketValue: '15500.00',
    delta: '1234.56',
    gamma: '45.67',
    vega: '89.01',
    varContribution: '800.00',
    esContribution: '1000.00',
    percentageOfTotal: '100.0',
  },
]

async function mockVolSurfaceRoutes(page: Page): Promise<void> {
  await page.route('**/api/v1/volatility/AAPL/surface', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(VOL_SURFACE),
    })
  })

  await page.route('**/api/v1/volatility/AAPL/surface/diff**', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(VOL_SURFACE_DIFF),
    })
  })

  // 404 for any other instrument
  await page.route('**/api/v1/volatility/*/surface', (route: Route) => {
    route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
  })
}

async function mockPositionRiskWithAapl(page: Page): Promise<void> {
  await page.unroute('**/api/v1/risk/positions/*')
  await page.route('**/api/v1/risk/positions/*', (route: Route) => {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(TEST_POSITION_RISK_AAPL),
    })
  })
}

async function goToMarketDataTab(page: Page): Promise<void> {
  await page.goto('/')
  await page.getByTestId('tab-risk').click()
  await page.getByTestId('risk-subtab-market-data').click()
}

test.describe('Vol Surface - Market Data tab', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
    await mockVolSurfaceRoutes(page)
    await mockPositionRiskWithAapl(page)
  })

  test('Market Data sub-tab is visible in the Risk tab', async ({ page }) => {
    await page.goto('/')
    await page.getByTestId('tab-risk').click()

    await expect(page.getByTestId('risk-subtab-market-data')).toBeVisible()
  })

  test('clicking Market Data sub-tab shows the vol surface panel', async ({ page }) => {
    await goToMarketDataTab(page)

    await expect(page.getByTestId('vol-surface-panel')).toBeVisible()
  })

  test('vol surface panel shows instrument selector', async ({ page }) => {
    await goToMarketDataTab(page)

    await expect(page.getByTestId('instrument-selector')).toBeVisible()
  })

  test('vol surface panel shows empty state before instrument is selected', async ({ page }) => {
    await goToMarketDataTab(page)

    await expect(page.getByTestId('vol-surface-panel')).toContainText('Select an instrument to view the vol surface.')
  })

  test('selecting AAPL instrument loads and renders both charts', async ({ page }) => {
    await goToMarketDataTab(page)

    await page.getByTestId('instrument-selector').selectOption('AAPL')

    await expect(page.getByTestId('vol-skew-chart')).toBeVisible()
    await expect(page.getByTestId('vol-term-structure-chart')).toBeVisible()
  })

  test('skew chart renders SVG polylines for each maturity', async ({ page }) => {
    await goToMarketDataTab(page)
    await page.getByTestId('instrument-selector').selectOption('AAPL')

    await page.getByTestId('vol-skew-chart').waitFor({ state: 'visible' })
    const svg = page.getByTestId('vol-skew-chart').locator('svg')
    await expect(svg).toBeVisible()

    // Expect polylines for the three maturities: 30, 90, 180
    const polylines = svg.locator('polyline')
    await expect(polylines).toHaveCount(3)
  })

  test('term structure chart renders SVG polyline', async ({ page }) => {
    await goToMarketDataTab(page)
    await page.getByTestId('instrument-selector').selectOption('AAPL')

    await page.getByTestId('vol-term-structure-chart').waitFor({ state: 'visible' })
    const svg = page.getByTestId('vol-term-structure-chart').locator('svg')
    await expect(svg).toBeVisible()

    const polylines = svg.locator('polyline')
    await expect(polylines.first()).toBeVisible()
  })

  test('maturity toggle buttons are rendered for each maturity', async ({ page }) => {
    await goToMarketDataTab(page)
    await page.getByTestId('instrument-selector').selectOption('AAPL')

    await expect(page.getByTestId('maturity-toggle-30')).toBeVisible()
    await expect(page.getByTestId('maturity-toggle-90')).toBeVisible()
    await expect(page.getByTestId('maturity-toggle-180')).toBeVisible()
  })

  test('compare date picker is visible after selecting an instrument', async ({ page }) => {
    await goToMarketDataTab(page)
    await page.getByTestId('instrument-selector').selectOption('AAPL')

    await expect(page.getByTestId('compare-date-picker')).toBeVisible()
  })

  test('dashed diff overlay appears after selecting a compare date', async ({ page }) => {
    await goToMarketDataTab(page)
    await page.getByTestId('instrument-selector').selectOption('AAPL')

    // Wait for charts to render
    await page.getByTestId('vol-skew-chart').waitFor({ state: 'visible' })

    // Set a compare date
    await page.getByTestId('compare-date-picker').fill('2026-03-24')

    // After setting date, the diff should be fetched and dashed lines should appear
    const skewSvg = page.getByTestId('vol-skew-chart').locator('svg')
    await expect(skewSvg.locator('[stroke-dasharray]').first()).toBeVisible()
  })
})
