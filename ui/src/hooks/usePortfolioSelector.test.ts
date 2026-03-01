import { renderHook, act, waitFor } from '@testing-library/react'
import { describe, expect, it, beforeEach, vi } from 'vitest'
import { usePortfolioSelector } from './usePortfolioSelector'

vi.mock('../api/positions', () => ({
  fetchPortfolios: vi.fn(),
  fetchPositions: vi.fn(),
}))

import { fetchPortfolios, fetchPositions } from '../api/positions'

const mockFetchPortfolios = vi.mocked(fetchPortfolios)
const mockFetchPositions = vi.mocked(fetchPositions)

describe('usePortfolioSelector', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('should include All Portfolios option when multiple portfolios exist', async () => {
    mockFetchPortfolios.mockResolvedValue([
      { portfolioId: 'port-1' },
      { portfolioId: 'port-2' },
    ])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => usePortfolioSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.portfolioOptions).toContainEqual({
      value: '__ALL__',
      label: 'All Portfolios',
    })
    expect(result.current.portfolioOptions).toContainEqual({
      value: 'port-1',
      label: 'port-1',
    })
    expect(result.current.portfolioOptions).toContainEqual({
      value: 'port-2',
      label: 'port-2',
    })
  })

  it('should not include All Portfolios option when only one portfolio exists', async () => {
    mockFetchPortfolios.mockResolvedValue([
      { portfolioId: 'port-1' },
    ])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => usePortfolioSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.portfolioOptions).not.toContainEqual(
      expect.objectContaining({ value: '__ALL__' }),
    )
  })

  it('should set isAllSelected to true when All Portfolios is selected', async () => {
    mockFetchPortfolios.mockResolvedValue([
      { portfolioId: 'port-1' },
      { portfolioId: 'port-2' },
    ])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => usePortfolioSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.selectPortfolio('__ALL__')
    })

    expect(result.current.isAllSelected).toBe(true)
    expect(result.current.selectedPortfolioId).toBe('__ALL__')
  })

  it('should aggregate positions when All Portfolios is selected', async () => {
    mockFetchPortfolios.mockResolvedValue([
      { portfolioId: 'port-1' },
      { portfolioId: 'port-2' },
    ])
    mockFetchPositions
      .mockResolvedValueOnce([]) // initial load for port-1
      .mockResolvedValueOnce([
        {
          portfolioId: 'port-1',
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          quantity: '100',
          averageCost: { amount: '150.00', currency: 'USD' },
          marketPrice: { amount: '155.00', currency: 'USD' },
          marketValue: { amount: '15500.00', currency: 'USD' },
          unrealizedPnl: { amount: '500.00', currency: 'USD' },
        },
      ])
      .mockResolvedValueOnce([
        {
          portfolioId: 'port-2',
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          quantity: '50',
          averageCost: { amount: '145.00', currency: 'USD' },
          marketPrice: { amount: '155.00', currency: 'USD' },
          marketValue: { amount: '7750.00', currency: 'USD' },
          unrealizedPnl: { amount: '500.00', currency: 'USD' },
        },
      ])

    const { result } = renderHook(() => usePortfolioSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    await act(async () => {
      result.current.selectPortfolio('__ALL__')
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.aggregatedPositions).toHaveLength(1)
    expect(result.current.aggregatedPositions[0].instrumentId).toBe('AAPL')
    expect(result.current.aggregatedPositions[0].quantity).toBe('150')
  })

  it('should return individual portfolio IDs list when All Portfolios selected', async () => {
    mockFetchPortfolios.mockResolvedValue([
      { portfolioId: 'port-1' },
      { portfolioId: 'port-2' },
    ])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => usePortfolioSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.selectPortfolio('__ALL__')
    })

    expect(result.current.allPortfolioIds).toEqual(['port-1', 'port-2'])
  })
})
