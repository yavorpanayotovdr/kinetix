import { renderHook, act } from '@testing-library/react'
import { describe, expect, it, beforeEach, vi, afterEach } from 'vitest'
import { useDataQuality } from './useDataQuality'

vi.mock('../api/dataQuality', () => ({
  fetchDataQualityStatus: vi.fn(),
}))

import { fetchDataQualityStatus } from '../api/dataQuality'

const mockFetch = vi.mocked(fetchDataQualityStatus)

describe('useDataQuality', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('fetches data quality status on mount', async () => {
    mockFetch.mockResolvedValue({
      overall: 'OK',
      checks: [
        { name: 'Price Freshness', status: 'OK', message: 'All fresh', lastChecked: '2025-01-15T10:00:00Z' },
      ],
    })

    const { result } = renderHook(() => useDataQuality())

    expect(result.current.loading).toBe(true)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1)
    })

    expect(result.current.loading).toBe(false)
    expect(result.current.status).not.toBeNull()
    expect(result.current.status!.overall).toBe('OK')
    expect(mockFetch).toHaveBeenCalledTimes(1)
  })

  it('polls data quality status periodically', async () => {
    mockFetch.mockResolvedValue({
      overall: 'OK',
      checks: [],
    })

    renderHook(() => useDataQuality())

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1)
    })
    expect(mockFetch).toHaveBeenCalledTimes(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })
    expect(mockFetch).toHaveBeenCalledTimes(2)
  })

  it('handles fetch error gracefully', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useDataQuality())

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1)
    })

    expect(result.current.loading).toBe(false)
    expect(result.current.status).toBeNull()
    expect(result.current.error).toBe('Network error')
  })

  it('sets a synthetic CRITICAL status when fetch fails so the indicator remains visible', async () => {
    mockFetch.mockRejectedValue(new Error('Service down'))

    const { result } = renderHook(() => useDataQuality())

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1)
    })

    expect(result.current.syntheticStatus).not.toBeNull()
    expect(result.current.syntheticStatus!.overall).toBe('CRITICAL')
    expect(result.current.syntheticStatus!.checks).toHaveLength(1)
    expect(result.current.syntheticStatus!.checks[0].name).toBe('Data Quality Monitoring')
    expect(result.current.syntheticStatus!.checks[0].status).toBe('CRITICAL')
    expect(result.current.syntheticStatus!.checks[0].message).toBe('Monitoring unavailable')
  })

  it('clears syntheticStatus when a subsequent fetch succeeds', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Transient error'))

    const successStatus = {
      overall: 'OK' as const,
      checks: [
        { name: 'Price Freshness', status: 'OK' as const, message: 'All fresh', lastChecked: '2025-01-15T10:00:00Z' },
      ],
    }
    mockFetch.mockResolvedValue(successStatus)

    const { result } = renderHook(() => useDataQuality())

    // First poll — fails
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1)
    })

    expect(result.current.syntheticStatus).not.toBeNull()

    // Second poll — succeeds
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })

    expect(result.current.syntheticStatus).toBeNull()
    expect(result.current.status).toEqual(successStatus)
  })
})
