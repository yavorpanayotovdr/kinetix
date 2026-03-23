import { test, expect } from '@playwright/test'
import { mockAllApiRoutes } from './fixtures'

test.describe('Maintenance banner — DEGRADED system health', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows blue maintenance banner when system health is DEGRADED and not reconnecting', async ({ page }) => {
    // Override health endpoint to return DEGRADED
    await page.route('**/api/v1/system/health', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'DEGRADED',
          services: {
            gateway: { status: 'READY' },
            'position-service': { status: 'DOWN' },
            'price-service': { status: 'READY' },
            'risk-orchestrator': { status: 'READY' },
            'notification-service': { status: 'READY' },
          },
        }),
      }),
    )

    await page.goto('/')

    const banner = page.getByTestId('maintenance-banner')
    await expect(banner).toBeVisible({ timeout: 5000 })
    await expect(banner).toContainText('Scheduled maintenance in progress')
    await expect(banner).toContainText('Some features may be temporarily limited')
  })

  test('maintenance banner has role="status" for non-urgent announcements', async ({ page }) => {
    await page.route('**/api/v1/system/health', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'DEGRADED',
          services: {
            gateway: { status: 'READY' },
            'position-service': { status: 'DOWN' },
            'price-service': { status: 'READY' },
            'risk-orchestrator': { status: 'READY' },
            'notification-service': { status: 'READY' },
          },
        }),
      }),
    )

    await page.goto('/')

    const banner = page.getByTestId('maintenance-banner')
    await expect(banner).toBeVisible({ timeout: 5000 })
    await expect(banner).toHaveAttribute('role', 'status')
  })

  test('maintenance banner has blue styling', async ({ page }) => {
    await page.route('**/api/v1/system/health', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'DEGRADED',
          services: {
            gateway: { status: 'READY' },
            'position-service': { status: 'DOWN' },
            'price-service': { status: 'READY' },
            'risk-orchestrator': { status: 'READY' },
            'notification-service': { status: 'READY' },
          },
        }),
      }),
    )

    await page.goto('/')

    const banner = page.getByTestId('maintenance-banner')
    await expect(banner).toBeVisible({ timeout: 5000 })
    // Blue banner class is present
    await expect(banner).toHaveClass(/bg-blue/)
  })

  test('maintenance banner does not show when system is UP', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByTestId('maintenance-banner')).not.toBeVisible()
  })
})
