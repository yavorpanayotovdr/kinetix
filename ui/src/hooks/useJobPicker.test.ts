import { renderHook, waitFor, act } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('../api/jobHistory')

import { useJobPicker } from './useJobPicker'
import { fetchValuationJobs } from '../api/jobHistory'

const mockFetch = vi.mocked(fetchValuationJobs)

const completedJobs = [
  {
    jobId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    bookId: 'book-1',
    triggerType: 'ON_DEMAND',
    status: 'COMPLETED',
    startedAt: '2025-01-15T08:00:00Z',
    completedAt: '2025-01-15T08:01:00Z',
    durationMs: 60000,
    calculationType: 'PARAMETRIC',
    confidenceLevel: 'CL_95',
    varValue: 500.0,
    expectedShortfall: 600.0,
    pvValue: null,
    delta: null, gamma: null, vega: null, theta: null, rho: null,
    runLabel: null, promotedAt: null, promotedBy: null, manifestId: null,
  },
  {
    jobId: 'bbbbbbbb-cccc-dddd-eeee-ffffffffffff',
    bookId: 'book-1',
    triggerType: 'SCHEDULED',
    status: 'COMPLETED',
    startedAt: '2025-01-15T06:00:00Z',
    completedAt: '2025-01-15T06:00:30Z',
    durationMs: 30000,
    calculationType: 'HISTORICAL',
    confidenceLevel: 'CL_95',
    varValue: 450.0,
    expectedShortfall: 550.0,
    pvValue: null,
    delta: null, gamma: null, vega: null, theta: null, rho: null,
    runLabel: null, promotedAt: null, promotedBy: null, manifestId: null,
  },
]

