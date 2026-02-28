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

  it('fetches position risk data on mount when portfolioId is provided', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result } = renderHook(() => usePositionRisk('port-1'))

    expect(result.current.loading).toBe(true)

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPositionRisk).toHaveBeenCalledWith('port-1')
    expect(result.current.positionRisk).toEqual(positionRiskData)
    expect(result.current.error).toBeNull()
  })

  it('does not fetch when portfolioId is null', () => {
    const { result } = renderHook(() => usePositionRisk(null))

    expect(mockFetchPositionRisk).not.toHaveBeenCalled()
    expect(result.current.positionRisk).toEqual([])
    expect(result.current.loading).toBe(false)
  })

  it('sets error on fetch failure', async () => {
    mockFetchPositionRisk.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => usePositionRisk('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.positionRisk).toEqual([])
  })

  it('re-fetches when portfolioId changes', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result, rerender } = renderHook(
      ({ portfolioId }) => usePositionRisk(portfolioId),
      { initialProps: { portfolioId: 'port-1' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchPositionRisk).toHaveBeenCalledWith('port-1')

    mockFetchPositionRisk.mockResolvedValue([])
    rerender({ portfolioId: 'port-2' })

    await waitFor(() => {
      expect(mockFetchPositionRisk).toHaveBeenCalledWith('port-2')
    })
  })

  it('refreshes data when refresh is called', async () => {
    mockFetchPositionRisk.mockResolvedValue(positionRiskData)

    const { result } = renderHook(() => usePositionRisk('port-1'))

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
})
