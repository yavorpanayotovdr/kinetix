import { test, expect } from '@playwright/test'
import {
  mockAllApiRoutes,
  mockIntradayVaRTimelineRoutes,
  TEST_INTRADAY_VAR_POINTS,
  TEST_INTRADAY_TRADE_ANNOTATIONS,
} from './fixtures'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function goToIntradayVaRTab(page: import('@playwright/test').Page) {
  await page.goto('/')
  await page.getByTestId('tab-risk').click()
  await page.waitForSelector('[data-testid="risk-subtab-intraday"]')
  await page.getByTestId('risk-subtab-intraday').click()
  await page.waitForSelector('[data-testid="intraday-var-panel"]')
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Intraday VaR timeline tab', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('Intraday sub-tab is visible in the Risk tab', async ({ page }) => {
    await page.goto('/')
    await page.getByTestId('tab-risk').click()

    await expect(page.getByTestId('risk-subtab-intraday')).toBeVisible()
  })

  test('clicking Intraday sub-tab shows the intraday panel', async ({ page }) => {
    await goToIntradayVaRTab(page)

    await expect(page.getByTestId('intraday-var-panel')).toBeVisible()
  })

  test('shows empty state when no VaR points are available', async ({ page }) => {
    // Default mockAllApiRoutes returns empty varPoints
    await goToIntradayVaRTab(page)

    await expect(page.getByTestId('intraday-var-chart')).toBeVisible()
    await expect(page.getByTestId('intraday-var-chart-empty')).toBeVisible()
    await expect(page.getByTestId('intraday-var-chart-empty')).toContainText('No intraday VaR data')
  })

  test('renders SVG chart when VaR points are loaded', async ({ page }) => {
    await mockIntradayVaRTimelineRoutes(page, 'port-1', TEST_INTRADAY_VAR_POINTS)

    await goToIntradayVaRTab(page)

    const chart = page.getByTestId('intraday-var-chart')
    await expect(chart.locator('svg')).toBeVisible()
  })

  test('displays latest VaR value in chart header', async ({ page }) => {
    await mockIntradayVaRTimelineRoutes(page, 'port-1', TEST_INTRADAY_VAR_POINTS)

    await goToIntradayVaRTab(page)

    await expect(page.getByTestId('intraday-var-latest')).toBeVisible()
    // Latest varValue is 12500.0, formatted as $12,500.00
    await expect(page.getByTestId('intraday-var-latest')).toContainText('12,500')
  })

  test('renders trade annotation markers when annotations are present', async ({ page }) => {
    await mockIntradayVaRTimelineRoutes(
      page,
      'port-1',
      TEST_INTRADAY_VAR_POINTS,
      TEST_INTRADAY_TRADE_ANNOTATIONS,
    )

    await goToIntradayVaRTab(page)

    const chart = page.getByTestId('intraday-var-chart')
    // Each annotation renders a polygon with data-testid="trade-marker"
    const markers = chart.locator('[data-testid="trade-marker"]')
    await expect(markers).toHaveCount(1)
  })

  test('switching away from and back to Intraday sub-tab preserves state', async ({ page }) => {
    await mockIntradayVaRTimelineRoutes(page, 'port-1', TEST_INTRADAY_VAR_POINTS)

    await goToIntradayVaRTab(page)

    // Switch to Dashboard
    await page.getByTestId('risk-subtab-dashboard').click()
    await expect(page.getByTestId('intraday-var-panel')).not.toBeVisible()

    // Switch back to Intraday
    await page.getByTestId('risk-subtab-intraday').click()
    await expect(page.getByTestId('intraday-var-panel')).toBeVisible()
  })
})
