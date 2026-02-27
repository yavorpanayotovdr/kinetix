import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useVaR } from './useVaR'

vi.mock('../api/risk', () => ({
  fetchVaR: vi.fn(),
  triggerVaRCalculation: vi.fn(),
}))

vi.mock('../api/jobHistory', () => ({
  fetchValuationJobs: vi.fn(),
}))

import { fetchVaR } from '../api/risk'
import { fetchValuationJobs } from '../api/jobHistory'

const mockFetchVaR = vi.mocked(fetchVaR)
const mockFetchValuationJobs = vi.mocked(fetchValuationJobs)

describe('useVaR', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('populates history from job history on initial load', async () => {
    mockFetchValuationJobs.mockResolvedValue({
      items: [
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
        },
        {
          jobId: 'j2',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          varValue: 1300000,
          expectedShortfall: 1600000,
          pvValue: 11000000,
        },
      ],
      totalCount: 2,
    })
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchValuationJobs).toHaveBeenCalledWith(
      'port-1',
      60,
      0,
      expect.any(String),
      expect.any(String),
    )
    expect(result.current.history).toHaveLength(2)
    expect(result.current.history[0].varValue).toBe(1200000)
    expect(result.current.history[1].varValue).toBe(1300000)
  })

  it('filters out non-COMPLETED jobs and jobs with null varValue', async () => {
    mockFetchValuationJobs.mockResolvedValue({
      items: [
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
        },
        {
          jobId: 'j2',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'FAILED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: null,
          durationMs: null,
          calculationType: null,
          varValue: null,
          expectedShortfall: null,
          pvValue: null,
        },
        {
          jobId: 'j3',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T11:00:00Z',
          completedAt: '2025-01-15T11:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          varValue: null,
          expectedShortfall: null,
          pvValue: null,
        },
      ],
      totalCount: 3,
    })
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(1)
    expect(result.current.history[0].varValue).toBe(1200000)
  })

  it('sorts history by completedAt ascending', async () => {
    mockFetchValuationJobs.mockResolvedValue({
      items: [
        {
          jobId: 'j2',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          varValue: 1300000,
          expectedShortfall: 1600000,
          pvValue: 11000000,
        },
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
        },
      ],
      totalCount: 2,
    })
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history[0].calculatedAt).toBe('2025-01-15T09:01:00Z')
    expect(result.current.history[1].calculatedAt).toBe('2025-01-15T10:01:00Z')
  })

  it('appends polled VaR result to pre-loaded history without duplicating', async () => {
    mockFetchValuationJobs.mockResolvedValue({
      items: [
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
        },
      ],
      totalCount: 1,
    })
    mockFetchVaR.mockResolvedValue({
      portfolioId: 'port-1',
      calculationType: 'HISTORICAL',
      confidenceLevel: 'CL_95',
      varValue: '1400000',
      expectedShortfall: '1700000',
      componentBreakdown: [],
      calculatedAt: '2025-01-15T10:30:00Z',
    })

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(2)
    expect(result.current.history[0].varValue).toBe(1200000)
    expect(result.current.history[1].varValue).toBe(1400000)
  })

  it('does not fetch job history when portfolioId is null', () => {
    const { result } = renderHook(() => useVaR(null))

    expect(mockFetchValuationJobs).not.toHaveBeenCalled()
    expect(result.current.history).toHaveLength(0)
    expect(result.current.loading).toBe(false)
  })

  it('gracefully handles job history fetch failure', async () => {
    mockFetchValuationJobs.mockRejectedValue(new Error('Network error'))
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(0)
    expect(result.current.error).toBeNull()
  })
})
