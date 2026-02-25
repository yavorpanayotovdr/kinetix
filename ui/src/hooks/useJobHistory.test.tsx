import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/jobHistory')

import { fetchValuationJobs, fetchValuationJobDetail } from '../api/jobHistory'
import { useJobHistory } from './useJobHistory'

const mockFetchJobs = vi.mocked(fetchValuationJobs)
const mockFetchJobDetail = vi.mocked(fetchValuationJobDetail)

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

const jobSummary2 = {
  jobId: 'job-2',
  portfolioId: 'port-1',
  triggerType: 'TRADE_EVENT',
  status: 'COMPLETED',
  startedAt: '2025-01-15T09:00:00Z',
  completedAt: '2025-01-15T09:00:00.200Z',
  durationMs: 200,
  calculationType: 'PARAMETRIC',
  varValue: 4000.0,
  expectedShortfall: 5000.0,
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

const jobDetail2 = {
  ...jobSummary2,
  confidenceLevel: 'CL_99',
  steps: [
    {
      name: 'FETCH_POSITIONS',
      status: 'COMPLETED',
      startedAt: '2025-01-15T09:00:00Z',
      completedAt: '2025-01-15T09:00:00.030Z',
      durationMs: 30,
      details: { positionCount: '3' },
      error: null,
    },
  ],
  error: null,
}

describe('useJobHistory', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('does nothing when portfolioId is null', () => {
    const { result } = renderHook(() => useJobHistory(null))

    expect(result.current.runs).toEqual([])
    expect(result.current.expandedJobs).toEqual({})
    expect(result.current.loadingJobIds.size).toBe(0)
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
    expect(mockFetchJobs).not.toHaveBeenCalled()
  })

  it('fetches jobs on mount when portfolioId is provided', async () => {
    mockFetchJobs.mockResolvedValue([jobSummary])

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.runs).toEqual([jobSummary])
    expect(mockFetchJobs).toHaveBeenCalledWith(
      'port-1',
      200,
      0,
      expect.any(String),
      expect.any(String),
    )
  })

  it('sets error on fetch failure', async () => {
    mockFetchJobs.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
  })

  it('toggleJob expands a job and stores its detail in expandedJobs', async () => {
    mockFetchJobs.mockResolvedValue([jobSummary])
    mockFetchJobDetail.mockResolvedValue(jobDetail)

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(1)
    })

    await act(async () => {
      result.current.toggleJob('job-1')
    })

    await waitFor(() => {
      expect(result.current.loadingJobIds.size).toBe(0)
    })

    expect(result.current.expandedJobs['job-1']).toEqual(jobDetail)
    expect(result.current.loading).toBe(false)
    expect(mockFetchJobDetail).toHaveBeenCalledWith('job-1')
  })

  it('toggleJob collapses an already expanded job', async () => {
    mockFetchJobs.mockResolvedValue([jobSummary])
    mockFetchJobDetail.mockResolvedValue(jobDetail)

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(1)
    })

    await act(async () => {
      result.current.toggleJob('job-1')
    })

    await waitFor(() => {
      expect(result.current.expandedJobs['job-1']).toBeDefined()
    })

    act(() => {
      result.current.toggleJob('job-1')
    })

    expect(result.current.expandedJobs['job-1']).toBeUndefined()
    expect(mockFetchJobDetail).toHaveBeenCalledTimes(1)
  })

  it('keeps first job open when selecting a second job', async () => {
    mockFetchJobs.mockResolvedValue([jobSummary, jobSummary2])
    mockFetchJobDetail
      .mockResolvedValueOnce(jobDetail)
      .mockResolvedValueOnce(jobDetail2)

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(2)
    })

    await act(async () => {
      result.current.toggleJob('job-1')
    })

    await waitFor(() => {
      expect(result.current.expandedJobs['job-1']).toBeDefined()
    })

    await act(async () => {
      result.current.toggleJob('job-2')
    })

    await waitFor(() => {
      expect(result.current.expandedJobs['job-2']).toBeDefined()
    })

    expect(result.current.expandedJobs['job-1']).toEqual(jobDetail)
    expect(result.current.expandedJobs['job-2']).toEqual(jobDetail2)
    expect(mockFetchJobDetail).toHaveBeenCalledTimes(2)
  })

  it('clearSelection empties expandedJobs', async () => {
    mockFetchJobs.mockResolvedValue([jobSummary])
    mockFetchJobDetail.mockResolvedValue(jobDetail)

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(1)
    })

    await act(async () => {
      result.current.toggleJob('job-1')
    })

    await waitFor(() => {
      expect(result.current.expandedJobs['job-1']).toBeDefined()
    })

    act(() => {
      result.current.clearSelection()
    })

    expect(result.current.expandedJobs).toEqual({})
  })

  it('does not store null in expandedJobs when job detail returns 404', async () => {
    mockFetchJobs.mockResolvedValue([jobSummary])
    mockFetchJobDetail.mockResolvedValue(null)

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(1)
    })

    await act(async () => {
      result.current.toggleJob('job-1')
    })

    await waitFor(() => {
      expect(result.current.loadingJobIds.size).toBe(0)
    })

    expect(result.current.expandedJobs['job-1']).toBeUndefined()
    expect(result.current.expandedJobs).toEqual({})
  })

  it('re-fetches when time range changes', async () => {
    mockFetchJobs.mockResolvedValue([jobSummary])

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchJobs).toHaveBeenCalledTimes(1)

    const newRange = {
      from: '2025-01-15T00:00:00Z',
      to: '2025-01-15T23:59:59Z',
      label: 'Today',
    }

    act(() => {
      result.current.setTimeRange(newRange)
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(2)
    })

    expect(mockFetchJobs).toHaveBeenLastCalledWith(
      'port-1',
      200,
      0,
      '2025-01-15T00:00:00Z',
      '2025-01-15T23:59:59Z',
    )
  })

  it('exposes zoomDepth of 0 initially', async () => {
    mockFetchJobs.mockResolvedValue([])

    const { result } = renderHook(() => useJobHistory('port-1'))

    expect(result.current.zoomDepth).toBe(0)
  })

  it('zoomIn pushes current range onto zoom stack and sets new range', async () => {
    mockFetchJobs.mockResolvedValue([])

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const zoomedRange = { from: '2025-01-15T10:00:00Z', to: '2025-01-15T11:00:00Z', label: 'Custom' }

    act(() => {
      result.current.zoomIn(zoomedRange)
    })

    expect(result.current.timeRange).toEqual(zoomedRange)
    expect(result.current.zoomDepth).toBe(1)

    // Zoom again
    const zoomedRange2 = { from: '2025-01-15T10:15:00Z', to: '2025-01-15T10:30:00Z', label: 'Custom' }
    act(() => {
      result.current.zoomIn(zoomedRange2)
    })

    expect(result.current.timeRange).toEqual(zoomedRange2)
    expect(result.current.zoomDepth).toBe(2)
  })

  it('resetZoom restores the original range and clears the stack', async () => {
    mockFetchJobs.mockResolvedValue([])

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const originalRange = result.current.timeRange

    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:00:00Z', to: '2025-01-15T11:00:00Z', label: 'Custom' })
    })

    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:15:00Z', to: '2025-01-15T10:30:00Z', label: 'Custom' })
    })

    expect(result.current.zoomDepth).toBe(2)

    act(() => {
      result.current.resetZoom()
    })

    expect(result.current.timeRange).toEqual(originalRange)
    expect(result.current.zoomDepth).toBe(0)
  })

  it('setTimeRange clears the zoom stack', async () => {
    mockFetchJobs.mockResolvedValue([])

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:00:00Z', to: '2025-01-15T11:00:00Z', label: 'Custom' })
    })

    expect(result.current.zoomDepth).toBe(1)

    const newRange = { from: '2025-01-14T00:00:00Z', to: '2025-01-15T00:00:00Z', label: 'Last 24h' }
    act(() => {
      result.current.setTimeRange(newRange)
    })

    expect(result.current.timeRange).toEqual(newRange)
    expect(result.current.zoomDepth).toBe(0)
  })

  it('resets jobs and expanded state when portfolioId becomes null', async () => {
    mockFetchJobs.mockResolvedValue([jobSummary])

    const { result, rerender } = renderHook(
      ({ pid }) => useJobHistory(pid),
      { initialProps: { pid: 'port-1' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.runs).toHaveLength(1)
    })

    rerender({ pid: null })

    expect(result.current.runs).toEqual([])
    expect(result.current.expandedJobs).toEqual({})
    expect(result.current.loadingJobIds.size).toBe(0)
  })
})
