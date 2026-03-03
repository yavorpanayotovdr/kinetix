import { test, expect } from '@playwright/test'
import { mockAllApiRoutes, mockWhatIfAnalysis, TEST_WHATIF_RESPONSE } from './fixtures'

test.describe('What-If Panel', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  // ---------------------------------------------------------------------------
  // Panel open/close
  // ---------------------------------------------------------------------------

  test('opens panel on button click', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()

    await expect(page.getByTestId('whatif-panel')).toBeVisible()
    await expect(page.getByTestId('whatif-backdrop')).toBeVisible()
    await expect(page.getByTestId('whatif-panel')).toHaveAttribute('role', 'dialog')
    await expect(page.getByTestId('whatif-panel')).toHaveAttribute('aria-modal', 'true')
  })

  test('closes on close button', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await expect(page.getByTestId('whatif-panel')).toBeVisible()

    await page.getByTestId('whatif-close').click()

    await expect(page.getByTestId('whatif-panel')).not.toBeVisible()
  })

  test('closes on backdrop click', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await expect(page.getByTestId('whatif-panel')).toBeVisible()

    await page.getByTestId('whatif-backdrop').click()

    await expect(page.getByTestId('whatif-panel')).not.toBeVisible()
  })

  test('closes on Escape key', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await expect(page.getByTestId('whatif-panel')).toBeVisible()

    await page.keyboard.press('Escape')

    await expect(page.getByTestId('whatif-panel')).not.toBeVisible()
  })

  // ---------------------------------------------------------------------------
  // Form defaults and interaction
  // ---------------------------------------------------------------------------

  test('auto-focuses first instrument input', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-instrument-0"]')

    await expect(page.getByTestId('whatif-instrument-0')).toBeFocused()
  })

  test('shows one empty trade form with BUY selected', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-instrument-0"]')

    // Instrument input is empty
    await expect(page.getByTestId('whatif-instrument-0')).toHaveValue('')

    // Asset class defaults to EQUITY
    await expect(page.getByTestId('whatif-asset-class-0')).toHaveValue('EQUITY')

    // BUY is selected
    await expect(page.getByTestId('whatif-side-buy-0')).toHaveAttribute('aria-pressed', 'true')

    // Remove button not visible when only one trade
    await expect(page.getByTestId('whatif-remove-trade-0')).not.toBeVisible()
  })

  test('adds a second trade form', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-instrument-0"]')

    await page.getByTestId('whatif-add-trade').click()

    await expect(page.getByTestId('whatif-instrument-1')).toBeVisible()
    // Remove buttons appear for both trades
    await expect(page.getByTestId('whatif-remove-trade-0')).toBeVisible()
    await expect(page.getByTestId('whatif-remove-trade-1')).toBeVisible()
  })

  test('removes a trade form', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-instrument-0"]')

    // Add a second trade
    await page.getByTestId('whatif-add-trade').click()
    await expect(page.getByTestId('whatif-instrument-1')).toBeVisible()

    // Remove the second trade
    await page.getByTestId('whatif-remove-trade-1').click()

    await expect(page.getByTestId('whatif-instrument-1')).not.toBeVisible()
    // Only first trade remains, remove button hidden again
    await expect(page.getByTestId('whatif-remove-trade-0')).not.toBeVisible()
  })

  test('shows notional preview', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-quantity-0"]')

    await page.getByTestId('whatif-quantity-0').fill('100')
    await page.getByTestId('whatif-price-0').fill('450.00')

    // Notional = 100 * 450 = 45000 -> "45,000"
    await expect(page.getByTestId('whatif-notional-0')).toContainText('45,000')
  })

  // ---------------------------------------------------------------------------
  // Validation and submission
  // ---------------------------------------------------------------------------

  test('shows validation errors for empty fields', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-run"]')

    // Click Run with empty form
    await page.getByTestId('whatif-run').click()

    await expect(page.locator('text=Instrument is required')).toBeVisible()
    await expect(page.locator('text=Quantity must be a positive number')).toBeVisible()
    await expect(page.locator('text=Price must be a non-negative number')).toBeVisible()
  })

  test('direction toggle', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-side-sell-0"]')

    // Initially BUY is pressed
    await expect(page.getByTestId('whatif-side-buy-0')).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByTestId('whatif-side-sell-0')).toHaveAttribute('aria-pressed', 'false')

    // Click SELL
    await page.getByTestId('whatif-side-sell-0').click()

    await expect(page.getByTestId('whatif-side-sell-0')).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByTestId('whatif-side-buy-0')).toHaveAttribute('aria-pressed', 'false')
  })

  test('submits and shows before/after results', async ({ page }) => {
    await mockWhatIfAnalysis(page, TEST_WHATIF_RESPONSE)

    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    // Open panel and fill form
    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-instrument-0"]')

    await page.getByTestId('whatif-instrument-0').fill('SPY')
    await page.getByTestId('whatif-quantity-0').fill('100')
    await page.getByTestId('whatif-price-0').fill('450')

    // Submit
    await page.getByTestId('whatif-run').click()

    // Wait for results
    await page.waitForSelector('[data-testid="whatif-comparison"]')

    // VaR comparison
    await expect(page.getByTestId('whatif-var-base')).toHaveText('1,142.00')
    await expect(page.getByTestId('whatif-var-after')).toHaveText('1,380.50')
    await expect(page.getByTestId('whatif-var-change')).toContainText('238.50')

    // ES comparison
    await expect(page.getByTestId('whatif-es-base')).toHaveText('1,502.90')
    await expect(page.getByTestId('whatif-es-after')).toHaveText('1,820.00')

    // Greeks
    await expect(page.getByTestId('whatif-delta-base')).toBeVisible()
    await expect(page.getByTestId('whatif-theta-base')).toHaveText('-125.50')
    await expect(page.getByTestId('whatif-rho-base')).toHaveText('42.30')

    // Position breakdown
    await expect(page.getByTestId('whatif-position-breakdown')).toBeVisible()
    await expect(page.getByTestId('whatif-position-breakdown')).toContainText('AAPL')
    await expect(page.getByTestId('whatif-position-breakdown')).toContainText('SPY')

    // Reset button visible
    await expect(page.getByTestId('whatif-reset')).toBeVisible()
  })

  // ---------------------------------------------------------------------------
  // Reset and error
  // ---------------------------------------------------------------------------

  test('resets panel to initial state', async ({ page }) => {
    await mockWhatIfAnalysis(page, TEST_WHATIF_RESPONSE)

    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    // Open, fill, submit
    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-instrument-0"]')
    await page.getByTestId('whatif-instrument-0').fill('SPY')
    await page.getByTestId('whatif-quantity-0').fill('100')
    await page.getByTestId('whatif-price-0').fill('450')
    await page.getByTestId('whatif-run').click()
    await page.waitForSelector('[data-testid="whatif-comparison"]')

    // Click reset
    await page.getByTestId('whatif-reset').click()

    // Comparison, breakdown, and reset should be hidden
    await expect(page.getByTestId('whatif-comparison')).not.toBeVisible()
    await expect(page.getByTestId('whatif-position-breakdown')).not.toBeVisible()
    await expect(page.getByTestId('whatif-reset')).not.toBeVisible()

    // Form fields should be cleared
    await expect(page.getByTestId('whatif-instrument-0')).toHaveValue('')
  })

  test('shows error on API failure', async ({ page }) => {
    // Mock what-if to return 500
    await page.route('**/api/v1/risk/what-if/*', (route) => {
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Internal Server Error' }),
      })
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="whatif-open-button"]')

    await page.getByTestId('whatif-open-button').click()
    await page.waitForSelector('[data-testid="whatif-instrument-0"]')

    // Fill valid data
    await page.getByTestId('whatif-instrument-0').fill('SPY')
    await page.getByTestId('whatif-quantity-0').fill('100')
    await page.getByTestId('whatif-price-0').fill('450')

    // Submit
    await page.getByTestId('whatif-run').click()

    // Error should appear
    await expect(page.getByTestId('whatif-error')).toBeVisible()
  })
})
