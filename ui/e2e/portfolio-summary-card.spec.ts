import { test, expect } from '@playwright/test'
import { mockAllApiRoutes } from './fixtures'

test.describe('Portfolio Summary Card', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('displays Total NAV with correct formatting', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="total-nav"]')

    await expect(page.getByTestId('total-nav')).toHaveText('$168,850.00')
  })

  test('displays Unrealized P&L with correct formatting', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="total-unrealized-pnl"]')

    await expect(page.getByTestId('total-unrealized-pnl')).toContainText('$3,050.00')
  })

  test('shows base currency selector defaulting to USD', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="base-currency-selector"]')

    await expect(page.getByTestId('base-currency-selector')).toHaveValue('USD')
  })

  test('does not render card when summary fetch fails', async ({ page }) => {
    await page.unroute('**/api/v1/books/*/summary*')
    await page.route('**/api/v1/books/*/summary*', (route) => {
      route.fulfill({ status: 500, contentType: 'application/json', body: '{}' })
    })
    await page.unroute('**/api/v1/firm/summary*')
    await page.route('**/api/v1/firm/summary*', (route) => {
      route.fulfill({ status: 500, contentType: 'application/json', body: '{}' })
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="position-row-AAPL"]')

    await expect(page.getByTestId('book-summary-card')).not.toBeVisible()
  })
})
