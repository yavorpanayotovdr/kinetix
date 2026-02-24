import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchCalculationRuns, fetchCalculationRunDetail } from './runHistory'

describe('runHistory API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const runSummary = {
    runId: 'run-1',
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

  const runDetail = {
    ...runSummary,
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

  describe('fetchCalculationRuns', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([runSummary]),
      })

      const result = await fetchCalculationRuns('port-1')

      expect(result).toEqual([runSummary])
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/runs/port-1?limit=20&offset=0',
      )
    })

    it('passes custom limit and offset', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      })

      await fetchCalculationRuns('port-1', 5, 10)

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/runs/port-1?limit=5&offset=10',
      )
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchCalculationRuns('port-1')).rejects.toThrow(
        'Failed to fetch calculation runs: 500 Internal Server Error',
      )
    })
  })

  describe('fetchCalculationRunDetail', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(runDetail),
      })

      const result = await fetchCalculationRunDetail('run-1')

      expect(result).toEqual(runDetail)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/runs/detail/run-1',
      )
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await fetchCalculationRunDetail('unknown')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchCalculationRunDetail('run-1')).rejects.toThrow(
        'Failed to fetch run detail: 500 Internal Server Error',
      )
    })
  })
})
