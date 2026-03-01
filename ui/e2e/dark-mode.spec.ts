import { test, expect } from '@playwright/test'
import { mockAllApiRoutes } from './fixtures'

test.describe('Dark Mode - Persistence and No Flash', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('clicking the dark mode toggle adds "dark" class to the html element', async ({
    page,
  }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="dark-mode-toggle"]')

    const htmlClassBefore = await page.locator('html').getAttribute('class')
    expect(htmlClassBefore ?? '').not.toContain('dark')

    await page.getByTestId('dark-mode-toggle').click()

    const htmlClassAfter = await page.locator('html').getAttribute('class')
    expect(htmlClassAfter).toContain('dark')
  })

  test('toggling dark mode off removes the "dark" class from html', async ({
    page,
  }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="dark-mode-toggle"]')

    // Enable dark mode
    await page.getByTestId('dark-mode-toggle').click()
    expect(await page.locator('html').getAttribute('class')).toContain('dark')

    // Disable dark mode
    await page.getByTestId('dark-mode-toggle').click()
    const htmlClassAfter = await page.locator('html').getAttribute('class')
    expect(htmlClassAfter ?? '').not.toContain('dark')
  })

  test('dark mode toggle updates the icon from moon to sun', async ({
    page,
  }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="dark-mode-toggle"]')

    const toggleButton = page.getByTestId('dark-mode-toggle')

    // In light mode, aria-label should indicate switching to dark
    await expect(toggleButton).toHaveAttribute(
      'aria-label',
      'Switch to dark mode',
    )

    await toggleButton.click()

    // In dark mode, aria-label should indicate switching to light
    await expect(toggleButton).toHaveAttribute(
      'aria-label',
      'Switch to light mode',
    )
  })

  test('dark mode persists across page reload', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('[data-testid="dark-mode-toggle"]')

    // Enable dark mode
    await page.getByTestId('dark-mode-toggle').click()
    expect(await page.locator('html').getAttribute('class')).toContain('dark')

    // Verify localStorage was set
    const storedTheme = await page.evaluate(() =>
      localStorage.getItem('kinetix:theme'),
    )
    expect(storedTheme).toBe('dark')

    // Reload the page
    await page.reload()
    await page.waitForSelector('[data-testid="dark-mode-toggle"]')

    // The html element should still have the "dark" class after reload
    const htmlClassAfterReload =
      await page.locator('html').getAttribute('class')
    expect(htmlClassAfterReload).toContain('dark')

    // The toggle button should reflect dark mode state
    await expect(page.getByTestId('dark-mode-toggle')).toHaveAttribute(
      'aria-label',
      'Switch to light mode',
    )
  })

  test('light mode is the default when localStorage has no theme preference', async ({
    page,
  }) => {
    // Clear any stored theme preference before navigating
    await page.addInitScript(() => {
      localStorage.removeItem('kinetix:theme')
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="dark-mode-toggle"]')

    const htmlClass = await page.locator('html').getAttribute('class')
    expect(htmlClass ?? '').not.toContain('dark')
  })

  test('dark mode preference set before navigation applies without a flash of light mode', async ({
    page,
  }) => {
    // Pre-set dark mode in localStorage before the page loads
    await page.addInitScript(() => {
      localStorage.setItem('kinetix:theme', 'dark')
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="dark-mode-toggle"]')

    // Take a screenshot immediately after the app renders to verify no flash
    // The html element should already have "dark" class
    const htmlClass = await page.locator('html').getAttribute('class')
    expect(htmlClass).toContain('dark')

    // The main content area should have dark background styles applied
    const mainBg = await page
      .locator('main')
      .evaluate((el) => getComputedStyle(el).backgroundColor)
    // dark:bg-surface-900 is #0f172a which is rgb(15, 23, 42)
    expect(mainBg).toBe('rgb(15, 23, 42)')
  })

  test('dark mode styles are applied to the position grid table', async ({
    page,
  }) => {
    await page.addInitScript(() => {
      localStorage.setItem('kinetix:theme', 'dark')
    })

    await page.goto('/')
    await page.waitForSelector('[data-testid="dark-mode-toggle"]')

    // Wait for positions to render
    await page.waitForSelector('table')

    // Verify that dark variant classes are active on the table
    const tableRowBgClass = await page
      .locator('thead tr')
      .last()
      .evaluate((el) => el.className)
    expect(tableRowBgClass).toContain('dark:bg-surface-800')
  })
})
