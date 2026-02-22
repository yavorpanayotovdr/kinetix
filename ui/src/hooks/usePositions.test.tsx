import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PortfolioDto, PositionDto } from '../types'

vi.mock('../api/positions')

import { fetchPortfolios, fetchPositions } from '../api/positions'
import { usePositions } from './usePositions'

const mockFetchPortfolios = vi.mocked(fetchPortfolios)
const mockFetchPositions = vi.mocked(fetchPositions)

const position: PositionDto = {
  portfolioId: 'port-1',
  instrumentId: 'AAPL',
  assetClass: 'EQUITY',
  quantity: '100',
  averageCost: { amount: '150.00', currency: 'USD' },
  marketPrice: { amount: '155.00', currency: 'USD' },
  marketValue: { amount: '15500.00', currency: 'USD' },
  unrealizedPnl: { amount: '500.00', currency: 'USD' },
}

describe('usePositions', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('starts in loading state', () => {
    mockFetchPortfolios.mockReturnValue(new Promise(() => {}))

    const { result } = renderHook(() => usePositions())

    expect(result.current.loading).toBe(true)
    expect(result.current.positions).toEqual([])
    expect(result.current.portfolioId).toBeNull()
    expect(result.current.portfolios).toEqual([])
    expect(result.current.error).toBeNull()
  })

  it('loads positions from first portfolio and exposes portfolio list', async () => {
    const portfolios: PortfolioDto[] = [
      { portfolioId: 'port-1' },
      { portfolioId: 'port-2' },
    ]
    mockFetchPortfolios.mockResolvedValue(portfolios)
    mockFetchPositions.mockResolvedValue([position])

    const { result } = renderHook(() => usePositions())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.positions).toEqual([position])
    expect(result.current.portfolioId).toBe('port-1')
    expect(result.current.portfolios).toEqual(['port-1', 'port-2'])
    expect(result.current.error).toBeNull()
    expect(mockFetchPositions).toHaveBeenCalledWith('port-1')
  })

  it('returns empty positions when no portfolios exist', async () => {
    mockFetchPortfolios.mockResolvedValue([])

    const { result } = renderHook(() => usePositions())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.positions).toEqual([])
    expect(result.current.portfolioId).toBeNull()
    expect(result.current.portfolios).toEqual([])
    expect(mockFetchPositions).not.toHaveBeenCalled()
  })

  it('sets error on fetch failure', async () => {
    mockFetchPortfolios.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => usePositions())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.positions).toEqual([])
  })

  it('selectPortfolio switches portfolio and reloads positions', async () => {
    const portfolios: PortfolioDto[] = [
      { portfolioId: 'port-1' },
      { portfolioId: 'port-2' },
    ]
    mockFetchPortfolios.mockResolvedValue(portfolios)
    mockFetchPositions.mockResolvedValue([position])

    const { result } = renderHook(() => usePositions())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const position2: PositionDto = { ...position, portfolioId: 'port-2', instrumentId: 'MSFT' }
    mockFetchPositions.mockResolvedValue([position2])

    await act(async () => {
      result.current.selectPortfolio('port-2')
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.portfolioId).toBe('port-2')
    expect(result.current.positions).toEqual([position2])
    expect(mockFetchPositions).toHaveBeenCalledWith('port-2')
  })
})
