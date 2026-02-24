import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchCalculationJobs, fetchCalculationJobDetail } from './jobHistory'

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
    portfolioId: 'port-1',
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
    steps: [
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

  describe('fetchCalculationJobs', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([jobSummary]),
      })

      const result = await fetchCalculationJobs('port-1')

      expect(result).toEqual([jobSummary])
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/jobs/port-1?limit=20&offset=0',
      )
    })

    it('passes custom limit and offset', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      })

      await fetchCalculationJobs('port-1', 5, 10)

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/jobs/port-1?limit=5&offset=10',
      )
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchCalculationJobs('port-1')).rejects.toThrow(
        'Failed to fetch calculation jobs: 500 Internal Server Error',
      )
    })
  })

  describe('fetchCalculationJobDetail', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(jobDetail),
      })

      const result = await fetchCalculationJobDetail('job-1')

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

      const result = await fetchCalculationJobDetail('unknown')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchCalculationJobDetail('job-1')).rejects.toThrow(
        'Failed to fetch job detail: 500 Internal Server Error',
      )
    })
  })
})
