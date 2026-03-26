import { test, expect, type Page, type Route } from '@playwright/test'
import { mockAllApiRoutes } from './fixtures'

const TEST_KRD_RESPONSE = {
  bookId: 'port-1',
  instruments: [
    {
      instrumentId: 'UST-10Y',
      krdBuckets: [
        { tenorLabel: '2Y', tenorDays: 730, dv01: '25.30' },
        { tenorLabel: '5Y', tenorDays: 1825, dv01: '85.60' },
        { tenorLabel: '10Y', tenorDays: 3650, dv01: '520.40' },
        { tenorLabel: '30Y', tenorDays: 10950, dv01: '45.10' },
      ],
      totalDv01: '676.40',
    },
    {
      instrumentId: 'UST-2Y',
      krdBuckets: [
        { tenorLabel: '2Y', tenorDays: 730, dv01: '190.20' },
        { tenorLabel: '5Y', tenorDays: 1825, dv01: '5.00' },
        { tenorLabel: '10Y', tenorDays: 3650, dv01: '0.00' },
        { tenorLabel: '30Y', tenorDays: 10950, dv01: '0.00' },
      ],
      totalDv01: '195.20',
    },
  ],
  aggregated: [
    { tenorLabel: '2Y', tenorDays: 730, dv01: '215.50' },
    { tenorLabel: '5Y', tenorDays: 1825, dv01: '90.60' },
    { tenorLabel: '10Y', tenorDays: 3650, dv01: '520.40' },
    { tenorLabel: '30Y', tenorDays: 10950, dv01: '45.10' },
  ],
}

async function mockKrdRoute(page: Page, response: object = TEST_KRD_RESPONSE, status = 200): Promise<void> {
  await page.route('**/api/v1/risk/krd/*', (route: Route) => {
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(response) })
  })
}

async function goToDashboard(page: Page): Promise<void> {
  await page.goto('/')
  await page.getByTestId('tab-risk').click()
  await page.waitForSelector('[data-testid="risk-subtab-dashboard"]')
}

test.describe('Key Rate Duration panel', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows empty state when no fixed-income positions', async ({ page }) => {
    await mockKrdRoute(page, { bookId: 'port-1', instruments: [], aggregated: [] })
    await goToDashboard(page)

    await expect(page.getByTestId('krd-empty')).toBeVisible()
    await expect(page.getByTestId('krd-empty')).toContainText('No fixed-income positions')
  })

  test('renders all four tenor buckets with DV01 values', async ({ page }) => {
    await mockKrdRoute(page)
    await goToDashboard(page)

    const panel = page.getByTestId('krd-panel')
    await expect(panel).toBeVisible()

    await expect(page.getByTestId('krd-bucket-2Y')).toBeVisible()
    await expect(page.getByTestId('krd-bucket-5Y')).toBeVisible()
    await expect(page.getByTestId('krd-bucket-10Y')).toBeVisible()
    await expect(page.getByTestId('krd-bucket-30Y')).toBeVisible()
  })

  test('displays total DV01', async ({ page }) => {
    await mockKrdRoute(page)
    await goToDashboard(page)

    await expect(page.getByTestId('krd-total-dv01')).toBeVisible()
  })

  test('renders SVG bars for visualization', async ({ page }) => {
    await mockKrdRoute(page)
    await goToDashboard(page)

    const buckets = page.getByTestId('krd-buckets')
    await expect(buckets.locator('svg').first()).toBeVisible()
  })

  test('expand toggle shows instrument-level detail', async ({ page }) => {
    await mockKrdRoute(page)
    await goToDashboard(page)

    await expect(page.getByTestId('krd-instrument-detail')).not.toBeVisible()

    await page.getByTestId('krd-expand-toggle').click()
    await expect(page.getByTestId('krd-instrument-detail')).toBeVisible()
    await expect(page.getByText('UST-10Y')).toBeVisible()
    await expect(page.getByText('UST-2Y')).toBeVisible()
  })
})
