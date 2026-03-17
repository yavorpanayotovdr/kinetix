import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { VaRResultDto } from '../types'

vi.mock('../api/risk')
vi.mock('../api/jobHistory')

import { fetchVaR, triggerVaRCalculation } from '../api/risk'
import { fetchChartData } from '../api/jobHistory'
import { useVaR } from './useVaR'

const mockFetchVaR = vi.mocked(fetchVaR)
const mockTriggerVaR = vi.mocked(triggerVaRCalculation)
const mockFetchHistory = vi.mocked(fetchChartData)

const varResult: VaRResultDto = {
  bookId: 'book-1',
  calculationType: 'HISTORICAL',
  confidenceLevel: 'CL_95',
  varValue: '1234567.89',
  expectedShortfall: '1567890.12',
  componentBreakdown: [
    { assetClass: 'EQUITY', varContribution: '800000.00', percentageOfTotal: '64.85' },
  ],
  calculatedAt: '2025-01-15T10:30:00Z',
}

describe('useVaR', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('does nothing when bookId is null', () => {
    const { result } = renderHook(() => useVaR(null))

    expect(result.current.varResult).toBeNull()
    expect(result.current.history).toEqual([])
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
    expect(mockFetchVaR).not.toHaveBeenCalled()
  })

  it('starts in loading state on initial fetch', () => {
    mockFetchVaR.mockReturnValue(new Promise(() => {}))

    const { result } = renderHook(() => useVaR('book-1'))

    expect(result.current.loading).toBe(true)
    expect(result.current.varResult).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('fetches VaR result on mount', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.varResult).toEqual(varResult)
    expect(result.current.error).toBeNull()
    expect(mockFetchVaR).toHaveBeenCalledWith('book-1', null)
  })

  it('sets error on fetch failure', async () => {
    mockFetchVaR.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.varResult).toBeNull()
  })

  it('handles null result (404)', async () => {
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.varResult).toBeNull()
    expect(result.current.history).toEqual([])
    expect(result.current.error).toBeNull()
  })

  it('appends to history on successful fetch', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toEqual([
      {
        varValue: 1234567.89,
        expectedShortfall: 1567890.12,
        calculatedAt: '2025-01-15T10:30:00Z',
        confidenceLevel: 'CL_95',
      },
    ])
  })

  it('deduplicates history by calculatedAt', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.history).toHaveLength(1)
    })

    // Poll returns same calculatedAt — should not duplicate
    await act(async () => {
      vi.advanceTimersByTime(30_000)
    })

    await waitFor(() => {
      expect(mockFetchVaR).toHaveBeenCalledTimes(2)
    })

    expect(result.current.history).toHaveLength(1)
  })

  it('adds new entry when calculatedAt changes', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.history).toHaveLength(1)
    })

    const updatedResult = {
      ...varResult,
      varValue: '999999.00',
      calculatedAt: '2025-01-15T11:00:00Z',
    }
    mockFetchVaR.mockResolvedValue(updatedResult)

    await act(async () => {
      vi.advanceTimersByTime(30_000)
    })

    await waitFor(() => {
      expect(result.current.history).toHaveLength(2)
    })

    expect(result.current.history[1]).toEqual({
      varValue: 999999.0,
      expectedShortfall: 1567890.12,
      calculatedAt: '2025-01-15T11:00:00Z',
      confidenceLevel: 'CL_95',
    })
  })

  it('polls every 30 seconds', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(mockFetchVaR).toHaveBeenCalledTimes(1)
    })

    await act(async () => {
      vi.advanceTimersByTime(30_000)
    })

    await waitFor(() => {
      expect(mockFetchVaR).toHaveBeenCalledTimes(2)
    })

    await act(async () => {
      vi.advanceTimersByTime(30_000)
    })

    await waitFor(() => {
      expect(mockFetchVaR).toHaveBeenCalledTimes(3)
    })
  })

  it('refresh triggers a new VaR calculation', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const freshResult = {
      ...varResult,
      varValue: '999999.00',
      calculatedAt: '2025-01-15T11:00:00Z',
    }
    mockTriggerVaR.mockResolvedValue(freshResult)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchVaR).toHaveBeenCalledTimes(1)
    expect(mockTriggerVaR).not.toHaveBeenCalled()

    await act(async () => {
      result.current.refresh()
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockTriggerVaR).toHaveBeenCalledWith('book-1', { confidenceLevel: 'CL_95' })
    expect(result.current.varResult).toEqual(freshResult)
    expect(result.current.history).toHaveLength(2)
  })

  it('refresh sets refreshing instead of loading', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    let resolveRefresh!: (v: VaRResultDto) => void
    mockTriggerVaR.mockReturnValue(
      new Promise<VaRResultDto>((r) => {
        resolveRefresh = r
      }),
    )

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    // Start refresh — should set refreshing, NOT loading
    act(() => {
      result.current.refresh()
    })

    expect(result.current.refreshing).toBe(true)
    expect(result.current.loading).toBe(false)

    // Resolve refresh
    await act(async () => {
      resolveRefresh({ ...varResult, calculatedAt: '2025-01-15T11:00:00Z' })
    })

    await waitFor(() => {
      expect(result.current.refreshing).toBe(false)
    })
  })

  it('default time range is Last 24h', () => {
    const { result } = renderHook(() => useVaR(null))

    expect(result.current.timeRange.label).toBe('Last 24h')
  })

  it('filteredHistory only includes entries within the time range', async () => {
    const oldEntry: VaRResultDto = {
      ...varResult,
      calculatedAt: '2025-01-15T08:00:00Z',
      varValue: '100000.00',
    }
    const recentEntry: VaRResultDto = {
      ...varResult,
      calculatedAt: '2025-01-15T10:30:00Z',
      varValue: '200000.00',
    }

    mockFetchVaR.mockResolvedValueOnce(oldEntry)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.history).toHaveLength(1)
    })

    mockFetchVaR.mockResolvedValueOnce(recentEntry)

    await act(async () => {
      vi.advanceTimersByTime(30_000)
    })

    await waitFor(() => {
      expect(result.current.history).toHaveLength(2)
    })

    // Set a narrow time range that only includes the recent entry
    act(() => {
      result.current.setTimeRange({
        from: '2025-01-15T10:00:00Z',
        to: '2025-01-15T11:00:00Z',
        label: 'Custom',
      })
    })

    expect(result.current.filteredHistory).toHaveLength(1)
    expect(result.current.filteredHistory[0].varValue).toBe(200000)
  })

  it('setTimeRange clears zoom stack', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    // Zoom in to create a stack entry
    act(() => {
      result.current.zoomIn({
        from: '2025-01-15T10:00:00Z',
        to: '2025-01-15T10:30:00Z',
        label: 'Custom',
      })
    })

    expect(result.current.zoomDepth).toBe(1)

    // setTimeRange should clear the zoom stack
    act(() => {
      result.current.setTimeRange({
        from: '2025-01-15T09:00:00Z',
        to: '2025-01-15T11:00:00Z',
        label: 'Last 1h',
      })
    })

    expect(result.current.zoomDepth).toBe(0)
  })

  it('zoomIn pushes current range onto stack', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.zoomIn({
        from: '2025-01-15T10:00:00Z',
        to: '2025-01-15T10:15:00Z',
        label: 'Custom',
      })
    })

    expect(result.current.zoomDepth).toBe(1)
    expect(result.current.timeRange.label).toBe('Custom')

    // Zoom in again
    act(() => {
      result.current.zoomIn({
        from: '2025-01-15T10:05:00Z',
        to: '2025-01-15T10:10:00Z',
        label: 'Custom',
      })
    })

    expect(result.current.zoomDepth).toBe(2)
  })

  it('resetZoom restores original range', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const originalRange = result.current.timeRange

    act(() => {
      result.current.zoomIn({
        from: '2025-01-15T10:00:00Z',
        to: '2025-01-15T10:15:00Z',
        label: 'Custom',
      })
    })

    act(() => {
      result.current.zoomIn({
        from: '2025-01-15T10:05:00Z',
        to: '2025-01-15T10:10:00Z',
        label: 'Custom',
      })
    })

    expect(result.current.zoomDepth).toBe(2)

    act(() => {
      result.current.resetZoom()
    })

    expect(result.current.zoomDepth).toBe(0)
    expect(result.current.timeRange.label).toBe(originalRange.label)
  })

  it('zoomDepth reflects stack length', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.zoomDepth).toBe(0)

    act(() => {
      result.current.zoomIn({
        from: '2025-01-15T10:00:00Z',
        to: '2025-01-15T10:15:00Z',
        label: 'Custom',
      })
    })

    expect(result.current.zoomDepth).toBe(1)
  })

  describe('refresh 503 retry', () => {
    it('retries once after 5s delay when refresh returns 503, then succeeds', async () => {
      mockFetchVaR.mockResolvedValue(varResult)

      const freshResult: VaRResultDto = {
        ...varResult,
        varValue: '999999.00',
        calculatedAt: '2025-01-15T11:00:00Z',
      }

      const error503 = new Error('Risk engine temporarily unavailable') as Error & { status: number }
      error503.status = 503

      mockTriggerVaR
        .mockRejectedValueOnce(error503)
        .mockResolvedValueOnce(freshResult)

      const { result } = renderHook(() => useVaR('book-1'))

      await waitFor(() => {
        expect(result.current.loading).toBe(false)
      })

      // Start refresh — first call will 503, retry after 5s should succeed
      let refreshPromise: Promise<void>
      act(() => {
        refreshPromise = result.current.refresh()
      })

      expect(result.current.refreshing).toBe(true)

      // Flush microtasks so the catch handler runs and setTimeout is registered
      await act(async () => {
        await Promise.resolve()
      })

      // Advance past the 5s delay
      await act(async () => {
        vi.advanceTimersByTime(5000)
      })

      await act(async () => {
        await refreshPromise!
      })

      expect(result.current.refreshing).toBe(false)
      expect(mockTriggerVaR).toHaveBeenCalledTimes(2)
      expect(result.current.error).toBeNull()
      expect(result.current.varResult).toEqual(freshResult)
    })

    it('shows error immediately when refresh returns 500 (no retry)', async () => {
      mockFetchVaR.mockResolvedValue(varResult)

      const error500 = new Error('Internal server error') as Error & { status: number }
      error500.status = 500

      mockTriggerVaR.mockRejectedValue(error500)

      const { result } = renderHook(() => useVaR('book-1'))

      await waitFor(() => {
        expect(result.current.loading).toBe(false)
      })

      await act(async () => {
        await result.current.refresh()
      })

      expect(result.current.error).toBe('Internal server error')
      expect(mockTriggerVaR).toHaveBeenCalledTimes(1)
    })

    it('shows error when 503 retry also fails', async () => {
      mockFetchVaR.mockResolvedValue(varResult)

      const error503 = new Error('Risk engine temporarily unavailable') as Error & { status: number }
      error503.status = 503

      mockTriggerVaR
        .mockRejectedValueOnce(error503)
        .mockRejectedValueOnce(error503)

      const { result } = renderHook(() => useVaR('book-1'))

      await waitFor(() => {
        expect(result.current.loading).toBe(false)
      })

      let refreshPromise: Promise<void>
      act(() => {
        refreshPromise = result.current.refresh()
      })

      // Flush microtasks so the catch handler runs and setTimeout is registered
      await act(async () => {
        await Promise.resolve()
      })

      // Advance past the 5s delay
      await act(async () => {
        vi.advanceTimersByTime(5000)
      })

      await act(async () => {
        await refreshPromise!
      })

      expect(result.current.refreshing).toBe(false)
      expect(mockTriggerVaR).toHaveBeenCalledTimes(2)
      expect(result.current.error).toBe('Risk engine temporarily unavailable')
    })
  })

  describe('preset switching and zoom lifecycle', () => {
    // Pin clock to a known instant so sliding-window presets resolve deterministically
    const NOW = new Date('2025-01-15T12:00:00Z')

    function makeResult(calculatedAt: string, value: string): VaRResultDto {
      return { ...varResult, calculatedAt, varValue: value }
    }

    // History entries deliberately spread across multiple time windows:
    //   5 days ago  — within Last 7d only
    //   6 hours ago — within Last 24h but NOT Last 1h
    //   2 hours ago — within Last 24h but NOT Last 1h
    //   30 min ago  — within Last 1h
    //   15 min ago  — within Last 1h
    const entries = [
      makeResult('2025-01-10T12:00:00Z', '100000'),
      makeResult('2025-01-15T06:00:00Z', '200000'),
      makeResult('2025-01-15T10:00:00Z', '300000'),
      makeResult('2025-01-15T11:30:00Z', '400000'),
      makeResult('2025-01-15T11:45:00Z', '500000'),
    ]

    async function loadAllEntries() {
      mockFetchVaR
        .mockResolvedValueOnce(entries[0])
        .mockResolvedValueOnce(entries[1])
        .mockResolvedValueOnce(entries[2])
        .mockResolvedValueOnce(entries[3])
        .mockResolvedValueOnce(entries[4])

      const hook = renderHook(() => useVaR('book-1'))

      await waitFor(() => {
        expect(hook.result.current.history).toHaveLength(1)
      })

      for (let i = 2; i <= 5; i++) {
        await act(async () => {
          vi.advanceTimersByTime(30_000)
        })
        await waitFor(() => {
          expect(hook.result.current.history).toHaveLength(i)
        })
      }

      return hook
    }

    it('Last 24h default shows entries within the last 24 hours', async () => {
      vi.setSystemTime(NOW)

      const { result } = await loadAllEntries()

      expect(result.current.history).toHaveLength(5)
      expect(result.current.timeRange.label).toBe('Last 24h')
      expect(result.current.filteredHistory).toHaveLength(4)
      expect(result.current.filteredHistory.map((e) => e.varValue)).toEqual([200000, 300000, 400000, 500000])
    })

    it('switching to Last 24h includes entries from 6h and 2h ago', async () => {
      vi.setSystemTime(NOW)

      const { result } = await loadAllEntries()

      act(() => {
        result.current.setTimeRange({
          from: new Date(NOW.getTime() - 24 * 60 * 60 * 1000).toISOString(),
          to: NOW.toISOString(),
          label: 'Last 24h',
        })
      })

      expect(result.current.filteredHistory).toHaveLength(4)
      expect(result.current.filteredHistory.map((e) => e.varValue)).toEqual([
        200000, 300000, 400000, 500000,
      ])
    })

    it('switching to Last 7d includes all entries', async () => {
      vi.setSystemTime(NOW)

      const { result } = await loadAllEntries()

      act(() => {
        result.current.setTimeRange({
          from: new Date(NOW.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString(),
          to: NOW.toISOString(),
          label: 'Last 7d',
        })
      })

      expect(result.current.filteredHistory).toHaveLength(5)
    })

    it('switching back to Last 1h narrows to only recent entries', async () => {
      vi.setSystemTime(NOW)

      const { result } = await loadAllEntries()

      // Widen to Last 7d
      act(() => {
        result.current.setTimeRange({
          from: new Date(NOW.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString(),
          to: NOW.toISOString(),
          label: 'Last 7d',
        })
      })

      expect(result.current.filteredHistory).toHaveLength(5)

      // Narrow back to Last 1h
      act(() => {
        result.current.setTimeRange({
          from: new Date(NOW.getTime() - 60 * 60 * 1000).toISOString(),
          to: NOW.toISOString(),
          label: 'Last 1h',
        })
      })

      expect(result.current.filteredHistory).toHaveLength(2)
      expect(result.current.filteredHistory.map((e) => e.varValue)).toEqual([400000, 500000])
    })

    it('zooming into a narrow Custom range filters to that window only', async () => {
      vi.setSystemTime(NOW)

      const { result } = await loadAllEntries()

      // Start at Last 24h
      act(() => {
        result.current.setTimeRange({
          from: new Date(NOW.getTime() - 24 * 60 * 60 * 1000).toISOString(),
          to: NOW.toISOString(),
          label: 'Last 24h',
        })
      })

      expect(result.current.filteredHistory).toHaveLength(4)

      // Zoom into a 1-hour window around 10:00 — only the 10:00 entry
      act(() => {
        result.current.zoomIn({
          from: '2025-01-15T09:30:00Z',
          to: '2025-01-15T10:30:00Z',
          label: 'Custom',
        })
      })

      expect(result.current.zoomDepth).toBe(1)
      expect(result.current.filteredHistory).toHaveLength(1)
      expect(result.current.filteredHistory[0].varValue).toBe(300000)
    })

    it('resetZoom after zoom restores the previous preset and its filtered entries', async () => {
      vi.setSystemTime(NOW)

      const { result } = await loadAllEntries()

      // Start at Last 24h (4 entries)
      act(() => {
        result.current.setTimeRange({
          from: new Date(NOW.getTime() - 24 * 60 * 60 * 1000).toISOString(),
          to: NOW.toISOString(),
          label: 'Last 24h',
        })
      })

      expect(result.current.filteredHistory).toHaveLength(4)

      // Zoom into a narrow window (1 entry)
      act(() => {
        result.current.zoomIn({
          from: '2025-01-15T09:30:00Z',
          to: '2025-01-15T10:30:00Z',
          label: 'Custom',
        })
      })

      expect(result.current.filteredHistory).toHaveLength(1)

      // Zoom even deeper (0 entries — empty window)
      act(() => {
        result.current.zoomIn({
          from: '2025-01-15T10:05:00Z',
          to: '2025-01-15T10:10:00Z',
          label: 'Custom',
        })
      })

      expect(result.current.zoomDepth).toBe(2)
      expect(result.current.filteredHistory).toHaveLength(0)

      // Reset zoom — should pop all the way back to Last 24h
      act(() => {
        result.current.resetZoom()
      })

      expect(result.current.zoomDepth).toBe(0)
      expect(result.current.timeRange.label).toBe('Last 24h')
      expect(result.current.filteredHistory).toHaveLength(4)
    })
  })

  describe('sliding window chart data refresh', () => {
    const NOW = new Date('2025-01-15T12:00:00Z')

    it('re-fetches chart data periodically for sliding presets in live mode', async () => {
      vi.setSystemTime(NOW)

      mockFetchVaR.mockResolvedValue(varResult)
      mockFetchHistory.mockResolvedValue({
        points: [
          { bucket: '2025-01-14T12:00:00Z', varValue: 100000, expectedShortfall: 120000, confidenceLevel: 'CL_95', delta: null, gamma: null, vega: null, theta: null, rho: null, pvValue: null, jobCount: 1, completedCount: 1, failedCount: 0, runningCount: 0 },
          { bucket: '2025-01-15T11:00:00Z', varValue: 200000, expectedShortfall: 240000, confidenceLevel: 'CL_95', delta: null, gamma: null, vega: null, theta: null, rho: null, pvValue: null, jobCount: 1, completedCount: 1, failedCount: 0, runningCount: 0 },
        ],
        bucketSizeMs: 900000,
      })

      renderHook(() => useVaR('book-1'))

      await waitFor(() => {
        expect(mockFetchHistory).toHaveBeenCalledTimes(1)
      })

      // Advance multiple poll intervals — chart data should be re-fetched
      await act(async () => {
        vi.advanceTimersByTime(30_000)
      })

      await waitFor(() => {
        expect(mockFetchHistory).toHaveBeenCalledTimes(2)
      })

      await act(async () => {
        vi.advanceTimersByTime(30_000)
      })

      await waitFor(() => {
        expect(mockFetchHistory).toHaveBeenCalledTimes(3)
      })
    })

    it('does not re-fetch chart data periodically in historical mode', async () => {
      vi.setSystemTime(NOW)

      mockFetchVaR.mockResolvedValue(varResult)
      mockFetchHistory.mockResolvedValue({ points: [], bucketSizeMs: 900000 })

      renderHook(() => useVaR('book-1', '2025-01-15'))

      await waitFor(() => {
        expect(mockFetchHistory).toHaveBeenCalledTimes(1)
      })

      await act(async () => {
        vi.advanceTimersByTime(60_000)
      })

      // Should still be 1 — no periodic re-fetch in historical mode
      expect(mockFetchHistory).toHaveBeenCalledTimes(1)
    })

    it('filteredHistory does not lose data at window start as time passes', async () => {
      vi.setSystemTime(NOW)

      mockFetchVaR.mockResolvedValue(varResult)
      // Chart data bucket near the start of the 24h window
      const nearStartBucket = new Date(NOW.getTime() - 23 * 60 * 60 * 1000).toISOString()
      const midBucket = new Date(NOW.getTime() - 2 * 60 * 60 * 1000).toISOString()
      mockFetchHistory.mockResolvedValue({
        points: [
          { bucket: nearStartBucket, varValue: 100000, expectedShortfall: 120000, confidenceLevel: 'CL_95', delta: null, gamma: null, vega: null, theta: null, rho: null, pvValue: null, jobCount: 1, completedCount: 1, failedCount: 0, runningCount: 0 },
          { bucket: midBucket, varValue: 200000, expectedShortfall: 240000, confidenceLevel: 'CL_95', delta: null, gamma: null, vega: null, theta: null, rho: null, pvValue: null, jobCount: 1, completedCount: 1, failedCount: 0, runningCount: 0 },
        ],
        bucketSizeMs: 900000,
      })

      const { result } = renderHook(() => useVaR('book-1'))

      // Wait for both chart data and polling VaR — chart data adds 2, polling adds 1
      await waitFor(() => {
        expect(result.current.filteredHistory.length).toBeGreaterThanOrEqual(2)
      })

      // Verify the near-start bucket is present
      expect(result.current.filteredHistory.some((e) => e.varValue === 100000)).toBe(true)

      // Advance time by 10 minutes — the start-of-window bucket should still be visible
      // because chart data has been re-fetched with a fresh window
      const freshNearStart = new Date(NOW.getTime() + 10 * 60 * 1000 - 23 * 60 * 60 * 1000).toISOString()
      mockFetchHistory.mockResolvedValue({
        points: [
          { bucket: freshNearStart, varValue: 100000, expectedShortfall: 120000, confidenceLevel: 'CL_95', delta: null, gamma: null, vega: null, theta: null, rho: null, pvValue: null, jobCount: 1, completedCount: 1, failedCount: 0, runningCount: 0 },
          { bucket: midBucket, varValue: 200000, expectedShortfall: 240000, confidenceLevel: 'CL_95', delta: null, gamma: null, vega: null, theta: null, rho: null, pvValue: null, jobCount: 1, completedCount: 1, failedCount: 0, runningCount: 0 },
        ],
        bucketSizeMs: 900000,
      })

      await act(async () => {
        vi.advanceTimersByTime(10 * 60 * 1000)
      })

      await waitFor(() => {
        // Near-start bucket should still be present — not filtered out by sliding window drift
        expect(result.current.filteredHistory.some((e) => e.varValue === 100000)).toBe(true)
      })
    })
  })

  describe('historyLoading', () => {
    it('is false immediately when bookId is null', () => {
      const { result } = renderHook(() => useVaR(null))

      expect(result.current.historyLoading).toBe(false)
    })

    it('is true on initial mount with a bookId before history resolves', () => {
      mockFetchVaR.mockReturnValue(new Promise(() => {}))
      mockFetchHistory.mockReturnValue(new Promise(() => {}) as never)

      const { result } = renderHook(() => useVaR('book-1'))

      expect(result.current.historyLoading).toBe(true)
    })

    it('goes false after loadHistory resolves successfully', async () => {
      mockFetchVaR.mockResolvedValue(null)
      mockFetchHistory.mockResolvedValue({ points: [], bucketSizeMs: 3600000 })

      const { result } = renderHook(() => useVaR('book-1'))

      await waitFor(() => {
        expect(result.current.historyLoading).toBe(false)
      })
    })

    it('goes false after loadHistory rejects (skeleton does not stay forever)', async () => {
      mockFetchVaR.mockResolvedValue(null)
      mockFetchHistory.mockRejectedValue(new Error('network failure') as never)

      const { result } = renderHook(() => useVaR('book-1'))

      await waitFor(() => {
        expect(result.current.historyLoading).toBe(false)
      })
    })
  })
})
