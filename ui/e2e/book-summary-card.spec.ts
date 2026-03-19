import { test, expect } from '@playwright/test'
import {
  mockAllApiRoutes,
  mockPortfolioSummary,
  TEST_PORTFOLIO_SUMMARY_MULTI_CURRENCY,
} from './fixtures'

test.describe('Book Summary Card', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('displays Total NAV with correct formatting', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="total-nav"]')

    await expect(page.getByTestId('total-nav')).toHaveText('$168,850.00')
  })

  test('displays Unrealized P&L with green color for positive', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="total-unrealized-pnl"]')

    await expect(page.getByTestId('total-unrealized-pnl')).toContainText('$3,050.00')
    await expect(page.getByTestId('total-unrealized-pnl')).toHaveClass(/text-green-600/)
  })

  test('shows base currency selector defaulting to USD', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="base-currency-selector"]')

    const selector = page.getByTestId('base-currency-selector')
    await expect(selector).toHaveValue('USD')

    // Verify available options
    const options = selector.locator('option')
    await expect(options).toHaveCount(4)
    await expect(options.nth(0)).toHaveText('USD')
    await expect(options.nth(1)).toHaveText('EUR')
    await expect(options.nth(2)).toHaveText('GBP')
    await expect(options.nth(3)).toHaveText('JPY')
  })

  test('changing currency re-fetches portfolio summary', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="base-currency-selector"]')

    // Set up mock for EUR-denominated summary before changing currency
    const eurSummary = {
      bookId: 'port-1',
      baseCurrency: 'EUR',
      totalNav: { amount: '156000.00', currency: 'EUR' },
      totalUnrealizedPnl: { amount: '2820.00', currency: 'EUR' },
      currencyBreakdown: [],
    }

    // Intercept the API call with baseCurrency=EUR (firm-level summary)
    const summaryPromise = page.waitForRequest((req) =>
      req.url().includes('/summary') &&
      req.url().includes('baseCurrency=EUR'),
    )

    await mockPortfolioSummary(page, eurSummary)

    // Change currency to EUR
    await page.getByTestId('base-currency-selector').selectOption('EUR')

    await summaryPromise

    // Verify the NAV updates to EUR values
    await expect(page.getByTestId('total-nav')).toHaveText('€156,000.00')
  })

  test('displays currency breakdown table for multi-currency portfolio', async ({ page }) => {
    await mockPortfolioSummary(page, TEST_PORTFOLIO_SUMMARY_MULTI_CURRENCY)

    await page.goto('/')
    await page.waitForSelector('[data-testid="currency-row-USD"]')

    await expect(page.getByTestId('currency-row-USD')).toBeVisible()
    await expect(page.getByTestId('currency-row-EUR')).toBeVisible()

    // USD row values
    const usdRow = page.getByTestId('currency-row-USD')
    await expect(usdRow).toContainText('USD')
    await expect(usdRow).toContainText('$168,850.00')
    await expect(usdRow).toContainText('1.0000')

    // EUR row values
    const eurRow = page.getByTestId('currency-row-EUR')
    await expect(eurRow).toContainText('EUR')
    await expect(eurRow).toContainText('€75,000.00')
    await expect(eurRow).toContainText('$81,150.00')
    await expect(eurRow).toContainText('1.0820')
  })

  test('hides currency breakdown when empty', async ({ page }) => {
    // Default mock has currencyBreakdown: []
    await page.goto('/')
    await page.waitForSelector('[data-testid="book-summary-card"]')

    // Breakdown table should not be visible (only shown when length > 1)
    await expect(page.getByTestId('currency-row-USD')).not.toBeVisible()
  })

  test('does not render card when summary fetch fails', async ({ page }) => {
    // Make summary request fail so summary stays null (override both book and firm endpoints)
    await page.unroute('**/api/v1/books/*/summary*')
    await page.route('**/api/v1/books/*/summary*', (route) => {
      route.fulfill({ status: 500, contentType: 'application/json', body: '{}' })
    })
    await page.unroute('**/api/v1/firm/summary*')
    await page.route('**/api/v1/firm/summary*', (route) => {
      route.fulfill({ status: 500, contentType: 'application/json', body: '{}' })
    })

    await page.goto('/')
    // Wait for positions to load so we know the page is ready
    await page.waitForSelector('[data-testid="position-row-AAPL"]')

    // Portfolio summary card should not be rendered when summary is null
    await expect(page.getByTestId('book-summary-card')).not.toBeVisible()
    await expect(page.getByTestId('total-nav')).not.toBeVisible()
  })

  test('renders "Firm Summary" heading at firm level', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="book-summary-card"]')

    await expect(page.getByTestId('book-summary-card')).toBeVisible()
    await expect(page.getByTestId('book-summary-card')).toContainText('Firm Summary')
  })
})
