import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchValuationJobs, fetchValuationJobDetail, fetchChartData } from './jobHistory'

describe('jobHistory API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const jobSummary = {
    jobId: 'job-1',
    bookId: 'book-1',
    triggerType: 'ON_DEMAND',
    status: 'COMPLETED',
    startedAt: '2025-01-15T10:00:00Z',
    completedAt: '2025-01-15T10:00:00.150Z',
    durationMs: 150,
    calculationType: 'PARAMETRIC',
    varValue: 5000.0,
    expectedShortfall: 6250.0,
  }

  const jobDetail = {
    ...jobSummary,
    confidenceLevel: 'CL_95',
    phases: [
      {
        name: 'FETCH_POSITIONS',
        status: 'COMPLETED',
        startedAt: '2025-01-15T10:00:00Z',
        completedAt: '2025-01-15T10:00:00.020Z',
        durationMs: 20,
        details: { positionCount: '5' },
        error: null,
      },
    ],
    error: null,
  }

  describe('fetchValuationJobs', () => {
    it('returns parsed paginated JSON on 200', async () => {
      const paginatedResponse = { items: [jobSummary], totalCount: 1 }
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(paginatedResponse),
      })

      const result = await fetchValuationJobs('book-1')

      expect(result).toEqual(paginatedResponse)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/jobs/book-1?limit=20&offset=0',
      )
    })

    it('passes custom limit and offset', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ items: [], totalCount: 0 }),
      })

      await fetchValuationJobs('book-1', 5, 10)

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/jobs/book-1?limit=5&offset=10',
      )
    })

    it('passes from and to query parameters when provided', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ items: [], totalCount: 0 }),
      })

      await fetchValuationJobs('book-1', 20, 0, '2025-01-15T09:00:00Z', '2025-01-15T11:00:00Z')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/jobs/book-1?limit=20&offset=0&from=2025-01-15T09%3A00%3A00Z&to=2025-01-15T11%3A00%3A00Z',
      )
    })

    it('passes from and to produced by fromDatetimeLocal conversion (custom range flow)', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ items: [], totalCount: 0 }),
      })

      // This mimics what TimeRangeSelector.handleApply does:
      // new Date(datetimeLocalValue).toISOString()
      const from = new Date('2025-01-15T10:00').toISOString()
      const to = new Date('2025-01-15T14:00').toISOString()

      await fetchValuationJobs('book-1', 20, 0, from, to)

      const calledUrl = mockFetch.mock.lastCall?.[0] as string
      expect(calledUrl).toContain('from=')
      expect(calledUrl).toContain('to=')
      expect(calledUrl).toMatch(/from=\d{4}-\d{2}-\d{2}T\d{2}%3A\d{2}%3A\d{2}/)
      expect(calledUrl).toMatch(/to=\d{4}-\d{2}-\d{2}T\d{2}%3A\d{2}%3A\d{2}/)
    })

    it('appends status query parameter when provided', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ items: [], totalCount: 0 }),
      })

      await fetchValuationJobs('book-1', 20, 0, undefined, undefined, 'COMPLETED')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/jobs/book-1?limit=20&offset=0&status=COMPLETED',
      )
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchValuationJobs('book-1')).rejects.toThrow(
        'Failed to fetch valuation jobs: 500 Internal Server Error',
      )
    })
  })

  describe('fetchChartData', () => {
    const chartResponse = {
      points: [{ bucket: '2025-01-15T10:00:00Z', varValue: 5000, expectedShortfall: 6250, confidenceLevel: 'CL_95', delta: null, gamma: null, vega: null, theta: null, rho: null, pvValue: null, jobCount: 1, completedCount: 1, failedCount: 0, runningCount: 0 }],
      bucketSizeMs: 300000,
    }

    it('calls the chart endpoint with from and to params', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(chartResponse),
      })

      const result = await fetchChartData('book-1', '2025-01-15T09:00:00Z', '2025-01-15T11:00:00Z')

      expect(result).toEqual(chartResponse)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/jobs/book-1/chart?from=2025-01-15T09%3A00%3A00Z&to=2025-01-15T11%3A00%3A00Z',
      )
    })

    it('encodes special characters in bookId', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(chartResponse),
      })

      await fetchChartData('book/1', '2025-01-15T09:00:00Z', '2025-01-15T11:00:00Z')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/jobs/book%2F1/chart?from=2025-01-15T09%3A00%3A00Z&to=2025-01-15T11%3A00%3A00Z',
      )
    })

    it('throws on error response', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchChartData('book-1', '2025-01-15T09:00:00Z', '2025-01-15T11:00:00Z')).rejects.toThrow(
        'Failed to fetch chart data: 500 Internal Server Error',
      )
    })
  })

  describe('fetchValuationJobDetail', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(jobDetail),
      })

      const result = await fetchValuationJobDetail('job-1')

      expect(result).toEqual(jobDetail)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/jobs/detail/job-1',
      )
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await fetchValuationJobDetail('unknown')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchValuationJobDetail('job-1')).rejects.toThrow(
        'Failed to fetch job detail: 500 Internal Server Error',
      )
    })
  })
})
