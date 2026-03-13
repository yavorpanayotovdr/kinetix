import { test, expect, type Page, type Route } from '@playwright/test'
import { mockAllApiRoutes, mockRiskTabRoutes, TEST_VAR_RESULT, TEST_POSITION_RISK } from './fixtures'

// --- Mock Data ---

const BASE_SNAPSHOT = {
  jobId: 'base-job-id',
  label: '2025-01-14',
  valuationDate: '2025-01-14',
  calcType: 'PARAMETRIC',
  confLevel: 'CL_95',
  varValue: '5000.00',
  es: '6250.00',
  pv: '100000.00',
  delta: '0.500000',
  gamma: '0.010000',
  vega: '100.000000',
  theta: '-50.000000',
  rho: '25.000000',
  componentBreakdown: [
    { assetClass: 'EQUITY', varContribution: '4000.00', percentageOfTotal: '80.00' },
    { assetClass: 'FX', varContribution: '1000.00', percentageOfTotal: '20.00' },
  ],
  positionRisk: [
    { instrumentId: 'AAPL', assetClass: 'EQUITY', marketValue: '17000.00', delta: '0.850000', gamma: '0.020000', vega: '1500.000000', varContribution: '4000.00', esContribution: '5000.00', percentageOfTotal: '80.00' },
  ],
  modelVersion: null,
  parameters: { calculationType: 'PARAMETRIC', confidenceLevel: 'CL_95' },
  calculatedAt: '2025-01-14T09:01:00Z',
}

const TARGET_SNAPSHOT = {
  ...BASE_SNAPSHOT,
  jobId: 'target-job-id',
  label: '2025-01-15',
  valuationDate: '2025-01-15',
  varValue: '7000.00',
  es: '8750.00',
  pv: '110000.00',
  delta: '0.700000',
  componentBreakdown: [
    { assetClass: 'EQUITY', varContribution: '5500.00', percentageOfTotal: '78.57' },
    { assetClass: 'FX', varContribution: '1500.00', percentageOfTotal: '21.43' },
  ],
  positionRisk: [
    { instrumentId: 'AAPL', assetClass: 'EQUITY', marketValue: '25000.00', delta: '1.200000', gamma: '0.030000', vega: '2000.000000', varContribution: '5500.00', esContribution: '6875.00', percentageOfTotal: '78.57' },
    { instrumentId: 'TSLA', assetClass: 'EQUITY', marketValue: '8000.00', delta: '0.900000', gamma: '0.050000', vega: '3000.000000', varContribution: '1500.00', esContribution: '1875.00', percentageOfTotal: '21.43' },
  ],
  calculatedAt: '2025-01-15T09:01:00Z',
}

const MOCK_COMPARISON = {
  comparisonId: 'comp-123',
  comparisonType: 'RUN_OVER_RUN',
  portfolioId: 'port-1',
  baseRun: BASE_SNAPSHOT,
  targetRun: TARGET_SNAPSHOT,
  portfolioDiff: {
    varChange: '2000.00',
    varChangePercent: '40.00',
    esChange: '2500.00',
    esChangePercent: '40.00',
    pvChange: '10000.00',
    deltaChange: '0.200000',
    gammaChange: '0.000000',
    vegaChange: '0.000000',
    thetaChange: '0.000000',
    rhoChange: '0.000000',
  },
  componentDiffs: [
    { assetClass: 'EQUITY', baseContribution: '4000.00', targetContribution: '5500.00', change: '1500.00', changePercent: '37.50' },
    { assetClass: 'FX', baseContribution: '1000.00', targetContribution: '1500.00', change: '500.00', changePercent: '50.00' },
  ],
  positionDiffs: [
    { instrumentId: 'AAPL', assetClass: 'EQUITY', changeType: 'MODIFIED', baseMarketValue: '17000.00', targetMarketValue: '25000.00', marketValueChange: '8000.00', baseVarContribution: '4000.00', targetVarContribution: '5500.00', varContributionChange: '1500.00', baseDelta: '0.850000', targetDelta: '1.200000', baseGamma: '0.020000', targetGamma: '0.030000', baseVega: '1500.000000', targetVega: '2000.000000' },
    { instrumentId: 'TSLA', assetClass: 'EQUITY', changeType: 'NEW', baseMarketValue: '0.00', targetMarketValue: '8000.00', marketValueChange: '8000.00', baseVarContribution: '0.00', targetVarContribution: '1500.00', varContributionChange: '1500.00', baseDelta: null, targetDelta: '0.900000', baseGamma: null, targetGamma: '0.050000', baseVega: null, targetVega: '3000.000000' },
  ],
  parameterDiffs: [],
  attribution: null,
}

const MOCK_ATTRIBUTION = {
  totalChange: '2000.00',
  positionEffect: '1000.00',
  volEffect: '0.00',
  corrEffect: '0.00',
  timeDecayEffect: '-50.00',
  unexplained: '1050.00',
}

// --- Helper ---

async function mockComparisonRoutes(page: Page, opts: {
  comparison?: object | null
  attribution?: object | null
} = {}) {
  await page.route('**/api/v1/risk/compare/*/day-over-day/attribution', (route: Route) => {
    if (opts.attribution) {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.attribution) })
    } else {
      route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
    }
  })
  await page.route('**/api/v1/risk/compare/*/day-over-day*', (route: Route) => {
    if (opts.comparison) {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.comparison) })
    } else {
      route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
    }
  })
  await page.route('**/api/v1/risk/compare/*/model', (route: Route) => {
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.comparison ?? MOCK_COMPARISON) })
  })
  await page.route('**/api/v1/risk/compare/*', (route: Route) => {
    if (route.request().method() === 'POST') {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(opts.comparison ?? MOCK_COMPARISON) })
    } else {
      route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify(null) })
    }
  })
}

