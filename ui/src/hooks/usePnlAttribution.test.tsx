import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePnlAttribution } from './usePnlAttribution'

vi.mock('../api/pnlAttribution', () => ({
  fetchPnlAttribution: vi.fn(),
}))

import { fetchPnlAttribution } from '../api/pnlAttribution'

const mockFetchPnlAttribution = vi.mocked(fetchPnlAttribution)

const pnlAttributionData = {
  portfolioId: 'port-1',
  date: '2025-01-15',
  totalPnl: '15000.00',
  deltaPnl: '8000.00',
  gammaPnl: '2500.00',
  vegaPnl: '3000.00',
  thetaPnl: '-1500.00',
  rhoPnl: '500.00',
  unexplainedPnl: '2500.00',
  positionAttributions: [
    {
      instrumentId: 'AAPL',
      assetClass: 'EQUITY',
      totalPnl: '8000.00',
      deltaPnl: '5000.00',
      gammaPnl: '1200.00',
      vegaPnl: '1500.00',
      thetaPnl: '-800.00',
      rhoPnl: '300.00',
      unexplainedPnl: '800.00',
    },
  ],
  calculatedAt: '2025-01-15T10:30:00Z',
}

describe('usePnlAttribution', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('fetches P&L attribution data on mount when portfolioId is provided', async () => {
    mockFetchPnlAttribution.mockResolvedValue(pnlAttributionData)

    const { result } = renderHook(() => usePnlAttribution('port-1'))

    expect(result.current.loading).toBe(true)

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPnlAttribution).toHaveBeenCalledWith('port-1', undefined)
    expect(result.current.data).toEqual(pnlAttributionData)
    expect(result.current.error).toBeNull()
  })

  it('does not fetch when portfolioId is null', () => {
    const { result } = renderHook(() => usePnlAttribution(null))

    expect(mockFetchPnlAttribution).not.toHaveBeenCalled()
    expect(result.current.data).toBeNull()
    expect(result.current.loading).toBe(false)
  })

  it('sets error on fetch failure', async () => {
    mockFetchPnlAttribution.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => usePnlAttribution('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.data).toBeNull()
  })

  it('re-fetches when portfolioId changes', async () => {
    mockFetchPnlAttribution.mockResolvedValue(pnlAttributionData)

    const { result, rerender } = renderHook(
      ({ portfolioId }) => usePnlAttribution(portfolioId),
      { initialProps: { portfolioId: 'port-1' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPnlAttribution).toHaveBeenCalledWith('port-1', undefined)

    mockFetchPnlAttribution.mockResolvedValue(null)
    rerender({ portfolioId: 'port-2' })

    await waitFor(() => {
      expect(mockFetchPnlAttribution).toHaveBeenCalledWith('port-2', undefined)
    })
  })

  it('handles null response from API', async () => {
    mockFetchPnlAttribution.mockResolvedValue(null)

    const { result } = renderHook(() => usePnlAttribution('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.data).toBeNull()
    expect(result.current.error).toBeNull()
  })
})
