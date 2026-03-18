import { test, expect } from '@playwright/test'
import {
  mockAllApiRoutes,
  mockRiskTabRoutes,
  TEST_VAR_RESULT,
  TEST_JOB_DETAIL,
  TEST_RUN_MANIFEST,
  TEST_REPLAY_RESPONSE_MATCH,
  TEST_REPLAY_RESPONSE_MISMATCH,
} from './fixtures'

const JOB_WITH_MANIFEST = {
  items: [
    {
      jobId: 'job-1',
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
      delta: null, gamma: null, vega: null, theta: null, rho: null,
      runLabel: null,
      promotedAt: null,
      promotedBy: null,
      manifestId: 'manifest-abc-123',
    },
  ],
  totalCount: 1,
}

const JOB_WITHOUT_MANIFEST = {
  items: [
    {
      ...JOB_WITH_MANIFEST.items[0],
      manifestId: null,
    },
  ],
  totalCount: 1,
}

async function navigateToRiskTab(page: import('@playwright/test').Page) {
  await page.goto('/')
  await page.getByRole('tab', { name: 'Risk' }).click()
}

test.describe('Run Reproducibility', () => {
  test.describe('Reproducibility button visibility', () => {
    test('shows Reproducibility button for completed jobs with a manifest', async ({ page }) => {
      await mockAllApiRoutes(page)
      await mockRiskTabRoutes(page, {
        varResult: TEST_VAR_RESULT,
        jobHistory: JOB_WITH_MANIFEST,
        jobDetail: TEST_JOB_DETAIL,
        manifestResponse: TEST_RUN_MANIFEST,
      })

      await navigateToRiskTab(page)
      await page.getByTestId('job-row-job-1').click()
      await expect(page.getByTestId('replay-toggle-job-1')).toBeVisible()
    })

    test('does not show Reproducibility button for jobs without a manifest', async ({ page }) => {
      await mockAllApiRoutes(page)
      await mockRiskTabRoutes(page, {
        varResult: TEST_VAR_RESULT,
        jobHistory: JOB_WITHOUT_MANIFEST,
        jobDetail: { ...TEST_JOB_DETAIL, manifestId: null },
      })

      await navigateToRiskTab(page)
      await page.getByTestId('job-row-job-1').click()
      await expect(page.getByTestId('job-detail-panel')).toBeVisible()
      await expect(page.getByTestId('replay-toggle-job-1')).not.toBeVisible()
    })
  })

  test.describe('Manifest display', () => {
    test('shows manifest details when Reproducibility is clicked', async ({ page }) => {
      await mockAllApiRoutes(page)
      await mockRiskTabRoutes(page, {
        varResult: TEST_VAR_RESULT,
        jobHistory: JOB_WITH_MANIFEST,
        jobDetail: TEST_JOB_DETAIL,
        manifestResponse: TEST_RUN_MANIFEST,
      })

      await navigateToRiskTab(page)
      await page.getByTestId('job-row-job-1').click()
      await page.getByTestId('replay-toggle-job-1').click()

      await expect(page.getByTestId('manifest-section')).toBeVisible()
      await expect(page.getByTestId('manifest-model-version')).toContainText('1.4.2-abc9876')
      await expect(page.getByTestId('manifest-status')).toContainText('COMPLETE')
    })

    test('shows original VaR and ES from manifest', async ({ page }) => {
      await mockAllApiRoutes(page)
      await mockRiskTabRoutes(page, {
        varResult: TEST_VAR_RESULT,
        jobHistory: JOB_WITH_MANIFEST,
        jobDetail: TEST_JOB_DETAIL,
        manifestResponse: TEST_RUN_MANIFEST,
      })

      await navigateToRiskTab(page)
      await page.getByTestId('job-row-job-1').click()
      await page.getByTestId('replay-toggle-job-1').click()

      await expect(page.getByTestId('manifest-original-var')).toBeVisible()
      await expect(page.getByTestId('manifest-original-es')).toBeVisible()
    })

    test('shows PARTIAL status with warning when manifest has missing data', async ({ page }) => {
      await mockAllApiRoutes(page)
      await mockRiskTabRoutes(page, {
        varResult: TEST_VAR_RESULT,
        jobHistory: JOB_WITH_MANIFEST,
        jobDetail: TEST_JOB_DETAIL,
        manifestResponse: { ...TEST_RUN_MANIFEST, status: 'PARTIAL' },
      })

      await navigateToRiskTab(page)
      await page.getByTestId('job-row-job-1').click()
      await page.getByTestId('replay-toggle-job-1').click()

      await expect(page.getByTestId('manifest-status')).toContainText('PARTIAL')
      await expect(page.getByTestId('manifest-partial-warning')).toBeVisible()
    })
  })

  test.describe('Replay execution — happy path', () => {
    test('shows replay button and triggers replay with matching digest', async ({ page }) => {
      await mockAllApiRoutes(page)
      await mockRiskTabRoutes(page, {
        varResult: TEST_VAR_RESULT,
        jobHistory: JOB_WITH_MANIFEST,
        jobDetail: TEST_JOB_DETAIL,
        manifestResponse: TEST_RUN_MANIFEST,
        replayResponse: TEST_REPLAY_RESPONSE_MATCH,
      })

      await navigateToRiskTab(page)
      await page.getByTestId('job-row-job-1').click()
      await page.getByTestId('replay-toggle-job-1').click()

      // Click replay
      await page.getByTestId('replay-btn-job-1').click()

      // Wait for result
      await expect(page.getByTestId('replay-result-panel')).toBeVisible()

      // Verify comparison values
      await expect(page.getByTestId('replay-original-var')).toBeVisible()
      await expect(page.getByTestId('replay-replayed-var')).toBeVisible()
      await expect(page.getByTestId('replay-digest-match')).toContainText('MATCHED')
      await expect(page.getByTestId('replay-var-delta')).toContainText('No significant change')
    })
  })

  test.describe('Replay execution — digest mismatch', () => {
    test('shows mismatch warning when replay produces different digest', async ({ page }) => {
      await mockAllApiRoutes(page)
      await mockRiskTabRoutes(page, {
        varResult: TEST_VAR_RESULT,
        jobHistory: JOB_WITH_MANIFEST,
        jobDetail: TEST_JOB_DETAIL,
        manifestResponse: TEST_RUN_MANIFEST,
        replayResponse: TEST_REPLAY_RESPONSE_MISMATCH,
      })

      await navigateToRiskTab(page)
      await page.getByTestId('job-row-job-1').click()
      await page.getByTestId('replay-toggle-job-1').click()
      await page.getByTestId('replay-btn-job-1').click()

      await expect(page.getByTestId('replay-result-panel')).toBeVisible()
      await expect(page.getByTestId('replay-digest-mismatch')).toContainText('MISMATCHED')
      await expect(page.getByTestId('replay-digest-mismatch-warning')).toBeVisible()
      await expect(page.getByTestId('replay-model-mismatch')).toBeVisible()
    })
  })

  test.describe('Error states', () => {
    test('shows error when replay API returns 500', async ({ page }) => {
      await mockAllApiRoutes(page)
      await mockRiskTabRoutes(page, {
        varResult: TEST_VAR_RESULT,
        jobHistory: JOB_WITH_MANIFEST,
        jobDetail: TEST_JOB_DETAIL,
        manifestResponse: TEST_RUN_MANIFEST,
        replayResponse: { error: 'REPLAY_ERROR', message: 'Risk engine unavailable' },
        replayStatus: 500,
      })

      await navigateToRiskTab(page)
      await page.getByTestId('job-row-job-1').click()
      await page.getByTestId('replay-toggle-job-1').click()
      await page.getByTestId('replay-btn-job-1').click()

      await expect(page.getByTestId('replay-error')).toBeVisible()
      await expect(page.getByTestId('replay-error')).toContainText('Risk engine unavailable')
    })

    test('shows blob missing error when market data is unavailable', async ({ page }) => {
      await mockAllApiRoutes(page)
      await mockRiskTabRoutes(page, {
        varResult: TEST_VAR_RESULT,
        jobHistory: JOB_WITH_MANIFEST,
        jobDetail: TEST_JOB_DETAIL,
        manifestResponse: TEST_RUN_MANIFEST,
        replayResponse: { error: 'BLOB_MISSING', message: 'Market data blob abc123 (SPOT_PRICE/AAPL) is no longer available' },
        replayStatus: 422,
      })

      await navigateToRiskTab(page)
      await page.getByTestId('job-row-job-1').click()
      await page.getByTestId('replay-toggle-job-1').click()
      await page.getByTestId('replay-btn-job-1').click()

      await expect(page.getByTestId('replay-error')).toBeVisible()
      await expect(page.getByTestId('replay-error')).toContainText('Market data is no longer available')
    })
  })
})
