import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePositionRisk } from './usePositionRisk'

vi.mock('../api/risk', () => ({
  fetchVaR: vi.fn(),
  triggerVaRCalculation: vi.fn(),
  fetchPositionRisk: vi.fn(),
}))

import { fetchPositionRisk } from '../api/risk'

const mockFetchPositionRisk = vi.mocked(fetchPositionRisk)

const positionRiskData = [
  {
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    marketValue: '15500.00',
    delta: '1234.56',
    gamma: '45.67',
    vega: '89.01',
    theta: null,
    rho: null,
    varContribution: '800.00',
    esContribution: '1000.00',
    percentageOfTotal: '64.85',
  },
  {
    instrumentId: 'MSFT',
    assetClass: 'EQUITY',
    marketValue: '10000.00',
    delta: '567.89',
    gamma: '23.45',
    vega: '34.56',
    theta: null,
    rho: null,
    varContribution: '433.00',
    esContribution: '550.00',
    percentageOfTotal: '35.15',
  },
]

describe('usePositionRisk', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('fetches position risk data on mount when bookId is provided', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result } = renderHook(() => usePositionRisk('book-1'))

    expect(result.current.loading).toBe(true)

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPositionRisk).toHaveBeenCalledWith('book-1', null)
    expect(result.current.positionRisk).toEqual(positionRiskData)
    expect(result.current.error).toBeNull()
  })

  it('does not fetch when bookId is null', () => {
    const { result } = renderHook(() => usePositionRisk(null))

    expect(mockFetchPositionRisk).not.toHaveBeenCalled()
    expect(result.current.positionRisk).toEqual([])
    expect(result.current.loading).toBe(false)
  })

  it('sets error on fetch failure when no prior data exists', async () => {
    mockFetchPositionRisk.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => usePositionRisk('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.positionRisk).toEqual([])
  })

  it('preserves previously loaded data when a refresh fails', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result } = renderHook(() => usePositionRisk('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.positionRisk).toEqual(positionRiskData)

    // Now fail on refresh
    mockFetchPositionRisk.mockRejectedValue(new Error('Refresh failed'))

    await act(async () => {
      await result.current.refresh()
    })

    expect(result.current.error).toBe('Refresh failed')
    // Data should NOT be cleared — stale data is preserved for the user
    expect(result.current.positionRisk).toEqual(positionRiskData)
  })

  it('clears data when bookId changes to a new book', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result, rerender } = renderHook(
      ({ bookId }) => usePositionRisk(bookId),
      { initialProps: { bookId: 'book-1' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.positionRisk).toEqual(positionRiskData)

    // Change book — stale data from the previous book should be cleared
    mockFetchPositionRisk.mockRejectedValue(new Error('Fetch failed'))
    rerender({ bookId: 'book-2' })

    await waitFor(() => {
      expect(mockFetchPositionRisk).toHaveBeenCalledWith('book-2', null)
    })

    // On initial load for a new book, data should be empty
    expect(result.current.positionRisk).toEqual([])
  })

  it('re-fetches when bookId changes', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result, rerender } = renderHook(
      ({ bookId }) => usePositionRisk(bookId),
      { initialProps: { bookId: 'book-1' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPositionRisk).toHaveBeenCalledWith('book-1', null)

    mockFetchPositionRisk.mockResolvedValue([])
    rerender({ bookId: 'book-2' })

    await waitFor(() => {
      expect(mockFetchPositionRisk).toHaveBeenCalledWith('book-2', null)
    })
  })

  it('passes valuationDate to fetchPositionRisk when provided', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result } = renderHook(() => usePositionRisk('book-1', '2025-03-10'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPositionRisk).toHaveBeenCalledWith('book-1', '2025-03-10')
  })

  it('passes null valuationDate to fetchPositionRisk when omitted', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result } = renderHook(() => usePositionRisk('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPositionRisk).toHaveBeenCalledWith('book-1', null)
  })

  it('re-fetches when valuationDate changes', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result, rerender } = renderHook(
      ({ bookId, valuationDate }) => usePositionRisk(bookId, valuationDate),
      { initialProps: { bookId: 'book-1' as string | null, valuationDate: null as string | null } },
    )

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPositionRisk).toHaveBeenCalledTimes(1)

    mockFetchPositionRisk.mockResolvedValue([])
    rerender({ bookId: 'book-1', valuationDate: '2025-03-10' })

    await waitFor(() => {
      expect(mockFetchPositionRisk).toHaveBeenCalledWith('book-1', '2025-03-10')
    })
  })

  it('refreshes data when refresh is called', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result } = renderHook(() => usePositionRisk('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPositionRisk).toHaveBeenCalledTimes(1)

    const updatedData = [{ ...positionRiskData[0], varContribution: '900.00' }]
    mockFetchPositionRisk.mockResolvedValue(updatedData)

    await act(async () => {
      await result.current.refresh()
    })

    expect(mockFetchPositionRisk).toHaveBeenCalledTimes(2)
    expect(result.current.positionRisk).toEqual(updatedData)
  })

  it('does not flash loading state during refresh', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result } = renderHook(() => usePositionRisk('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.positionRisk).toEqual(positionRiskData)

    const updatedData = [{ ...positionRiskData[0], varContribution: '900.00' }]
    let resolveRefresh!: (value: typeof positionRiskData) => void
    mockFetchPositionRisk.mockReturnValueOnce(new Promise((resolve) => { resolveRefresh = resolve }))

    let refreshPromise: Promise<void>
    act(() => {
      refreshPromise = result.current.refresh()
    })

    // Loading stays false — existing data remains visible during refresh
    expect(result.current.loading).toBe(false)
    expect(result.current.positionRisk).toEqual(positionRiskData)

    await act(async () => {
      resolveRefresh(updatedData)
      await refreshPromise!
    })

    expect(result.current.positionRisk).toEqual(updatedData)
  })
})
