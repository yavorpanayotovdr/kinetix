import { test, expect } from '@playwright/test'
import type { Page, Route } from '@playwright/test'
import {
  mockAllApiRoutes,
  mockRiskTabRoutes,
  TEST_VAR_RESULT,
  TEST_JOB_DETAIL,
} from './fixtures'

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

function makeJobHistory(overrides: Record<string, unknown>[] = []) {
  const defaults = [
    {
      jobId: 'job-completed-1',
      bookId: 'port-1',
      triggerType: 'ON_DEMAND',
      status: 'COMPLETED',
      startedAt: '2025-01-15T12:00:00Z',
      completedAt: '2025-01-15T12:00:05Z',
      durationMs: 5000,
      calculationType: 'PARAMETRIC',
      confidenceLevel: 'CL_95',
      varValue: 125000.50,
      expectedShortfall: 187500.75,
      pvValue: 5000000.00,
      delta: null,
      gamma: null,
      vega: null,
      theta: null,
      rho: null,
      runLabel: null,
      promotedAt: null,
      promotedBy: null,
    },
    {
      jobId: 'job-eod-1',
      bookId: 'port-1',
      triggerType: 'SCHEDULED',
      status: 'COMPLETED',
      startedAt: '2025-01-15T18:00:00Z',
      completedAt: '2025-01-15T18:00:10Z',
      durationMs: 10000,
      calculationType: 'PARAMETRIC',
      confidenceLevel: 'CL_95',
      varValue: 130000.00,
      expectedShortfall: 195000.00,
      pvValue: 5100000.00,
      delta: null,
      gamma: null,
      vega: null,
      theta: null,
      rho: null,
      runLabel: 'OFFICIAL_EOD',
      promotedAt: '2025-01-15T18:30:00Z',
      promotedBy: 'risk-manager',
    },
    {
      jobId: 'job-failed-1',
      bookId: 'port-1',
      triggerType: 'ON_DEMAND',
      status: 'FAILED',
      startedAt: '2025-01-15T09:00:00Z',
      completedAt: '2025-01-15T09:00:03Z',
      durationMs: 3000,
      calculationType: 'PARAMETRIC',
      confidenceLevel: 'CL_95',
      varValue: null,
      expectedShortfall: null,
      pvValue: null,
      delta: null,
      gamma: null,
      vega: null,
      theta: null,
      rho: null,
      runLabel: null,
      promotedAt: null,
      promotedBy: null,
    },
  ]
  const items = overrides.length > 0
    ? overrides.map((o, i) => ({ ...defaults[i % defaults.length], ...o }))
    : defaults
  return { items, totalCount: items.length }
}

function makeJobDetail(overrides: Record<string, unknown> = {}) {
  return {
    ...TEST_JOB_DETAIL,
    ...overrides,
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function goToRiskTab(page: Page) {
  await page.goto('/')
  await page.getByTestId('tab-risk').click()
}

async function setupEodRoutes(page: Page, jobHistory: object, jobDetail?: object) {
  await mockRiskTabRoutes(page, {
    varResult: TEST_VAR_RESULT,
    jobHistory,
    jobDetail: jobDetail ?? makeJobDetail(),
  })
}

// ---------------------------------------------------------------------------
// EOD Badge Display
// ---------------------------------------------------------------------------

test.describe('EOD Promotion - Badge Display', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows EOD badge on promoted jobs in job history table', async ({ page }) => {
    await setupEodRoutes(page, makeJobHistory())
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    // The promoted job should show an EOD badge
    await expect(page.getByTestId('eod-badge-job-eod-1')).toBeVisible()

    // The non-promoted completed job should NOT show an EOD badge
    await expect(page.getByTestId('eod-badge-job-completed-1')).not.toBeVisible()

    // The failed job should NOT show an EOD badge
    await expect(page.getByTestId('eod-badge-job-failed-1')).not.toBeVisible()
  })

  test('promoted job row has amber left border', async ({ page }) => {
    await setupEodRoutes(page, makeJobHistory())
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    const eodRow = page.getByTestId('job-row-job-eod-1')
    await expect(eodRow).toHaveClass(/border-l-amber-400/)

    const normalRow = page.getByTestId('job-row-job-completed-1')
    await expect(normalRow).not.toHaveClass(/border-l-amber-400/)
  })
})

// ---------------------------------------------------------------------------
// EOD Filter
// ---------------------------------------------------------------------------

