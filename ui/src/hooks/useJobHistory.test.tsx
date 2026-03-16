import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/jobHistory')

import { fetchValuationJobs, fetchValuationJobDetail, fetchValuationJobsForChart } from '../api/jobHistory'
import { useJobHistory } from './useJobHistory'

const mockFetchJobs = vi.mocked(fetchValuationJobs)
const mockFetchJobDetail = vi.mocked(fetchValuationJobDetail)
const mockFetchChartJobs = vi.mocked(fetchValuationJobsForChart)

const jobSummary = {
  jobId: 'job-1',
  portfolioId: 'port-1',
  triggerType: 'ON_DEMAND',
  status: 'COMPLETED',
  startedAt: '2025-01-15T10:00:00Z',
  completedAt: '2025-01-15T10:00:00.150Z',
  durationMs: 150,
  calculationType: 'PARAMETRIC',
  confidenceLevel: 'CL_95',
  varValue: 5000.0,
  expectedShortfall: 6250.0,
  pvValue: 50000.0,
  delta: 0,
  gamma: 0,
  vega: 0,
  theta: 0,
  rho: 0,
  runLabel: null, promotedAt: null, promotedBy: null, manifestId: null,
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
  confidenceLevel: 'CL_95',
  varValue: 4000.0,
  expectedShortfall: 5000.0,
  pvValue: 40000.0,
  delta: 0,
  gamma: 0,
  vega: 0,
  theta: 0,
  rho: 0,
  runLabel: null, promotedAt: null, promotedBy: null, manifestId: null,
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
  runLabel: null, promotedAt: null, promotedBy: null, manifestId: null,
}

