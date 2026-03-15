import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import {
  mockAllApiRoutes,
  mockRiskTabRoutes,
  TEST_VAR_RESULT,
  TEST_JOB_DETAIL,
  TEST_PNL_ATTRIBUTION,
} from './fixtures'

// ---------------------------------------------------------------------------
// Fixture helpers
// ---------------------------------------------------------------------------

function makeJobHistory(overrides: Record<string, unknown>[] = []) {
  const defaults = [
    {
      jobId: 'job-sod-1',
      portfolioId: 'port-1',
      triggerType: 'SCHEDULED',
      status: 'COMPLETED',
      startedAt: '2025-01-15T06:00:00Z',
      completedAt: '2025-01-15T06:00:08Z',
      durationMs: 8000,
      calculationType: 'PARAMETRIC',
      confidenceLevel: 'CL_95',
      varValue: 120000.00,
      expectedShortfall: 180000.00,
      pvValue: 4900000.00,
      delta: null,
      gamma: null,
      vega: null,
      theta: null,
      rho: null,
      runLabel: 'SOD',
      promotedAt: null,
      promotedBy: null,
    },
    {
      jobId: 'job-eod-1',
      portfolioId: 'port-1',
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
      jobId: 'job-adhoc-1',
      portfolioId: 'port-1',
      triggerType: 'ON_DEMAND',
      status: 'COMPLETED',
      startedAt: '2025-01-15T10:00:00Z',
      completedAt: '2025-01-15T10:00:05Z',
      durationMs: 5000,
      calculationType: 'PARAMETRIC',
      confidenceLevel: 'CL_95',
      varValue: 125000.00,
      expectedShortfall: 187500.00,
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
  ]
  const items = overrides.length > 0
    ? overrides.map((o, i) => ({ ...defaults[i % defaults.length], ...o }))
    : defaults
  return { items, totalCount: items.length }
}

async function goToRiskTab(page: Page) {
  await page.goto('/')
  await page.getByTestId('tab-risk').click()
}

async function goToPnlTab(page: Page) {
  await page.goto('/')
  await page.getByTestId('tab-pnl').click()
}

// ---------------------------------------------------------------------------
// SOD Badge Display
// ---------------------------------------------------------------------------

test.describe('SOD Badge - Display in Job History', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('SOD badge renders on job row with runLabel SOD', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      varResult: TEST_VAR_RESULT,
      jobHistory: makeJobHistory(),
      jobDetail: TEST_JOB_DETAIL,
    })
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    await expect(page.getByTestId('sod-badge-job-sod-1')).toBeVisible()
    await expect(page.getByTestId('sod-badge-job-sod-1')).toContainText('SOD')
  })

  test('SOD badge does not appear on OFFICIAL_EOD row', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      varResult: TEST_VAR_RESULT,
      jobHistory: makeJobHistory(),
      jobDetail: TEST_JOB_DETAIL,
    })
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    // The EOD row should have the EOD badge but not a SOD badge
    await expect(page.getByTestId('eod-badge-job-eod-1')).toBeVisible()
    await expect(page.getByTestId('sod-badge-job-eod-1')).not.toBeVisible()
  })

  test('SOD badge does not appear on unlabelled row', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      varResult: TEST_VAR_RESULT,
      jobHistory: makeJobHistory(),
      jobDetail: TEST_JOB_DETAIL,
    })
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    await expect(page.getByTestId('sod-badge-job-adhoc-1')).not.toBeVisible()
  })
})

// ---------------------------------------------------------------------------
// SOD Row Border Stripe
// ---------------------------------------------------------------------------

test.describe('SOD Badge - Row Border Stripe', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('SOD row has sky-400 left border stripe', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      varResult: TEST_VAR_RESULT,
      jobHistory: makeJobHistory(),
      jobDetail: TEST_JOB_DETAIL,
    })
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    const sodRow = page.getByTestId('job-row-job-sod-1')
    await expect(sodRow).toHaveClass(/border-l-sky-400/)
  })

  test('OFFICIAL_EOD row retains amber-400 left border stripe', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      varResult: TEST_VAR_RESULT,
      jobHistory: makeJobHistory(),
      jobDetail: TEST_JOB_DETAIL,
    })
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    const eodRow = page.getByTestId('job-row-job-eod-1')
    await expect(eodRow).toHaveClass(/border-l-amber-400/)
    await expect(eodRow).not.toHaveClass(/border-l-sky-400/)
  })

  test('unlabelled row has no coloured left border stripe', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      varResult: TEST_VAR_RESULT,
      jobHistory: makeJobHistory(),
      jobDetail: TEST_JOB_DETAIL,
    })
    await goToRiskTab(page)

    await page.waitForSelector('[data-testid="job-history-table"]')

    const adhocRow = page.getByTestId('job-row-job-adhoc-1')
    await expect(adhocRow).not.toHaveClass(/border-l-sky-400/)
    await expect(adhocRow).not.toHaveClass(/border-l-amber-400/)
  })
})

// ---------------------------------------------------------------------------
// PnlTab - Baseline Provenance
// ---------------------------------------------------------------------------

test.describe('PnlTab - Baseline Provenance Line', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApiRoutes(page)
  })

  test('shows baseline provenance when attribution data and SOD baseline exist', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      sodStatus: {
        exists: true,
        baselineDate: '2025-01-15',
        snapshotType: 'AUTO',
        createdAt: '2025-01-15T06:00:08Z',
        sourceJobId: 'job-sod-1-aaaa-bbbb-cccc',
        calculationType: 'PARAMETRIC',
      },
      pnlAttribution: TEST_PNL_ATTRIBUTION,
    })

    await goToPnlTab(page)

    await expect(page.getByTestId('pnl-baseline-provenance')).toBeVisible()
    await expect(page.getByTestId('pnl-baseline-provenance')).toContainText('Attribution baseline:')
    await expect(page.getByTestId('pnl-baseline-provenance')).toContainText('Auto')
    await expect(page.getByTestId('pnl-baseline-provenance')).toContainText('PARAMETRIC')
  })

  test('provenance line includes Manual label for manual snapshots', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      sodStatus: {
        exists: true,
        baselineDate: '2025-01-15',
        snapshotType: 'MANUAL',
        createdAt: '2025-01-15T08:30:00Z',
        sourceJobId: null,
        calculationType: null,
      },
      pnlAttribution: TEST_PNL_ATTRIBUTION,
    })

    await goToPnlTab(page)

    await expect(page.getByTestId('pnl-baseline-provenance')).toBeVisible()
    await expect(page.getByTestId('pnl-baseline-provenance')).toContainText('Manual')
  })

  test('provenance line is not shown when no SOD baseline exists', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      sodStatus: null,
      pnlAttribution: TEST_PNL_ATTRIBUTION,
    })

    await goToPnlTab(page)

    await expect(page.getByTestId('pnl-baseline-provenance')).not.toBeVisible()
  })

  test('provenance line is not shown when attribution data is absent', async ({ page }) => {
    await mockRiskTabRoutes(page, {
      sodStatus: {
        exists: true,
        baselineDate: '2025-01-15',
        snapshotType: 'AUTO',
        createdAt: '2025-01-15T06:00:08Z',
        sourceJobId: null,
        calculationType: 'PARAMETRIC',
      },
      pnlAttribution: null,
    })

    await goToPnlTab(page)

    await expect(page.getByTestId('pnl-baseline-provenance')).not.toBeVisible()
  })
})
