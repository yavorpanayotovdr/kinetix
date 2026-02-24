import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/system')

import { useSystemHealth } from './useSystemHealth'
import { fetchSystemHealth } from '../api/system'

const mockFetch = vi.mocked(fetchSystemHealth)

const healthyResponse = {
  status: 'UP' as const,
  services: {
    gateway: { status: 'UP' as const },
    'position-service': { status: 'UP' as const },
    'price-service': { status: 'UP' as const },
    'risk-orchestrator': { status: 'UP' as const },
    'notification-service': { status: 'UP' as const },
  },
}

describe('useSystemHealth', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.resetAllMocks()
    mockFetch.mockResolvedValue(healthyResponse)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('fetches health on mount', async () => {
    const { result } = renderHook(() => useSystemHealth())

    expect(result.current.loading).toBe(true)

    // Flush the initial promise (advance by 0 to flush microtasks)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(result.current.loading).toBe(false)
    expect(result.current.health).toEqual(healthyResponse)
    expect(result.current.error).toBeNull()
    expect(mockFetch).toHaveBeenCalledTimes(1)
  })

  it('auto-refreshes every 30 seconds', async () => {
    const { result } = renderHook(() => useSystemHealth())

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(result.current.loading).toBe(false)
    expect(mockFetch).toHaveBeenCalledTimes(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })

    expect(mockFetch).toHaveBeenCalledTimes(2)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })

    expect(mockFetch).toHaveBeenCalledTimes(3)
  })

  it('sets error on failure', async () => {
    mockFetch.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useSystemHealth())

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBe('Network error')
    expect(result.current.health).toBeNull()
  })

  it('refresh triggers manual fetch', async () => {
    const { result } = renderHook(() => useSystemHealth())

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(mockFetch).toHaveBeenCalledTimes(1)

    await act(async () => {
      result.current.refresh()
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(mockFetch).toHaveBeenCalledTimes(2)
  })

  it('clears interval on unmount', async () => {
    const { result, unmount } = renderHook(() => useSystemHealth())

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(result.current.loading).toBe(false)

    unmount()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000)
    })

    // Only the initial call, no interval calls after unmount
    expect(mockFetch).toHaveBeenCalledTimes(1)
  })
})
