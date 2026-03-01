import { test, expect } from '@playwright/test'
import { mockAllApiRoutes } from './fixtures'

const STORAGE_KEY = 'kinetix:column-visibility'

test.describe('Column Visibility - Persistence and Layout', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('hidden columns remain hidden after page reload', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="column-settings-button"]')

    // Hide the "Asset Class" column
    await page.getByTestId('column-settings-button').click()
    await page.getByTestId('column-toggle-assetClass').click()

    // Close the dropdown
    await page.locator('header').click()

    // Verify the column is hidden in the table
    const headerCells = page.locator('thead th')
    await expect(headerCells).not.toContainText(['Asset Class'])

    // Verify localStorage was updated
    const stored = await page.evaluate(
      (key) => localStorage.getItem(key),
      STORAGE_KEY,
    )
    expect(stored).toBeTruthy()
    const parsed = JSON.parse(stored!)
    expect(parsed.assetClass).toBe(false)

    // Reload the page
    await page.reload()
    await page.waitForSelector('[data-testid="column-settings-button"]')

    // The "Asset Class" column should still be hidden
    const headerTexts = await page.locator('thead tr').last().locator('th').allTextContents()
    expect(headerTexts).not.toContain('Asset Class')
    // Other columns should still be present
    expect(headerTexts).toContain('Instrument')
    expect(headerTexts).toContain('Quantity')
  })

  test('table layout reflows without horizontal overflow after hiding columns', async ({
    page,
  }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="column-settings-button"]')

    // Hide two columns to trigger a reflow
    await page.getByTestId('column-settings-button').click()
    await page.getByTestId('column-toggle-assetClass').click()
    await page.getByTestId('column-toggle-avgCost').click()

    // Close the dropdown
    await page.locator('header').click()

    // The table's scrollable container should not have horizontal overflow
    const overflowInfo = await page.locator('table').evaluate((table) => {
      const container = table.parentElement!
      return {
        scrollWidth: container.scrollWidth,
        clientWidth: container.clientWidth,
      }
    })
    expect(overflowInfo.scrollWidth).toBeLessThanOrEqual(
      overflowInfo.clientWidth + 1, // +1 for rounding tolerance
    )
  })

  test('settings dropdown shows correct checked/unchecked state after reload', async ({
    page,
  }) => {
    // Pre-set column visibility in localStorage: hide "Quantity" and "Market Value"
    await page.addInitScript(
      (key) => {
        localStorage.setItem(
          key,
          JSON.stringify({ quantity: false, marketValue: false }),
        )
      },
      STORAGE_KEY,
    )

    await page.goto('/')
    await page.waitForSelector('[data-testid="column-settings-button"]')

    // Open settings dropdown
    await page.getByTestId('column-settings-button').click()
    await page.waitForSelector('[data-testid="column-settings-dropdown"]')

    // "Quantity" and "Market Value" should be unchecked
    await expect(page.getByTestId('column-toggle-quantity')).not.toBeChecked()
    await expect(
      page.getByTestId('column-toggle-marketValue'),
    ).not.toBeChecked()

    // Other columns should be checked
    await expect(page.getByTestId('column-toggle-instrument')).toBeChecked()
    await expect(page.getByTestId('column-toggle-assetClass')).toBeChecked()
    await expect(page.getByTestId('column-toggle-avgCost')).toBeChecked()
    await expect(page.getByTestId('column-toggle-marketPrice')).toBeChecked()
    await expect(
      page.getByTestId('column-toggle-unrealizedPnl'),
    ).toBeChecked()
  })
})
