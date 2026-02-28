import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/notifications')

import { fetchRules } from '../api/notifications'
import { useVarLimit } from './useVarLimit'

const mockFetchRules = vi.mocked(fetchRules)

describe('useVarLimit', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('returns the threshold of the first enabled VAR_BREACH rule', async () => {
    mockFetchRules.mockResolvedValue([
      {
        id: 'rule-1',
        name: 'VaR Breach',
        type: 'VAR_BREACH',
        threshold: 2000000,
        operator: 'GREATER_THAN',
        severity: 'CRITICAL',
        channels: ['IN_APP'],
        enabled: true,
      },
    ])

    const { result } = renderHook(() => useVarLimit())

    await waitFor(() => {
      expect(result.current.varLimit).toBe(2000000)
    })
  })

  it('returns null when no VAR_BREACH rule exists', async () => {
    mockFetchRules.mockResolvedValue([
      {
        id: 'rule-2',
        name: 'ES Breach',
        type: 'ES_BREACH',
        threshold: 3000000,
        operator: 'GREATER_THAN',
        severity: 'WARNING',
        channels: ['IN_APP'],
        enabled: true,
      },
    ])

    const { result } = renderHook(() => useVarLimit())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.varLimit).toBeNull()
  })

  it('ignores disabled VAR_BREACH rules', async () => {
    mockFetchRules.mockResolvedValue([
      {
        id: 'rule-1',
        name: 'VaR Breach',
        type: 'VAR_BREACH',
        threshold: 2000000,
        operator: 'GREATER_THAN',
        severity: 'CRITICAL',
        channels: ['IN_APP'],
        enabled: false,
      },
    ])

    const { result } = renderHook(() => useVarLimit())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.varLimit).toBeNull()
  })

  it('returns null when fetch fails', async () => {
    mockFetchRules.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useVarLimit())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.varLimit).toBeNull()
  })
})
