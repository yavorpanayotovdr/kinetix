import { test, expect } from '@playwright/test'
import { mockAllApiRoutes, mockRegimeRoutes, TEST_REGIME_NORMAL, TEST_REGIME_CRISIS } from './fixtures'

test.describe('RegimeIndicator — header badge', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('displays NORMAL regime badge in the header by default', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByTestId('regime-indicator')).toBeVisible()
    await expect(page.getByTestId('regime-indicator')).toContainText('NORMAL')
  })

  test('displays CRISIS regime badge when crisis is active', async ({ page }) => {
    await mockRegimeRoutes(page, TEST_REGIME_CRISIS)

    await page.goto('/')

    await expect(page.getByTestId('regime-indicator')).toBeVisible()
    await expect(page.getByTestId('regime-indicator')).toContainText('CRISIS')
  })

  test('opens detail panel on clicking the badge', async ({ page }) => {
    await mockRegimeRoutes(page, TEST_REGIME_CRISIS)

    await page.goto('/')
    await page.getByTestId('regime-indicator').click()

    await expect(page.getByTestId('regime-detail-panel')).toBeVisible()
  })

  test('detail panel shows effective VaR method', async ({ page }) => {
    await mockRegimeRoutes(page, TEST_REGIME_CRISIS)

    await page.goto('/')
    await page.getByTestId('regime-indicator').click()

    await expect(page.getByTestId('regime-detail-panel')).toContainText('MONTE_CARLO')
  })

  test('detail panel shows confidence percentage', async ({ page }) => {
    await mockRegimeRoutes(page, TEST_REGIME_CRISIS)

    await page.goto('/')
    await page.getByTestId('regime-indicator').click()

    // 0.87 confidence → 87%
    await expect(page.getByTestId('regime-detail-panel')).toContainText('87')
  })

  test('detail panel closes on second click', async ({ page }) => {
    await mockRegimeRoutes(page, TEST_REGIME_CRISIS)

    await page.goto('/')
    await page.getByTestId('regime-indicator').click()
    await expect(page.getByTestId('regime-detail-panel')).toBeVisible()

    await page.getByTestId('regime-indicator').click()
    await expect(page.getByTestId('regime-detail-panel')).not.toBeVisible()
  })

  test('detail panel closes on Escape key', async ({ page }) => {
    await mockRegimeRoutes(page, TEST_REGIME_CRISIS)

    await page.goto('/')
    await page.getByTestId('regime-indicator').click()
    await expect(page.getByTestId('regime-detail-panel')).toBeVisible()

    await page.keyboard.press('Escape')
    await expect(page.getByTestId('regime-detail-panel')).not.toBeVisible()
  })

  test('NORMAL regime detail panel shows PARAMETRIC method', async ({ page }) => {
    await mockRegimeRoutes(page, TEST_REGIME_NORMAL)

    await page.goto('/')
    await page.getByTestId('regime-indicator').click()

    await expect(page.getByTestId('regime-detail-panel')).toContainText('PARAMETRIC')
  })

  test('shows degraded signal warning when degradedInputs is true', async ({ page }) => {
    const degradedRegime = { ...TEST_REGIME_CRISIS, degradedInputs: true }
    await mockRegimeRoutes(page, degradedRegime)

    await page.goto('/')
    await page.getByTestId('regime-indicator').click()

    await expect(page.getByTestId('regime-detail-panel')).toContainText('degraded')
  })
})
