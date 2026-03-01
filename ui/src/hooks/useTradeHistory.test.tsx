import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { TradeHistoryDto } from '../types'

vi.mock('../api/tradeHistory')

import { fetchTradeHistory } from '../api/tradeHistory'
import { useTradeHistory } from './useTradeHistory'

const mockFetchTradeHistory = vi.mocked(fetchTradeHistory)

const trade: TradeHistoryDto = {
  tradeId: 't-1',
  portfolioId: 'port-1',
  instrumentId: 'AAPL',
  assetClass: 'EQUITY',
  side: 'BUY',
  quantity: '100',
  price: { amount: '150.00', currency: 'USD' },
  tradedAt: '2025-01-15T10:00:00Z',
}

describe('useTradeHistory', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('does not fetch when portfolioId is null', () => {
    renderHook(() => useTradeHistory(null))

    expect(mockFetchTradeHistory).not.toHaveBeenCalled()
  })

  it('fetches trade history on mount', async () => {
    mockFetchTradeHistory.mockResolvedValue([trade])

    const { result } = renderHook(() => useTradeHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.trades).toEqual([trade])
    expect(result.current.error).toBeNull()
    expect(mockFetchTradeHistory).toHaveBeenCalledWith('port-1')
  })

  it('sets error on fetch failure', async () => {
    mockFetchTradeHistory.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useTradeHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.trades).toEqual([])
  })

  it('refetches when refetch is called', async () => {
    mockFetchTradeHistory.mockResolvedValue([trade])

    const { result } = renderHook(() => useTradeHistory('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const trade2: TradeHistoryDto = { ...trade, tradeId: 't-2' }
    mockFetchTradeHistory.mockResolvedValue([trade, trade2])

    await act(async () => {
      result.current.refetch()
    })

    await waitFor(() => {
      expect(result.current.trades).toHaveLength(2)
    })
  })

  it('refetches when portfolioId changes', async () => {
    mockFetchTradeHistory.mockResolvedValue([trade])

    const { result, rerender } = renderHook(
      ({ id }: { id: string | null }) => useTradeHistory(id),
      { initialProps: { id: 'port-1' } },
    )

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const trade2: TradeHistoryDto = { ...trade, portfolioId: 'port-2', tradeId: 't-2' }
    mockFetchTradeHistory.mockResolvedValue([trade2])

    rerender({ id: 'port-2' })

    await waitFor(() => {
      expect(result.current.trades).toEqual([trade2])
    })

    expect(mockFetchTradeHistory).toHaveBeenCalledWith('port-2')
  })
})
