import { test, expect } from '@playwright/test'
import {
  mockAllApiRoutes,
  mockLiquidityRiskRoutes,
  TEST_LIQUIDITY_RISK_RESULT,
  TEST_LIQUIDITY_RISK_RESULT_ADV_MISSING,
} from './fixtures'

async function goToRiskTab(page: import('@playwright/test').Page) {
  await page.goto('/')
  await page.getByTestId('tab-risk').click()
}

// ---------------------------------------------------------------------------
// Liquidity Risk Panel — data rendering
// ---------------------------------------------------------------------------

test.describe('Liquidity Risk Panel — data rendering', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('displays portfolio LVaR when latest snapshot is available', async ({ page }) => {
    await mockLiquidityRiskRoutes(page, { latest: TEST_LIQUIDITY_RISK_RESULT })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="portfolio-lvar"]')

    await expect(page.getByTestId('portfolio-lvar')).toContainText('316')
  })

  test('displays data completeness percentage', async ({ page }) => {
    await mockLiquidityRiskRoutes(page, { latest: TEST_LIQUIDITY_RISK_RESULT })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="data-completeness"]')

    await expect(page.getByTestId('data-completeness')).toContainText('85')
  })

  test('displays concentration status badge', async ({ page }) => {
    await mockLiquidityRiskRoutes(page, { latest: TEST_LIQUIDITY_RISK_RESULT })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="concentration-status"]')

    await expect(page.getByTestId('concentration-status')).toContainText('OK')
  })

  test('renders a row for each position in the liquidity risk result', async ({ page }) => {
    await mockLiquidityRiskRoutes(page, { latest: TEST_LIQUIDITY_RISK_RESULT })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="position-row-AAPL"]')

    await expect(page.getByTestId('position-row-AAPL')).toBeVisible()
    await expect(page.getByTestId('position-row-GOOGL')).toBeVisible()
  })
})

// ---------------------------------------------------------------------------
// Liquidity Risk Panel — empty state
// ---------------------------------------------------------------------------

test.describe('Liquidity Risk Panel — empty state', () => {
  test('shows empty state when no liquidity snapshot exists', async ({ page }) => {
    await mockAllApiRoutes(page)
    await mockLiquidityRiskRoutes(page, { latest: null })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="liquidity-empty"]')

    await expect(page.getByTestId('liquidity-empty')).toBeVisible()
  })
})

// ---------------------------------------------------------------------------
// Liquidity Risk Panel — refresh / trigger calculation
// ---------------------------------------------------------------------------

test.describe('Liquidity Risk Panel — refresh interaction', () => {
  test('clicking refresh button triggers calculation and renders result', async ({ page }) => {
    await mockAllApiRoutes(page)
    // Start with no snapshot — empty state shown
    await mockLiquidityRiskRoutes(page, {
      latest: null,
      calculated: TEST_LIQUIDITY_RISK_RESULT,
    })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="liquidity-empty"]')

    // Click "Calculate now"
    await page.getByTestId('liquidity-refresh').click()

    // Panel should now show the result
    await page.waitForSelector('[data-testid="portfolio-lvar"]')
    await expect(page.getByTestId('portfolio-lvar')).toContainText('316')
  })

  test('clicking refresh when result already shown triggers re-calculation', async ({ page }) => {
    await mockAllApiRoutes(page)
    await mockLiquidityRiskRoutes(page, {
      latest: TEST_LIQUIDITY_RISK_RESULT,
      calculated: {
        ...TEST_LIQUIDITY_RISK_RESULT,
        portfolioLvar: 400000,
        calculatedAt: '2026-03-24T11:00:00Z',
      },
    })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="portfolio-lvar"]')

    // Initial load shows 316K
    await expect(page.getByTestId('portfolio-lvar')).toContainText('316')

    // Click the header refresh button
    await page.getByTestId('liquidity-refresh').click()

    // Result should update to the POST response
    await expect(page.getByTestId('portfolio-lvar')).toContainText('400')
  })
})

// ---------------------------------------------------------------------------
// Liquidity Risk Panel — ADV missing warning
// ---------------------------------------------------------------------------

test.describe('Liquidity Risk Panel — ADV warnings', () => {
  test('shows ADV missing warning for positions without ADV data', async ({ page }) => {
    await mockAllApiRoutes(page)
    await mockLiquidityRiskRoutes(page, { latest: TEST_LIQUIDITY_RISK_RESULT_ADV_MISSING })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="adv-missing-warning"]')

    await expect(page.getByTestId('adv-missing-warning')).toBeVisible()
  })
})
