import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AlertEventDto } from '../types'

vi.mock('../api/notifications', () => ({
  fetchAlerts: vi.fn(),
}))

import { fetchAlerts } from '../api/notifications'
import { useAlerts } from './useAlerts'

const mockFetchAlerts = vi.mocked(fetchAlerts)

const alertFixtures: AlertEventDto[] = [
  {
    id: 'alert-1',
    ruleId: 'rule-1',
    ruleName: 'VaR Limit',
    type: 'VAR_BREACH',
    severity: 'CRITICAL',
    message: 'VaR exceeds limit by 15%',
    currentValue: 2300000,
    threshold: 2000000,
    portfolioId: 'port-1',
    triggeredAt: '2026-02-28T10:00:00Z',
  },
  {
    id: 'alert-2',
    ruleId: 'rule-2',
    ruleName: 'Concentration',
    type: 'CONCENTRATION',
    severity: 'WARNING',
    message: 'Single position exceeds 30% of portfolio',
    currentValue: 35,
    threshold: 30,
    portfolioId: 'port-1',
    triggeredAt: '2026-02-28T09:55:00Z',
  },
]

describe('useAlerts', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('fetches alerts on mount', async () => {
    mockFetchAlerts.mockResolvedValue(alertFixtures)

    const { result } = renderHook(() => useAlerts())

    await waitFor(() => {
      expect(result.current.alerts).toEqual(alertFixtures)
    })

    expect(mockFetchAlerts).toHaveBeenCalledWith(5)
  })

  it('returns empty array initially before fetch completes', () => {
    mockFetchAlerts.mockResolvedValue(alertFixtures)

    const { result } = renderHook(() => useAlerts())

    expect(result.current.alerts).toEqual([])
  })

  it('polls every 30 seconds', async () => {
    mockFetchAlerts.mockResolvedValue(alertFixtures)

    renderHook(() => useAlerts())

    await waitFor(() => {
      expect(mockFetchAlerts).toHaveBeenCalledTimes(1)
    })

    await act(async () => {
      vi.advanceTimersByTime(30000)
    })

    await waitFor(() => {
      expect(mockFetchAlerts).toHaveBeenCalledTimes(2)
    })

    await act(async () => {
      vi.advanceTimersByTime(30000)
    })

    await waitFor(() => {
      expect(mockFetchAlerts).toHaveBeenCalledTimes(3)
    })
  })

  it('dismisses an alert by id from local state', async () => {
    mockFetchAlerts.mockResolvedValue(alertFixtures)

    const { result } = renderHook(() => useAlerts())

    await waitFor(() => {
      expect(result.current.alerts).toHaveLength(2)
    })

    act(() => {
      result.current.dismissAlert('alert-1')
    })

    expect(result.current.alerts).toHaveLength(1)
    expect(result.current.alerts[0].id).toBe('alert-2')
  })

  it('keeps dismissed alerts hidden after re-fetch', async () => {
    mockFetchAlerts.mockResolvedValue(alertFixtures)

    const { result } = renderHook(() => useAlerts())

    await waitFor(() => {
      expect(result.current.alerts).toHaveLength(2)
    })

    act(() => {
      result.current.dismissAlert('alert-1')
    })

    expect(result.current.alerts).toHaveLength(1)

    await act(async () => {
      vi.advanceTimersByTime(30000)
    })

    await waitFor(() => {
      expect(mockFetchAlerts).toHaveBeenCalledTimes(2)
    })

    expect(result.current.alerts).toHaveLength(1)
    expect(result.current.alerts[0].id).toBe('alert-2')
  })

  it('clears interval on unmount', async () => {
    mockFetchAlerts.mockResolvedValue([])

    const { unmount } = renderHook(() => useAlerts())

    await waitFor(() => {
      expect(mockFetchAlerts).toHaveBeenCalledTimes(1)
    })

    unmount()

    await act(async () => {
      vi.advanceTimersByTime(30000)
    })

    expect(mockFetchAlerts).toHaveBeenCalledTimes(1)
  })

  it('handles fetch errors gracefully', async () => {
    mockFetchAlerts.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useAlerts())

    await waitFor(() => {
      expect(mockFetchAlerts).toHaveBeenCalledTimes(1)
    })

    expect(result.current.alerts).toEqual([])
  })
})
