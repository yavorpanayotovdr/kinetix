import { test, expect } from '@playwright/test'
import { mockAllApiRoutes, mockPositions, TEST_POSITIONS_MIXED_PNL } from './fixtures'

test.describe('P&L Color Coding', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
    await mockPositions(page, TEST_POSITIONS_MIXED_PNL)
  })

  test('applies green (text-green-600) to positive P&L', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="pnl-AAPL"]')

    const pnlCell = page.getByTestId('pnl-AAPL')
    await expect(pnlCell).toHaveClass(/text-green-600/)
  })

  test('applies red (text-red-600) to negative P&L', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="pnl-TSLA"]')

    const pnlCell = page.getByTestId('pnl-TSLA')
    await expect(pnlCell).toHaveClass(/text-red-600/)
  })

  test('applies gray (text-gray-500) to zero P&L', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="pnl-MSFT"]')

    const pnlCell = page.getByTestId('pnl-MSFT')
    await expect(pnlCell).toHaveClass(/text-gray-500/)
  })

  test('colors total P&L summary card green when aggregate is positive', async ({ page }) => {
    // Use default positions (total P&L = $3050 > 0)
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
    await page.waitForSelector('[data-testid="book-summary"]')

    const pnlCard = page.getByTestId('book-summary').locator('text=Unrealized P&L').locator('..')
    await expect(pnlCard.locator('.text-green-600')).toBeVisible()
  })

  test('colors total P&L summary card red when aggregate is negative', async ({ page }) => {
    // Override with single TSLA position (P&L = -6000 < 0)
    await mockPositions(page, [
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
    ])

    await page.goto('/')
    await page.waitForSelector('[data-testid="book-summary"]')

    const pnlCard = page.getByTestId('book-summary').locator('text=Unrealized P&L').locator('..')
    await expect(pnlCard.locator('.text-red-600')).toBeVisible()
  })
})