test.describe('EOD Promotion - Filter', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('EOD filter chip filters to only Official EOD runs', async ({ page }) => {
    await setupEodRoutes(page, makeJobHistory())
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    // Initially all 3 rows visible
    const rows = page.locator('[data-testid^="job-row-"]')
    await expect(rows).toHaveCount(3)

    // Click EOD filter chip
    await page.getByTestId('eod-filter-chip').click()

    // Only the promoted job should be visible
    await expect(rows).toHaveCount(1)
    await expect(page.getByTestId('job-row-job-eod-1')).toBeVisible()

    // Click again to deactivate
    await page.getByTestId('eod-filter-chip').click()
    await expect(rows).toHaveCount(3)
  })
})

// ---------------------------------------------------------------------------
// Promote Button
// ---------------------------------------------------------------------------

test.describe('EOD Promotion - Promote Button', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows promote button only for completed, non-promoted jobs in detail panel', async ({ page }) => {
    const detail = makeJobDetail({ jobId: 'job-completed-1', runLabel: null })
    await setupEodRoutes(page, makeJobHistory(), detail)
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    // Expand the non-promoted completed job
    await page.getByTestId('job-row-job-completed-1').click()
    await page.waitForSelector('[data-testid="job-detail-panel"]')

    // Promote button should be visible
    await expect(page.getByTestId('promote-eod-job-completed-1')).toBeVisible()
  })

  test('does not show promote button for already promoted jobs', async ({ page }) => {
    const detail = makeJobDetail({
      jobId: 'job-eod-1',
      runLabel: 'OFFICIAL_EOD',
      promotedAt: '2025-01-15T18:30:00Z',
      promotedBy: 'risk-manager',
    })
    await setupEodRoutes(page, makeJobHistory(), detail)
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    // Expand the promoted job
    await page.getByTestId('job-row-job-eod-1').click()
    await page.waitForSelector('[data-testid="job-detail-panel"]')

    // Promote button should NOT be visible
    await expect(page.getByTestId('promote-eod-job-eod-1')).not.toBeVisible()

    // Should show EOD info instead
    await expect(page.getByTestId('eod-info-job-eod-1')).toBeVisible()
    await expect(page.getByTestId('eod-info-job-eod-1')).toContainText('risk-manager')
  })

  test('promote button triggers confirmation dialog and calls API on confirm', async ({ page }) => {
    const detail = makeJobDetail({ jobId: 'job-completed-1', runLabel: null })
    await setupEodRoutes(page, makeJobHistory(), detail)

    // Mock the PATCH endpoint for promotion
    await page.route('**/api/v1/risk/jobs/*/label', (route: Route) => {
      if (route.request().method() === 'PATCH') {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            jobId: 'job-completed-1',
            bookId: 'port-1',
            valuationDate: '2025-01-15',
            runLabel: 'OFFICIAL_EOD',
            promotedAt: '2025-01-15T19:00:00Z',
            promotedBy: 'current-user',
          }),
        })
      } else {
        route.fallback()
      }
    })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="job-history-table"]')

    // Expand job and click promote
    await page.getByTestId('job-row-job-completed-1').click()
    await page.waitForSelector('[data-testid="promote-eod-job-completed-1"]')
    await page.getByTestId('promote-eod-job-completed-1').click()

    // Confirmation dialog should appear
    await expect(page.getByTestId('confirm-dialog')).toBeVisible()
    await expect(page.getByText('Are you sure you want to designate this run')).toBeVisible()

    // Click the confirm button
    await page.getByTestId('confirm-dialog-confirm').click()

    // Dialog should close after successful promotion
    await expect(page.getByText('Are you sure you want to designate this run')).not.toBeVisible()
  })

  test('shows error in confirmation dialog on 409 conflict', async ({ page }) => {
    const detail = makeJobDetail({ jobId: 'job-completed-1', runLabel: null })
    await setupEodRoutes(page, makeJobHistory(), detail)

    // Mock the PATCH endpoint to return 409
    await page.route('**/api/v1/risk/jobs/*/label', (route: Route) => {
      if (route.request().method() === 'PATCH') {
        route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            error: 'Another Official EOD already exists for this portfolio/date',
          }),
        })
      } else {
        route.fallback()
      }
    })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="job-history-table"]')

    // Expand job, click promote, confirm
    await page.getByTestId('job-row-job-completed-1').click()
    await page.waitForSelector('[data-testid="promote-eod-job-completed-1"]')
    await page.getByTestId('promote-eod-job-completed-1').click()
    await page.getByTestId('confirm-dialog-confirm').click()

    // Error should be displayed in the dialog
    await expect(page.getByTestId('promote-error')).toBeVisible()
    await expect(page.getByTestId('promote-error')).toContainText('Another Official EOD already exists')
  })
})

// ---------------------------------------------------------------------------
// Demotion Workflow
// ---------------------------------------------------------------------------

