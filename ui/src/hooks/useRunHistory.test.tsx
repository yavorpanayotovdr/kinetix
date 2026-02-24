import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/runHistory')

import { fetchCalculationRuns, fetchCalculationRunDetail } from '../api/runHistory'
import { useRunHistory } from './useRunHistory'

const mockFetchRuns = vi.mocked(fetchCalculationRuns)
const mockFetchDetail = vi.mocked(fetchCalculationRunDetail)

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

describe('useRunHistory', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('does nothing when portfolioId is null', () => {
    const { result } = renderHook(() => useRunHistory(null))

    expect(result.current.runs).toEqual([])
    expect(result.current.selectedRunId).toBeNull()
    expect(result.current.selectedRun).toBeNull()
    expect(result.current.detailLoading).toBe(false)
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
    expect(mockFetchRuns).not.toHaveBeenCalled()
  })

  it('fetches runs on mount when portfolioId is provided', async () => {
    mockFetchRuns.mockResolvedValue([runSummary])

    const { result } = renderHook(() => useRunHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.runs).toEqual([runSummary])
    expect(mockFetchRuns).toHaveBeenCalledWith('port-1')
  })

  it('sets error on fetch failure', async () => {
    mockFetchRuns.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useRunHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
  })

  it('selectRun sets selectedRunId immediately and fetches detail without setting loading', async () => {
    mockFetchRuns.mockResolvedValue([runSummary])
    mockFetchDetail.mockResolvedValue(runDetail)

    const { result } = renderHook(() => useRunHistory('port-1'))

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(1)
    })

    await act(async () => {
      result.current.selectRun('run-1')
    })

    await waitFor(() => {
      expect(result.current.detailLoading).toBe(false)
    })

    expect(result.current.selectedRunId).toBe('run-1')
    expect(result.current.selectedRun).toEqual(runDetail)
    expect(result.current.loading).toBe(false)
    expect(mockFetchDetail).toHaveBeenCalledWith('run-1')
  })

  it('selectRun toggles off when clicking the same run again', async () => {
    mockFetchRuns.mockResolvedValue([runSummary])
    mockFetchDetail.mockResolvedValue(runDetail)

    const { result } = renderHook(() => useRunHistory('port-1'))

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(1)
    })

    await act(async () => {
      result.current.selectRun('run-1')
    })

    await waitFor(() => {
      expect(result.current.selectedRun).not.toBeNull()
    })

    act(() => {
      result.current.selectRun('run-1')
    })

    expect(result.current.selectedRunId).toBeNull()
    expect(result.current.selectedRun).toBeNull()
    expect(mockFetchDetail).toHaveBeenCalledTimes(1)
  })

  it('clearSelection resets selectedRun', async () => {
    mockFetchRuns.mockResolvedValue([runSummary])
    mockFetchDetail.mockResolvedValue(runDetail)

    const { result } = renderHook(() => useRunHistory('port-1'))

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(1)
    })

    await act(async () => {
      result.current.selectRun('run-1')
    })

    await waitFor(() => {
      expect(result.current.selectedRun).not.toBeNull()
    })

    act(() => {
      result.current.clearSelection()
    })

    expect(result.current.selectedRunId).toBeNull()
    expect(result.current.selectedRun).toBeNull()
  })

  it('resets runs when portfolioId becomes null', async () => {
    mockFetchRuns.mockResolvedValue([runSummary])

    const { result, rerender } = renderHook(
      ({ pid }) => useRunHistory(pid),
      { initialProps: { pid: 'port-1' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(1)
    })

    rerender({ pid: null })

    expect(result.current.runs).toEqual([])
  })
})
