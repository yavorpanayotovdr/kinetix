import { test, expect } from '@playwright/test'
import { mockAllApiRoutes, mockPositions, TEST_POSITIONS_MIXED_PNL } from './fixtures'

test.describe('Empty State and Summary Cards', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows "No positions to display" when positions array is empty', async ({ page }) => {
    await mockPositions(page, [])

    await page.goto('/')
    await page.waitForSelector('text=No positions to display.')

    await expect(page.locator('text=No positions to display.')).toBeVisible()
  })

  test('does not show pagination controls in empty state', async ({ page }) => {
    await mockPositions(page, [])

    await page.goto('/')
    await page.waitForSelector('text=No positions to display.')

    await expect(page.getByTestId('pagination-controls')).not.toBeVisible()
  })

  test('does not show column settings or CSV export in empty state', async ({ page }) => {
    await mockPositions(page, [])

    await page.goto('/')
    await page.waitForSelector('text=No positions to display.')

    await expect(page.getByTestId('column-settings-button')).not.toBeVisible()
    await expect(page.getByTestId('csv-export-button')).not.toBeVisible()
  })

  test('summary card positions count matches actual number', async ({ page }) => {
    await mockPositions(page, [
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
    ])

    await page.goto('/')
    await page.waitForSelector('[data-testid="position-row-AAPL"]')

    const summary = page.getByTestId('portfolio-summary')
    await expect(summary.locator('text=Positions').locator('..')).toContainText('1')
  })

  test('summary card market value is the sum across all positions', async ({ page }) => {
    // Default 3 positions: 15500 + 142500 + 10850 = 168850
    await page.goto('/')
    await page.waitForSelector('[data-testid="portfolio-summary"]')

    const summary = page.getByTestId('portfolio-summary')
    await expect(summary).toContainText('$168.8K')
  })

  test('summary card P&L total reflects mixed positive/negative', async ({ page }) => {
    // AAPL: +500, TSLA: -6000, MSFT: 0 => total = -5500
    await mockPositions(page, TEST_POSITIONS_MIXED_PNL)

    await page.goto('/')
    await page.waitForSelector('[data-testid="portfolio-summary"]')

    const summary = page.getByTestId('portfolio-summary')
    await expect(summary).toContainText('-$5.5K')

    const pnlCard = summary.locator('text=Unrealized P&L').locator('..')
    await expect(pnlCard.locator('.text-red-600')).toBeVisible()
  })
})
