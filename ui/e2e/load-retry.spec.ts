import { test, expect, type Route } from '@playwright/test'
import { mockAllApiRoutes } from './fixtures'

test.describe('Initial load retry button', () => {
  test('shows error card with Retry button when initial load fails', async ({ page }) => {
    await mockAllApiRoutes(page)

    // Override books route to always fail (LIFO: runs before mockAllApiRoutes handler)
    await page.route('**/api/v1/books', (route: Route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Service Unavailable' }),
      }),
    )

    await page.goto('/')

    // Error card should appear with retry button
    const errorCard = page.getByTestId('load-error-card')
    await expect(errorCard).toBeVisible({ timeout: 5000 })
    await expect(errorCard).toContainText('Failed to load positions')
    await expect(errorCard).toContainText('503')

    const retryBtn = page.getByTestId('retry-load-button')
    await expect(retryBtn).toBeVisible()
    await expect(retryBtn).toContainText('Retry')
  })

  test('error card has role="alert" for screen reader accessibility', async ({ page }) => {
    await mockAllApiRoutes(page)

    // Override books route to always fail
    await page.route('**/api/v1/books', (route: Route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Service Unavailable' }),
      }),
    )

    await page.goto('/')

    const errorCard = page.getByTestId('load-error-card')
    await expect(errorCard).toBeVisible({ timeout: 5000 })
    await expect(errorCard).toHaveAttribute('role', 'alert')
  })
})
