import { test, expect } from '@playwright/test'
import { mockAllApiRoutes, mockManyPositions } from './fixtures'

test.describe('Position Grid Pagination', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
    await mockManyPositions(page, 120)
  })

  test('displays 50 rows per page with correct page count for 120 positions', async ({
    page,
  }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="pagination-controls"]')

    // Should show "Page 1 of 3" (120 positions / 50 per page = 3 pages)
    await expect(page.getByTestId('pagination-info')).toHaveText('Page 1 of 3')

    // First page should have 50 rows
    const rows = page.locator('tbody tr')
    await expect(rows).toHaveCount(50)
  })

  test('navigating to page 2 shows the next 50 rows', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="pagination-controls"]')

    // Click Next to go to page 2
    await page.getByTestId('pagination-next').click()
    await expect(page.getByTestId('pagination-info')).toHaveText('Page 2 of 3')

    const rows = page.locator('tbody tr')
    await expect(rows).toHaveCount(50)
  })

  test('last page shows remaining rows (20 of 120)', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="pagination-controls"]')

    // Navigate to page 3
    await page.getByTestId('pagination-next').click()
    await page.getByTestId('pagination-next').click()
    await expect(page.getByTestId('pagination-info')).toHaveText('Page 3 of 3')

    const rows = page.locator('tbody tr')
    await expect(rows).toHaveCount(20)
  })

  test('Previous button is disabled on page 1, Next button is disabled on last page', async ({
    page,
  }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="pagination-controls"]')

    // On page 1, Previous should be disabled
    await expect(page.getByTestId('pagination-prev')).toBeDisabled()
    await expect(page.getByTestId('pagination-next')).toBeEnabled()

    // Go to last page
    await page.getByTestId('pagination-next').click()
    await page.getByTestId('pagination-next').click()

    // On page 3 (last), Next should be disabled
    await expect(page.getByTestId('pagination-next')).toBeDisabled()
    await expect(page.getByTestId('pagination-prev')).toBeEnabled()
  })

  test('Previous button becomes enabled after navigating to page 2', async ({
    page,
  }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="pagination-controls"]')

    await page.getByTestId('pagination-next').click()
    await expect(page.getByTestId('pagination-info')).toHaveText('Page 2 of 3')

    // Both buttons should be enabled on a middle page
    await expect(page.getByTestId('pagination-prev')).toBeEnabled()
    await expect(page.getByTestId('pagination-next')).toBeEnabled()

    // Go back to page 1
    await page.getByTestId('pagination-prev').click()
    await expect(page.getByTestId('pagination-info')).toHaveText('Page 1 of 3')
    await expect(page.getByTestId('pagination-prev')).toBeDisabled()
  })

  test('pagination is not shown when positions fit on a single page', async ({
    page,
  }) => {
    // Override with only 10 positions (below 50-row threshold)
    await mockManyPositions(page, 10)

    await page.goto('/')
    await page.waitForSelector('tbody tr')

    // Pagination controls should not exist
    await expect(page.getByTestId('pagination-controls')).not.toBeVisible()
  })
})