test.describe('EOD Promotion - Demotion', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows Remove designation link for promoted jobs and opens danger dialog', async ({ page }) => {
    const detail = makeJobDetail({
      jobId: 'job-eod-1',
      runLabel: 'OFFICIAL_EOD',
      promotedAt: '2025-01-15T18:30:00Z',
      promotedBy: 'risk-manager',
    })
    await setupEodRoutes(page, makeJobHistory(), detail)

    // Mock the PATCH endpoint for demotion
    await page.route('**/api/v1/risk/jobs/*/label', (route: Route) => {
      if (route.request().method() === 'PATCH') {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            jobId: 'job-eod-1',
            bookId: 'port-1',
            valuationDate: '2025-01-15',
            runLabel: 'ADHOC',
            promotedAt: null,
            promotedBy: null,
          }),
        })
      } else {
        route.fallback()
      }
    })

    await goToRiskTab(page)
    await page.waitForSelector('[data-testid="job-history-table"]')

    // Expand the promoted job
    await page.getByTestId('job-row-job-eod-1').click()
    await page.waitForSelector('[data-testid="job-detail-panel"]')

    // Remove designation link should be visible
    await expect(page.getByTestId('demote-eod-job-eod-1')).toBeVisible()

    // Click it to open the danger dialog
    await page.getByTestId('demote-eod-job-eod-1').click()

    // Danger confirmation dialog should appear
    await expect(page.getByTestId('confirm-dialog')).toBeVisible()
    await expect(page.getByText('Remove Official EOD Designation')).toBeVisible()
    await expect(page.getByText('remove the Official EOD designation')).toBeVisible()

    // Confirm demotion
    await page.getByTestId('confirm-dialog-confirm').click()

    // Dialog should close
    await expect(page.getByText('remove the Official EOD designation')).not.toBeVisible()
  })
})

// ---------------------------------------------------------------------------
// Auto-Select Notice in Run Comparison
// ---------------------------------------------------------------------------

test.describe('EOD Promotion - Auto-Select Notice', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows auto-select notice when Official EOD is used in comparison', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      varResult: TEST_VAR_RESULT,
      jobHistory: makeJobHistory(),
    })

    // Mock the day-over-day comparison endpoint to return Official EOD labels
    await page.route('**/api/v1/risk/compare/*/day-over-day*', (route: Route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          comparisonId: 'cmp-1',
          comparisonType: 'DAILY_VAR',
          bookId: 'port-1',
          baseRun: {
            jobId: 'job-eod-base',
            label: 'Official EOD 2025-01-14',
            valuationDate: '2025-01-14',
            calcType: 'PARAMETRIC',
            confLevel: 'CL_95',
            varValue: '125000',
            es: '187500',
            pv: '5000000',
            delta: '0.5',
            gamma: '0.01',
            vega: '100',
            theta: '-50',
            rho: '25',
            componentBreakdown: [],
            positionRisk: [],
            modelVersion: null,
            parameters: {},
            calculatedAt: '2025-01-14T18:00:00Z',
          },
          targetRun: {
            jobId: 'job-target',
            label: '2025-01-15',
            valuationDate: '2025-01-15',
            calcType: 'PARAMETRIC',
            confLevel: 'CL_95',
            varValue: '130000',
            es: '195000',
            pv: '5100000',
            delta: '0.52',
            gamma: '0.011',
            vega: '105',
            theta: '-52',
            rho: '26',
            componentBreakdown: [],
            positionRisk: [],
            modelVersion: null,
            parameters: {},
            calculatedAt: '2025-01-15T12:00:00Z',
          },
          bookDiff: {
            varChange: '5000',
            varChangePercent: '4',
            esChange: '7500',
            esChangePercent: '4',
            pvChange: '100000',
            deltaChange: '0.02',
            gammaChange: '0.001',
            vegaChange: '5',
            thetaChange: '-2',
            rhoChange: '1',
          },
          componentDiffs: [],
          positionDiffs: [],
          parameterDiffs: [],
          attribution: null,
        }),
      })
    })

    await goToRiskTab(page)

    // Navigate to Run Comparison sub-tab
    await page.getByTestId('risk-subtab-run-compare').click()

    // Click Compare with default dates
    await page.getByTestId('compare-dates-btn').click()

    // Wait for comparison results
    await page.waitForSelector('[data-testid="run-comparison-panel"]')

    // Auto-select notice should be visible
    await expect(page.getByTestId('eod-auto-select-notice')).toBeVisible()
    await expect(page.getByTestId('eod-auto-select-notice')).toContainText('Official EOD auto-selected for base date')
  })
})