const jobDetail2 = {
  ...jobSummary2,
  confidenceLevel: 'CL_99',
  phases: [
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
  runLabel: null, promotedAt: null, promotedBy: null, manifestId: null,
}

describe('useJobHistory', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers({ shouldAdvanceTime: true })
    mockFetchChartJobs.mockResolvedValue([])
  })

  afterEach(() => {
    vi.useRealTimers()
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
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.runs).toEqual([jobSummary])
    expect(mockFetchJobs).toHaveBeenCalledWith(
      'port-1',
      10,
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
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })
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
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })
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
    mockFetchJobs.mockResolvedValue({ items: [jobSummary, jobSummary2], totalCount: 2 })
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
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })
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
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })
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
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchJobs).toHaveBeenCalledTimes(1)

    const newRange = {
      from: '2025-01-15T00:00:00Z',
      to: '2025-01-15T23:59:59Z',
      label: 'Custom',
    }

    act(() => {
      result.current.setTimeRange(newRange)
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(2)
    })

    expect(mockFetchJobs).toHaveBeenLastCalledWith(
      'port-1',
      10,
      0,
      '2025-01-15T00:00:00Z',
      '2025-01-15T23:59:59Z',
    )
  })

  it('exposes zoomDepth of 0 initially', async () => {
    mockFetchJobs.mockResolvedValue({ items: [], totalCount: 0 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.zoomDepth).toBe(0)
  })

  it('zoomIn pushes current range onto zoom stack and sets new range', async () => {
    mockFetchJobs.mockResolvedValue({ items: [], totalCount: 0 })

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

    await waitFor(() => expect(result.current.loading).toBe(false))

    // Zoom again
    const zoomedRange2 = { from: '2025-01-15T10:15:00Z', to: '2025-01-15T10:30:00Z', label: 'Custom' }
    act(() => {
      result.current.zoomIn(zoomedRange2)
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.timeRange).toEqual(zoomedRange2)
    expect(result.current.zoomDepth).toBe(2)
  })

  it('resetZoom restores the original range and clears the stack', async () => {
    mockFetchJobs.mockResolvedValue({ items: [], totalCount: 0 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const originalRange = result.current.timeRange

    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:00:00Z', to: '2025-01-15T11:00:00Z', label: 'Custom' })
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:15:00Z', to: '2025-01-15T10:30:00Z', label: 'Custom' })
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.zoomDepth).toBe(2)

    act(() => {
      result.current.resetZoom()
    })

    expect(result.current.timeRange).toEqual(originalRange)
    expect(result.current.zoomDepth).toBe(0)

    await waitFor(() => expect(result.current.loading).toBe(false))
  })

  it('setTimeRange clears the zoom stack', async () => {
    mockFetchJobs.mockResolvedValue({ items: [], totalCount: 0 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:00:00Z', to: '2025-01-15T11:00:00Z', label: 'Custom' })
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.zoomDepth).toBe(1)

    const newRange = { from: '2025-01-14T00:00:00Z', to: '2025-01-15T00:00:00Z', label: 'Last 24h' }
    act(() => {
      result.current.setTimeRange(newRange)
    })

    expect(result.current.timeRange).toEqual(newRange)
    expect(result.current.zoomDepth).toBe(0)

    await waitFor(() => expect(result.current.loading).toBe(false))
  })

  it('resets jobs and expanded state when portfolioId becomes null', async () => {
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })

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

  it('polls every 5 seconds to pick up new jobs and state changes', async () => {
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })

    renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(1)
    })

    await act(async () => {
      vi.advanceTimersByTime(5_000)
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(2)
    })

    await act(async () => {
      vi.advanceTimersByTime(5_000)
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(3)
    })
  })

  it('pins from and advances to for relative presets on each poll', async () => {
    mockFetchJobs.mockResolvedValue({ items: [], totalCount: 0 })

    renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(1)
    })

    const firstFrom = mockFetchJobs.mock.calls[0][3]!
    const firstTo = mockFetchJobs.mock.calls[0][4]!

    await act(async () => {
      vi.advanceTimersByTime(5_000)
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(2)
    })

    const secondFrom = mockFetchJobs.mock.calls[1][3]!
    const secondTo = mockFetchJobs.mock.calls[1][4]!

    // from stays pinned so items don't shuffle out of view
    expect(secondFrom).toBe(firstFrom)
    // to advances to pick up new jobs
    expect(new Date(secondTo).getTime()).toBeGreaterThan(new Date(firstTo).getTime())
  })

  it('does not slide time window for Custom ranges', async () => {
    mockFetchJobs.mockResolvedValue({ items: [], totalCount: 0 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(1)
    })

    act(() => {
      result.current.setTimeRange({
        from: '2025-01-15T00:00:00Z',
        to: '2025-01-15T12:00:00Z',
        label: 'Custom',
      })
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(2)
    })

    await act(async () => {
      vi.advanceTimersByTime(5_000)
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(3)
    })

    expect(mockFetchJobs.mock.calls[1][3]).toBe('2025-01-15T00:00:00Z')
    expect(mockFetchJobs.mock.calls[1][4]).toBe('2025-01-15T12:00:00Z')
    expect(mockFetchJobs.mock.calls[2][3]).toBe('2025-01-15T00:00:00Z')
    expect(mockFetchJobs.mock.calls[2][4]).toBe('2025-01-15T12:00:00Z')
  })

  it('fetches with limit=10 and offset=0 initially', async () => {
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })

    renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(1)
    })

    expect(mockFetchJobs).toHaveBeenCalledWith(
      'port-1',
      10,
      0,
      expect.any(String),
      expect.any(String),
    )
  })

  it('nextPage increments page and re-fetches with offset=10', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 10 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 40 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    mockFetchJobs.mockClear()
    mockFetchJobs.mockResolvedValue({ items: [{ ...jobSummary, jobId: 'job-page2' }], totalCount: 40 })

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledWith(
        'port-1',
        10,
        10,
        expect.any(String),
        expect.any(String),
      )
    })

    expect(result.current.page).toBe(1)
  })

  it('prevPage decrements page', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 40 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => {
      expect(result.current.page).toBe(1)
    })

    mockFetchJobs.mockClear()
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 40 })

    act(() => {
      result.current.prevPage()
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledWith(
        'port-1',
        10,
        0,
        expect.any(String),
        expect.any(String),
      )
    })

    expect(result.current.page).toBe(0)
  })

  it('prevPage is a no-op when page is 0', async () => {
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const callCount = mockFetchJobs.mock.calls.length

    act(() => {
      result.current.prevPage()
    })

    expect(result.current.page).toBe(0)
    expect(mockFetchJobs).toHaveBeenCalledTimes(callCount)
  })

  it('hasNextPage is true when totalCount exceeds current page, false on last page', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 10 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 15 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.hasNextPage).toBe(true)

    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 5 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 15 })

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.hasNextPage).toBe(false)
  })

  it('exposes totalPages computed from totalCount', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 10 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 25 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.totalPages).toBe(3)
    expect(result.current.runs).toHaveLength(10)
  })

  it('resets page to 0 when time range changes', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 40 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => {
      expect(result.current.page).toBe(1)
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => {
      result.current.setTimeRange({ from: '2025-01-14T00:00:00Z', to: '2025-01-15T00:00:00Z', label: 'Custom' })
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.page).toBe(0)
  })

  it('resets page to 0 when zoomIn is called', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 40 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => {
      expect(result.current.page).toBe(1)
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:00:00Z', to: '2025-01-15T11:00:00Z', label: 'Custom' })
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.page).toBe(0)
  })

  it('nextPage and prevPage clear expanded jobs', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 40 })
    mockFetchJobDetail.mockResolvedValue(jobDetail)

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    await act(async () => {
      result.current.toggleJob('job-0')
    })

    await waitFor(() => {
      expect(result.current.expandedJobs['job-0']).toBeDefined()
    })

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.expandedJobs).toEqual({})
  })

  it('firstPage resets to page 0', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 60 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => {
      expect(result.current.page).toBe(1)
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => {
      expect(result.current.page).toBe(2)
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => {
      result.current.firstPage()
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.page).toBe(0)
  })

  it('lastPage jumps to the final page', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 10 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 30 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.totalPages).toBe(3)

    act(() => {
      result.current.lastPage()
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.page).toBe(2)
  })

  it('goToPage jumps to specified page and re-fetches', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 100 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    mockFetchJobs.mockClear()
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-p2-${i}` })), totalCount: 100 })

    act(() => {
      result.current.goToPage(2)
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledWith(
        'port-1',
        10,
        20,
        expect.any(String),
        expect.any(String),
      )
    })

    expect(result.current.page).toBe(2)
  })

  it('goToPage clamps to last page when target exceeds totalPages', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 10 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 50 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.totalPages).toBe(5)

    act(() => {
      result.current.goToPage(99)
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.page).toBe(4)
  })

  it('goToPage clamps to 0 when target is negative', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 100 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.goToPage(-1)
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.page).toBe(0)
  })

  it('goToPage clears expanded jobs', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 20 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 100 })
    mockFetchJobDetail.mockResolvedValue(jobDetail)

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    await act(async () => {
      result.current.toggleJob('job-0')
    })

    await waitFor(() => {
      expect(result.current.expandedJobs['job-0']).toBeDefined()
    })

    act(() => {
      result.current.goToPage(2)
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.expandedJobs).toEqual({})
    expect(result.current.loadingJobIds.size).toBe(0)
  })

  it('setPageSize changes the limit and re-fetches', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 10 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 100 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    mockFetchJobs.mockClear()
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 50 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 100 })

    act(() => {
      result.current.setPageSize(50)
    })

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledWith(
        'port-1',
        50,
        0,
        expect.any(String),
        expect.any(String),
      )
    })

    expect(result.current.pageSize).toBe(50)
  })

  it('setPageSize resets page to 0', async () => {
    mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 10 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 100 })

    const { result } = renderHook(() => useJobHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.nextPage()
    })

    await waitFor(() => {
      expect(result.current.page).toBe(1)
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => {
      result.current.setPageSize(20)
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.page).toBe(0)
    expect(result.current.pageSize).toBe(20)
  })

  it('stops polling when portfolioId becomes null', async () => {
    mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })

    const { rerender } = renderHook(
      ({ pid }) => useJobHistory(pid),
      { initialProps: { pid: 'port-1' as string | null } },
    )

    await waitFor(() => {
      expect(mockFetchJobs).toHaveBeenCalledTimes(1)
    })

    rerender({ pid: null })

    await act(async () => {
      vi.advanceTimersByTime(10_000)
    })

    expect(mockFetchJobs).toHaveBeenCalledTimes(1)
  })

  describe('chartRuns', () => {
    it('fetches chart data on mount via fetchValuationJobsForChart', async () => {
      mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })
      mockFetchChartJobs.mockResolvedValue([jobSummary, jobSummary2])

      const { result } = renderHook(() => useJobHistory('port-1'))

      await waitFor(() => {
        expect(result.current.chartRuns).toHaveLength(2)
      })

      expect(mockFetchChartJobs).toHaveBeenCalledWith(
        'port-1',
        expect.any(String),
        expect.any(String),
      )
    })

    it('returns empty chartRuns when portfolioId is null', () => {
      const { result } = renderHook(() => useJobHistory(null))

      expect(result.current.chartRuns).toEqual([])
      expect(mockFetchChartJobs).not.toHaveBeenCalled()
    })

    it('clears chartRuns when portfolioId becomes null', async () => {
      mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })
      mockFetchChartJobs.mockResolvedValue([jobSummary])

      const { result, rerender } = renderHook(
        ({ pid }) => useJobHistory(pid),
        { initialProps: { pid: 'port-1' as string | null } },
      )

      await waitFor(() => {
        expect(result.current.chartRuns).toHaveLength(1)
      })

      rerender({ pid: null })

      expect(result.current.chartRuns).toEqual([])
    })

    it('re-fetches chart data when time range changes', async () => {
      mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })
      mockFetchChartJobs.mockResolvedValue([jobSummary])

      const { result } = renderHook(() => useJobHistory('port-1'))

      await waitFor(() => {
        expect(result.current.chartRuns).toHaveLength(1)
      })

      expect(mockFetchChartJobs).toHaveBeenCalledTimes(1)

      mockFetchChartJobs.mockResolvedValue([jobSummary, jobSummary2])

      act(() => {
        result.current.setTimeRange({
          from: '2025-01-14T00:00:00Z',
          to: '2025-01-15T00:00:00Z',
          label: 'Custom',
        })
      })

      await waitFor(() => {
        expect(mockFetchChartJobs).toHaveBeenCalledTimes(2)
      })

      await waitFor(() => {
        expect(result.current.chartRuns).toHaveLength(2)
      })
    })

    it('does not re-fetch chart data on page navigation', async () => {
      mockFetchJobs.mockResolvedValue({ items: Array.from({ length: 10 }, (_, i) => ({ ...jobSummary, jobId: `job-${i}` })), totalCount: 40 })
      mockFetchChartJobs.mockResolvedValue([jobSummary])

      const { result } = renderHook(() => useJobHistory('port-1'))

      await waitFor(() => {
        expect(result.current.chartRuns).toHaveLength(1)
      })

      expect(mockFetchChartJobs).toHaveBeenCalledTimes(1)

      act(() => {
        result.current.nextPage()
      })

      await waitFor(() => {
        expect(result.current.page).toBe(1)
      })

      // Chart fetch should NOT have been called again
      expect(mockFetchChartJobs).toHaveBeenCalledTimes(1)
    })

    it('does not re-fetch chart data on 5-second poll', async () => {
      mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })
      mockFetchChartJobs.mockResolvedValue([jobSummary])

      renderHook(() => useJobHistory('port-1'))

      await waitFor(() => {
        expect(mockFetchChartJobs).toHaveBeenCalledTimes(1)
      })

      await act(async () => {
        vi.advanceTimersByTime(5_000)
      })

      await waitFor(() => {
        expect(mockFetchJobs).toHaveBeenCalledTimes(2)
      })

      // Chart should still be 1 — not polled
      expect(mockFetchChartJobs).toHaveBeenCalledTimes(1)
    })

    it('re-fetches chart data on zoom', async () => {
      mockFetchJobs.mockResolvedValue({ items: [], totalCount: 0 })
      mockFetchChartJobs.mockResolvedValue([])

      const { result } = renderHook(() => useJobHistory('port-1'))

      await waitFor(() => {
        expect(result.current.loading).toBe(false)
      })

      expect(mockFetchChartJobs).toHaveBeenCalledTimes(1)

      act(() => {
        result.current.zoomIn({ from: '2025-01-15T10:00:00Z', to: '2025-01-15T11:00:00Z', label: 'Custom' })
      })

      await waitFor(() => {
        expect(mockFetchChartJobs).toHaveBeenCalledTimes(2)
      })
    })

    it('does not modify chartRuns from table poll — only loadChart updates chart data', async () => {
      mockFetchChartJobs.mockResolvedValue([jobSummary2])
      mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 2 })

      const { result } = renderHook(() => useJobHistory('port-1'))

      await waitFor(() => {
        expect(result.current.chartRuns).toHaveLength(1)
        expect(result.current.chartRuns[0].jobId).toBe('job-2')
      })

      // Poll should not append page items to chartRuns
      await act(async () => {
        vi.advanceTimersByTime(5_000)
      })

      await waitFor(() => {
        expect(mockFetchJobs).toHaveBeenCalledTimes(2)
      })

      // chartRuns stays at 1 — only loadChart controls it
      expect(result.current.chartRuns).toHaveLength(1)
      expect(result.current.chartRuns[0].jobId).toBe('job-2')
    })

    it('silently ignores chart fetch errors', async () => {
      mockFetchJobs.mockResolvedValue({ items: [jobSummary], totalCount: 1 })
      mockFetchChartJobs.mockRejectedValue(new Error('Network error'))

      const { result } = renderHook(() => useJobHistory('port-1'))

      await waitFor(() => {
        expect(result.current.loading).toBe(false)
      })

      // Table data should still load
      expect(result.current.runs).toEqual([jobSummary])
      // chartRuns should remain empty
      expect(result.current.chartRuns).toEqual([])
      // error should not be set (chart failure is non-critical)
      expect(result.current.error).toBeNull()
    })
  })
})
