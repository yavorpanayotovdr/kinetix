import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import {
  mockAllApiRoutes,
  mockBrinsonAttributionRoutes,
  TEST_BRINSON_ATTRIBUTION,
} from './fixtures'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function goToPnlTab(page: Page) {
  await page.goto('/')
  await page.getByTestId('tab-pnl').click()
  await page.waitForSelector('[data-testid="benchmark-attribution-section"]')
}

async function submitBenchmarkId(page: Page, benchmarkId: string) {
  await page.getByTestId('benchmark-id-input').fill(benchmarkId)
  await page.getByTestId('benchmark-attribution-submit').click()
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Benchmark Attribution (Brinson-Hood-Beebower)', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('renders the benchmark attribution section on the P&L tab', async ({ page }) => {
    await goToPnlTab(page)

    await expect(page.getByTestId('benchmark-attribution-section')).toBeVisible()
  })

  test('shows benchmark ID input and disabled submit button on initial load', async ({ page }) => {
    await goToPnlTab(page)

    await expect(page.getByTestId('benchmark-id-input')).toBeVisible()
    await expect(page.getByTestId('benchmark-attribution-submit')).toBeDisabled()
  })

  test('enables submit button when benchmark ID is typed', async ({ page }) => {
    await goToPnlTab(page)

    await page.getByTestId('benchmark-id-input').fill('SP500')
    await expect(page.getByTestId('benchmark-attribution-submit')).toBeEnabled()
  })

  test('shows empty prompt before any benchmark is submitted', async ({ page }) => {
    await goToPnlTab(page)

    await expect(page.getByTestId('benchmark-attribution-empty')).toBeVisible()
    await expect(page.getByTestId('benchmark-attribution-empty')).toContainText('Enter a benchmark ID')
  })

  test('empty prompt disappears after benchmark is submitted', async ({ page }) => {
    await mockBrinsonAttributionRoutes(page)

    await goToPnlTab(page)
    await submitBenchmarkId(page, 'SP500')

    await expect(page.getByTestId('benchmark-attribution-empty')).not.toBeVisible()
  })

  test('renders the attribution table after a successful submission', async ({ page }) => {
    await mockBrinsonAttributionRoutes(page)

    await goToPnlTab(page)
    await submitBenchmarkId(page, 'SP500')

    await expect(page.getByTestId('brinson-attribution-table')).toBeVisible()
  })

  test('displays benchmark ID and as-of-date in the table header', async ({ page }) => {
    await mockBrinsonAttributionRoutes(page)

    await goToPnlTab(page)
    await submitBenchmarkId(page, 'SP500')

    const table = page.getByTestId('brinson-attribution-table')
    await expect(table).toContainText('SP500')
    await expect(table).toContainText(TEST_BRINSON_ATTRIBUTION.asOfDate)
  })

  test('renders a row for each sector in the response', async ({ page }) => {
    await mockBrinsonAttributionRoutes(page)

    await goToPnlTab(page)
    await submitBenchmarkId(page, 'SP500')

    await expect(page.getByTestId('brinson-row-AAPL')).toBeVisible()
    await expect(page.getByTestId('brinson-row-GOOGL')).toBeVisible()
  })

  test('displays allocation, selection, and interaction effects as percentages', async ({ page }) => {
    await mockBrinsonAttributionRoutes(page)

    await goToPnlTab(page)
    await submitBenchmarkId(page, 'SP500')

    const aaplRow = page.getByTestId('brinson-row-AAPL')
    // allocationEffect: 0.028 → 2.80%
    await expect(aaplRow).toContainText('2.80%')
    // selectionEffect: 0.014 → 1.40%
    await expect(aaplRow).toContainText('1.40%')
    // interactionEffect: 0.004 → 0.40%
    await expect(aaplRow).toContainText('0.40%')
  })

  test('renders the totals row with total active return', async ({ page }) => {
    await mockBrinsonAttributionRoutes(page)

    await goToPnlTab(page)
    await submitBenchmarkId(page, 'SP500')

    const totalsRow = page.getByTestId('brinson-totals-row')
    await expect(totalsRow).toBeVisible()
    // totalActiveReturn: 0.0476 → 4.76%
    await expect(totalsRow).toContainText('4.76%')
  })

  test('shows error message when the benchmark is not found', async ({ page }) => {
    await mockBrinsonAttributionRoutes(page, {
      errorStatus: 400,
      errorMessage: 'Benchmark not found: UNKNOWN',
    })

    await goToPnlTab(page)
    await submitBenchmarkId(page, 'UNKNOWN')

    await expect(page.getByTestId('benchmark-attribution-error')).toBeVisible()
    await expect(page.getByTestId('benchmark-attribution-error')).toContainText('Failed to fetch attribution')
  })

  test('renders empty state when the response has no sectors', async ({ page }) => {
    await mockBrinsonAttributionRoutes(page, {
      data: { ...TEST_BRINSON_ATTRIBUTION, sectors: [] },
    })

    await goToPnlTab(page)
    await submitBenchmarkId(page, 'SP500')

    await expect(page.getByTestId('brinson-empty')).toBeVisible()
    await expect(page.getByTestId('brinson-empty')).toContainText('No sectors')
  })

  test('re-runs attribution when a new benchmark ID is submitted', async ({ page }) => {
    await mockBrinsonAttributionRoutes(page)

    await goToPnlTab(page)
    await submitBenchmarkId(page, 'SP500')
    await expect(page.getByTestId('brinson-attribution-table')).toContainText('SP500')

    // Override the mock to return a different benchmark ID
    await mockBrinsonAttributionRoutes(page, {
      data: { ...TEST_BRINSON_ATTRIBUTION, benchmarkId: 'FTSE100' },
    })

    await page.getByTestId('benchmark-id-input').fill('FTSE100')
    await page.getByTestId('benchmark-attribution-submit').click()

    await expect(page.getByTestId('brinson-attribution-table')).toContainText('FTSE100')
  })
})
