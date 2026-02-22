import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { GreeksResultDto } from '../types'

vi.mock('../api/stress')

import { fetchGreeks } from '../api/stress'
import { useGreeks } from './useGreeks'

const mockFetchGreeks = vi.mocked(fetchGreeks)

const greeksResult: GreeksResultDto = {
  portfolioId: 'port-1',
  assetClassGreeks: [
    { assetClass: 'EQUITY', delta: '0.85', gamma: '0.02', vega: '15000' },
  ],
  theta: '-5000.1234',
  rho: '2500.5678',
  calculatedAt: '2025-01-15T10:30:00Z',
}

describe('useGreeks', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('loads greeks on mount when portfolioId is provided', async () => {
    mockFetchGreeks.mockResolvedValue(greeksResult)

    const { result } = renderHook(() => useGreeks('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.greeksResult).toEqual(greeksResult)
    expect(result.current.error).toBeNull()
    expect(result.current.volBump).toBe(0)
    expect(mockFetchGreeks).toHaveBeenCalledWith('port-1')
  })

  it('does not fetch when portfolioId is null', async () => {
    const { result } = renderHook(() => useGreeks(null))

    expect(result.current.greeksResult).toBeNull()
    expect(result.current.loading).toBe(false)
    expect(mockFetchGreeks).not.toHaveBeenCalled()
  })

  it('refetches when portfolioId changes', async () => {
    mockFetchGreeks.mockResolvedValue(greeksResult)

    const { result, rerender } = renderHook(
      ({ portfolioId }) => useGreeks(portfolioId),
      { initialProps: { portfolioId: 'port-1' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const greeksResult2: GreeksResultDto = { ...greeksResult, portfolioId: 'port-2' }
    mockFetchGreeks.mockResolvedValue(greeksResult2)

    rerender({ portfolioId: 'port-2' })

    await waitFor(() => {
      expect(result.current.greeksResult).toEqual(greeksResult2)
    })

    expect(mockFetchGreeks).toHaveBeenCalledWith('port-2')
  })

  it('sets error on fetch failure', async () => {
    mockFetchGreeks.mockRejectedValue(new Error('Greeks failed'))

    const { result } = renderHook(() => useGreeks('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Greeks failed')
    expect(result.current.greeksResult).toBeNull()
  })
})