describe('useJobPicker', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockFetch.mockResolvedValue({ items: completedJobs, totalCount: 2 })
  })

  it('does not fetch when bookId is null', () => {
    renderHook(() => useJobPicker(null, true))

    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('does not fetch when open is false', () => {
    renderHook(() => useJobPicker('book-1', false))

    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('fetches completed jobs with status=COMPLETED on open', async () => {
    renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        'book-1',
        20,
        0,
        expect.any(String),
        expect.any(String),
        'COMPLETED',
      )
    })
  })

  it('defaults to Today time range', async () => {
    renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalled()
    })

    const [, , , from] = mockFetch.mock.calls[0]
    const fromDate = new Date(from!)
    const today = new Date()
    expect(fromDate.getFullYear()).toBe(today.getFullYear())
    expect(fromDate.getMonth()).toBe(today.getMonth())
    expect(fromDate.getDate()).toBe(today.getDate())
    expect(fromDate.getHours()).toBe(0)
    expect(fromDate.getMinutes()).toBe(0)
  })

  it('sets loading state during fetch', async () => {
    let resolvePromise: (v: { items: typeof completedJobs; totalCount: number }) => void
    mockFetch.mockReturnValue(new Promise((resolve) => { resolvePromise = resolve }))

    const { result } = renderHook(() => useJobPicker('book-1', true))

    expect(result.current.loading).toBe(true)

    await act(async () => {
      resolvePromise!({ items: completedJobs, totalCount: 2 })
    })

    expect(result.current.loading).toBe(false)
  })

  it('sets error on fetch failure', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.error).toBe('Network error')
    })
  })

  it('returns fetched jobs', async () => {
    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    expect(result.current.jobs[0].jobId).toBe('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
  })

  it('filters jobs client-side by keyword search', async () => {
    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    act(() => {
      result.current.setSearch('PARAMETRIC')
    })

    expect(result.current.jobs).toHaveLength(1)
    expect(result.current.jobs[0].calculationType).toBe('PARAMETRIC')
  })

  it('filters by UUID prefix matching only jobId', async () => {
    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    act(() => {
      result.current.setSearch('aaaaaaaa')
    })

    expect(result.current.jobs).toHaveLength(1)
    expect(result.current.jobs[0].jobId).toBe('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
  })

  it('shows all jobs when search term is empty', async () => {
    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    act(() => {
      result.current.setSearch('PARAMETRIC')
    })
    expect(result.current.jobs).toHaveLength(1)

    act(() => {
      result.current.setSearch('')
    })
    expect(result.current.jobs).toHaveLength(2)
  })

  it('resets page to 0 when time range changes', async () => {
    mockFetch.mockResolvedValue({ items: completedJobs, totalCount: 40 })

    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    act(() => {
      result.current.nextPage()
    })
    expect(result.current.page).toBe(1)

    act(() => {
      result.current.setTimeRange({ from: '', to: '', label: 'Last 24h' })
    })
    expect(result.current.page).toBe(0)
  })

  it('re-fetches when time range changes', async () => {
    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(1)
    })

    await act(async () => {
      result.current.setTimeRange({
        from: new Date().toISOString(),
        to: new Date().toISOString(),
        label: 'Last 24h',
      })
    })

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(2)
    })
  })

  it('nextPage increments page and re-fetches with correct offset', async () => {
    mockFetch.mockResolvedValue({ items: completedJobs, totalCount: 40 })

    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    await act(async () => {
      result.current.nextPage()
    })

    expect(result.current.page).toBe(1)

    await waitFor(() => {
      const lastCall = mockFetch.mock.calls[mockFetch.mock.calls.length - 1]
      expect(lastCall[2]).toBe(20) // offset = page 1 * pageSize 20
    })
  })

  it('prevPage decrements page', async () => {
    mockFetch.mockResolvedValue({ items: completedJobs, totalCount: 40 })

    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    await act(async () => {
      result.current.nextPage()
    })
    expect(result.current.page).toBe(1)

    await act(async () => {
      result.current.prevPage()
    })
    expect(result.current.page).toBe(0)
  })

  it('prevPage is a no-op on page 0', async () => {
    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    act(() => {
      result.current.prevPage()
    })
    expect(result.current.page).toBe(0)
  })

  it('computes totalPages from totalCount and pageSize', async () => {
    mockFetch.mockResolvedValue({ items: completedJobs, totalCount: 45 })

    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.totalPages).toBe(3) // ceil(45/20)
    })
  })

  it('hasNextPage reflects whether more pages exist', async () => {
    mockFetch.mockResolvedValue({ items: completedJobs, totalCount: 2 })

    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.hasNextPage).toBe(false)
    })
  })

  it('firstPage resets to page 0', async () => {
    mockFetch.mockResolvedValue({ items: completedJobs, totalCount: 60 })

    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    await act(async () => {
      result.current.nextPage()
    })
    await act(async () => {
      result.current.nextPage()
    })
    expect(result.current.page).toBe(2)

    await act(async () => {
      result.current.firstPage()
    })
    expect(result.current.page).toBe(0)
  })

  it('lastPage jumps to the final page', async () => {
    mockFetch.mockResolvedValue({ items: completedJobs, totalCount: 60 })

    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    await act(async () => {
      result.current.lastPage()
    })
    expect(result.current.page).toBe(2) // ceil(60/20) - 1
  })

  it('refresh re-fetches current page', async () => {
    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(1)
    })

    await act(async () => {
      result.current.refresh()
    })

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(2)
    })
  })

  it('resets all state when open changes from true to false and back', async () => {
    const { result, rerender } = renderHook(
      ({ open }) => useJobPicker('book-1', open),
      { initialProps: { open: true } },
    )

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    act(() => {
      result.current.setSearch('PARAMETRIC')
    })
    expect(result.current.jobs).toHaveLength(1)

    rerender({ open: false })

    expect(result.current.search).toBe('')
    expect(result.current.page).toBe(0)

    rerender({ open: true })

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })
    expect(result.current.search).toBe('')
  })

  it('only fetches once on initial mount without explicit refresh', async () => {
    const { result } = renderHook(() => useJobPicker('book-1', true))

    await waitFor(() => {
      expect(result.current.jobs).toHaveLength(2)
    })

    // Only the initial fetch — no polling
    expect(mockFetch).toHaveBeenCalledTimes(1)
  })
})
