import { test, expect } from '@playwright/test'
import { mockAllApiRoutes, mockPositionRisk, TEST_POSITION_RISK } from './fixtures'

test.describe('Risk Metrics', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows risk column headers when risk data available', async ({ page }) => {
    await mockPositionRisk(page, TEST_POSITION_RISK)

    await page.goto('/')
    await page.waitForSelector('[data-testid="sort-delta"]')

    await expect(page.getByTestId('sort-delta')).toBeVisible()
    await expect(page.getByTestId('sort-gamma')).toBeVisible()
    await expect(page.getByTestId('sort-vega')).toBeVisible()
    await expect(page.getByTestId('sort-var-pct')).toBeVisible()
  })

  test('displays "Position Details" and "Risk Metrics" header groups', async ({ page }) => {
    await mockPositionRisk(page, TEST_POSITION_RISK)

    await page.goto('/')
    await page.waitForSelector('[data-testid="header-group-position"]')

    await expect(page.getByTestId('header-group-position')).toBeVisible()
    await expect(page.getByTestId('header-group-position')).toHaveText('Position Details')
    await expect(page.getByTestId('header-group-risk')).toBeVisible()
    await expect(page.getByTestId('header-group-risk')).toHaveText('Risk Metrics')
  })

  test('does not display header groups when risk data absent', async ({ page }) => {
    // Default mock returns 404 for risk endpoint -- no risk data
    await page.goto('/')
    await page.waitForSelector('[data-testid="position-row-AAPL"]')

    await expect(page.getByTestId('header-group-position')).not.toBeVisible()
    await expect(page.getByTestId('header-group-risk')).not.toBeVisible()
  })

  test('renders delta values correctly, em-dash for null', async ({ page }) => {
    await mockPositionRisk(page, TEST_POSITION_RISK)

    await page.goto('/')
    await page.waitForSelector('[data-testid="delta-AAPL"]')

    await expect(page.getByTestId('delta-AAPL')).toHaveText('1,550.25')
    await expect(page.getByTestId('delta-GOOGL')).toHaveText('7,125.00')
    await expect(page.getByTestId('delta-EUR_USD')).toHaveText('\u2014')
  })

  test('renders gamma values correctly', async ({ page }) => {
    await mockPositionRisk(page, TEST_POSITION_RISK)

    await page.goto('/')
    await page.waitForSelector('[data-testid="gamma-AAPL"]')

    await expect(page.getByTestId('gamma-AAPL')).toHaveText('12.50')
    await expect(page.getByTestId('gamma-GOOGL')).toHaveText('45.00')
    await expect(page.getByTestId('gamma-EUR_USD')).toHaveText('\u2014')
  })

  test('renders vega values correctly', async ({ page }) => {
    await mockPositionRisk(page, TEST_POSITION_RISK)

    await page.goto('/')
    await page.waitForSelector('[data-testid="vega-AAPL"]')

    await expect(page.getByTestId('vega-AAPL')).toHaveText('320.00')
    await expect(page.getByTestId('vega-GOOGL')).toHaveText('890.00')
    await expect(page.getByTestId('vega-EUR_USD')).toHaveText('\u2014')
  })

  test('renders VaR contribution with percent sign', async ({ page }) => {
    await mockPositionRisk(page, TEST_POSITION_RISK)

    await page.goto('/')
    await page.waitForSelector('[data-testid="var-pct-AAPL"]')

    await expect(page.getByTestId('var-pct-AAPL')).toHaveText('42.50%')
    await expect(page.getByTestId('var-pct-GOOGL')).toHaveText('45.60%')
    await expect(page.getByTestId('var-pct-EUR_USD')).toHaveText('11.90%')
  })

  test('shows Portfolio Delta summary card in compact format', async ({ page }) => {
    await mockPositionRisk(page, TEST_POSITION_RISK)

    await page.goto('/')
    await page.waitForSelector('[data-testid="summary-portfolio-delta"]')

    // totalDelta = 1550.25 + 7125.00 + 0 (null FX) = 8675.25 -> "$8.7K"
    await expect(page.getByTestId('summary-portfolio-delta')).toContainText('$8.7K')
  })

  test('shows Portfolio VaR summary card in compact format', async ({ page }) => {
    await mockPositionRisk(page, TEST_POSITION_RISK)

    await page.goto('/')
    await page.waitForSelector('[data-testid="summary-portfolio-var"]')

    // totalVar = 485.50 + 520.75 + 135.75 = 1142.00 -> "$1.1K"
    await expect(page.getByTestId('summary-portfolio-var')).toContainText('$1.1K')
  })

  test('hides Portfolio Delta/VaR cards when risk data absent', async ({ page }) => {
    // Default mock: no risk data
    await page.goto('/')
    await page.waitForSelector('[data-testid="portfolio-summary"]')

    await expect(page.getByTestId('summary-portfolio-delta')).not.toBeVisible()
    await expect(page.getByTestId('summary-portfolio-var')).not.toBeVisible()

    // Grid should show 3 cards (Positions, Market Value, P&L) not 5
    const cards = page.getByTestId('portfolio-summary').locator('> div')
    await expect(cards).toHaveCount(3)
  })
})