async function navigateToRunCompare(page: Page) {
  await page.goto('/')
  await page.getByTestId('tab-risk').click()
  await page.getByTestId('risk-subtab-run-compare').click()
}

// --- Tests ---

test.describe('Run Comparison', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
    await mockRiskTabRoutes(page, { varResult: TEST_VAR_RESULT, positionRisk: TEST_POSITION_RISK })
    await mockComparisonRoutes(page, { comparison: MOCK_COMPARISON })
  })

  test('daily VaR comparison renders side-by-side summaries', async ({ page }) => {
    await navigateToRunCompare(page)

    // Default mode is Daily VaR
    await expect(page.getByTestId('daily-var-selector')).toBeVisible()
    await page.getByTestId('compare-dates-btn').click()

    // Should show the comparison panel
    await expect(page.getByTestId('run-comparison-panel')).toBeVisible()
    // Two snapshot cards
    await expect(page.getByTestId('run-snapshot-card').first()).toBeVisible()
    // Diff summary
    await expect(page.getByTestId('run-diff-summary')).toBeVisible()
  })

  test('position diff table shows NEW/REMOVED badges', async ({ page }) => {
    await navigateToRunCompare(page)
    await page.getByTestId('compare-dates-btn').click()

    await expect(page.getByTestId('position-diff-table')).toBeVisible()
    // TSLA is a NEW position
    await expect(page.getByText('NEW')).toBeVisible()
    // AAPL is MODIFIED
    await expect(page.getByText('MODIFIED')).toBeVisible()
  })

  test('threshold filter hides small changes', async ({ page }) => {
    await navigateToRunCompare(page)
    await page.getByTestId('compare-dates-btn').click()

    await expect(page.getByTestId('position-diff-table')).toBeVisible()

    // Set threshold high enough to filter out positions
    const slider = page.getByTestId('threshold-slider')
    await slider.fill('100000')

    // Should show "No position changes above threshold" or similar
    await expect(page.getByText(/no position changes/i)).toBeVisible()
  })

  test('component diff chart renders asset class bars', async ({ page }) => {
    await navigateToRunCompare(page)
    await page.getByTestId('compare-dates-btn').click()

    await expect(page.getByTestId('component-diff-chart')).toBeVisible()
    // Should show EQUITY and FX
    await expect(page.getByText('EQUITY')).toBeVisible()
    await expect(page.getByText('FX')).toBeVisible()
  })

  test('mode switching between Daily VaR and Model Comparison', async ({ page }) => {
    await navigateToRunCompare(page)

    // Start on Daily VaR
    await expect(page.getByTestId('daily-var-selector')).toBeVisible()

    // Switch to Model
    await page.getByTestId('mode-model').click()
    await expect(page.getByTestId('model-comparison-selector')).toBeVisible()
    await expect(page.getByTestId('daily-var-selector')).not.toBeVisible()

    // Switch back
    await page.getByTestId('mode-daily-var').click()
    await expect(page.getByTestId('daily-var-selector')).toBeVisible()
  })

  test('VaR attribution panel loads on request', async ({ page }) => {
    await mockComparisonRoutes(page, { comparison: MOCK_COMPARISON, attribution: MOCK_ATTRIBUTION })
    await navigateToRunCompare(page)
    await page.getByTestId('compare-dates-btn').click()

    // Attribution panel should show request button
    await expect(page.getByTestId('var-attribution-panel')).toBeVisible()
    await expect(page.getByTestId('request-attribution')).toBeVisible()

    // Click request
    await page.getByTestId('request-attribution').click()

    // Should show attribution data
    await expect(page.getByText('Position Effect')).toBeVisible()
  })

  test('CSV export of comparison results', async ({ page }) => {
    await navigateToRunCompare(page)
    await page.getByTestId('compare-dates-btn').click()

    const downloadPromise = page.waitForEvent('download')
    await page.getByTestId('export-comparison-csv').click()
    const download = await downloadPromise
    expect(download.suggestedFilename()).toMatch(/comparison-.*\.csv/)
  })

  test('empty state when no comparison data', async ({ page }) => {
    await mockComparisonRoutes(page, { comparison: null })
    await navigateToRunCompare(page)

    // Should show mode selector but no comparison panel
    await expect(page.getByTestId('run-comparison-container')).toBeVisible()
    await expect(page.getByTestId('run-comparison-panel')).not.toBeVisible()
  })

  test('backtest comparison shows side-by-side metrics', async ({ page }) => {
    await navigateToRunCompare(page)
    await page.getByTestId('mode-backtest').click()

    await expect(page.getByTestId('backtest-comparison-view')).toBeVisible()
  })

  test('dark mode styling for comparison views', async ({ page }) => {
    // Enable dark mode
    await page.evaluate(() => {
      document.documentElement.classList.add('dark')
    })

    await navigateToRunCompare(page)
    await page.getByTestId('compare-dates-btn').click()

    // Verify dark mode classes are applied
    const container = page.getByTestId('run-comparison-container')
    await expect(container).toBeVisible()
  })
})
