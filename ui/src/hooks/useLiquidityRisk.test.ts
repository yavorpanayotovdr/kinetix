import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useLiquidityRisk } from './useLiquidityRisk'

vi.mock('../api/liquidity', () => ({
  fetchLatestLiquidityRisk: vi.fn(),
  triggerLiquidityRiskCalculation: vi.fn(),
}))

import { fetchLatestLiquidityRisk, triggerLiquidityRiskCalculation } from '../api/liquidity'

const mockFetchLatest = vi.mocked(fetchLatestLiquidityRisk)
const mockTriggerCalc = vi.mocked(triggerLiquidityRiskCalculation)

const liquidityResult = {
  bookId: 'port-1',
  portfolioLvar: 316227.76,
  dataCompleteness: 0.85,
  portfolioConcentrationStatus: 'OK',
  calculatedAt: '2026-03-24T10:00:00Z',
  positionRisks: [],
}

describe('useLiquidityRisk', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('does not fetch on mount — requires explicit refresh call', () => {
    const { result } = renderHook(() => useLiquidityRisk('book-1'))

    expect(mockFetchLatest).not.toHaveBeenCalled()
    expect(result.current.result).toBeNull()
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('fetches latest liquidity risk when refresh is called without baseVar', async () => {
    mockFetchLatest.mockResolvedValue(liquidityResult)

    const { result } = renderHook(() => useLiquidityRisk('book-1'))

    await act(async () => {
      await result.current.refresh()
    })

    expect(mockFetchLatest).toHaveBeenCalledWith('book-1')
    expect(result.current.result).toEqual(liquidityResult)
    expect(result.current.error).toBeNull()
  })

  it('triggers calculation when refresh is called with baseVar', async () => {
    mockTriggerCalc.mockResolvedValue(liquidityResult)

    const { result } = renderHook(() => useLiquidityRisk('book-1'))

    await act(async () => {
      await result.current.refresh(500000)
    })

    expect(mockTriggerCalc).toHaveBeenCalledWith('book-1', 500000)
    expect(result.current.result).toEqual(liquidityResult)
  })

  it('sets error when fetch fails', async () => {
    mockFetchLatest.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useLiquidityRisk('book-1'))

    await act(async () => {
      await result.current.refresh()
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.result).toBeNull()
  })

  it('preserves previously loaded data when a refresh fails', async () => {
    mockFetchLatest.mockResolvedValue(liquidityResult)

    const { result } = renderHook(() => useLiquidityRisk('book-1'))

    await act(async () => {
      await result.current.refresh()
    })

    expect(result.current.result).toEqual(liquidityResult)

    // Now fail on the next refresh
    mockFetchLatest.mockRejectedValue(new Error('Refresh failed'))

    await act(async () => {
      await result.current.refresh()
    })

    expect(result.current.error).toBe('Refresh failed')
    // Data should be preserved — not cleared on error
    expect(result.current.result).toEqual(liquidityResult)
  })

  it('does not fetch when bookId is null', async () => {
    const { result } = renderHook(() => useLiquidityRisk(null))

    await act(async () => {
      await result.current.refresh()
    })

    expect(mockFetchLatest).not.toHaveBeenCalled()
    expect(result.current.result).toBeNull()
  })

  it('shows loading state during fetch', async () => {
    let resolvePromise!: (value: typeof liquidityResult) => void
    mockFetchLatest.mockReturnValueOnce(
      new Promise((resolve) => { resolvePromise = resolve }),
    )

    const { result } = renderHook(() => useLiquidityRisk('book-1'))

    act(() => {
      void result.current.refresh()
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(true)
    })

    await act(async () => {
      resolvePromise(liquidityResult)
    })

    expect(result.current.loading).toBe(false)
    expect(result.current.result).toEqual(liquidityResult)
  })
})
