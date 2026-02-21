import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { VaRResultDto } from '../types'

vi.mock('../api/risk')

import { fetchVaR } from '../api/risk'
import { useVaR } from './useVaR'

const mockFetchVaR = vi.mocked(fetchVaR)

const varResult: VaRResultDto = {
  portfolioId: 'port-1',
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

  it('does nothing when portfolioId is null', () => {
    const { result } = renderHook(() => useVaR(null))

    expect(result.current.varResult).toBeNull()
    expect(result.current.history).toEqual([])
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
    expect(mockFetchVaR).not.toHaveBeenCalled()
  })

  it('starts in loading state on initial fetch', () => {
    mockFetchVaR.mockReturnValue(new Promise(() => {}))

    const { result } = renderHook(() => useVaR('port-1'))

    expect(result.current.loading).toBe(true)
    expect(result.current.varResult).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('fetches VaR result on mount', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.varResult).toEqual(varResult)
    expect(result.current.error).toBeNull()
    expect(mockFetchVaR).toHaveBeenCalledWith('port-1')
  })

  it('sets error on fetch failure', async () => {
    mockFetchVaR.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.varResult).toBeNull()
  })

  it('handles null result (404)', async () => {
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.varResult).toBeNull()
    expect(result.current.history).toEqual([])
    expect(result.current.error).toBeNull()
  })

  it('appends to history on successful fetch', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toEqual([
      {
        varValue: 1234567.89,
        expectedShortfall: 1567890.12,
        calculatedAt: '2025-01-15T10:30:00Z',
      },
    ])
  })

  it('deduplicates history by calculatedAt', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('port-1'))

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

    const { result } = renderHook(() => useVaR('port-1'))

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
    })
  })

  it('polls every 30 seconds', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    renderHook(() => useVaR('port-1'))

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

  it('refresh triggers immediate re-fetch', async () => {
    mockFetchVaR.mockResolvedValue(varResult)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchVaR).toHaveBeenCalledTimes(1)

    await act(async () => {
      result.current.refresh()
    })

    await waitFor(() => {
      expect(mockFetchVaR).toHaveBeenCalledTimes(2)
    })
  })
})
