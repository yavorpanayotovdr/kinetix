import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useEodPromotion } from './useEodPromotion'

vi.mock('../api/officialEod', () => ({
  promoteToOfficialEod: vi.fn(),
  demoteOfficialEod: vi.fn(),
}))

import * as api from '../api/officialEod'

const mockPromote = vi.mocked(api.promoteToOfficialEod)
const mockDemote = vi.mocked(api.demoteOfficialEod)

function makeResponse(overrides: Partial<api.EodPromotionResponse> = {}): api.EodPromotionResponse {
  return {
    jobId: 'job-1',
    portfolioId: 'port-1',
    valuationDate: '2026-03-13',
    runLabel: 'OFFICIAL_EOD',
    promotedAt: '2026-03-13T18:00:00Z',
    promotedBy: 'user-b',
    ...overrides,
  }
}

describe('useEodPromotion', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('starts in idle state', () => {
    const { result } = renderHook(() => useEodPromotion())
    expect(result.current.state).toBe('idle')
    expect(result.current.error).toBeNull()
    expect(result.current.errorStatus).toBeNull()
  })

  it('transitions to loading then promoted on successful promote', async () => {
    mockPromote.mockResolvedValue(makeResponse())
    const { result } = renderHook(() => useEodPromotion())

    let promiseResult: api.EodPromotionResponse | null = null
    await act(async () => {
      promiseResult = await result.current.promote('job-1', 'user-b')
    })

    expect(result.current.state).toBe('promoted')
    expect(result.current.error).toBeNull()
    expect(promiseResult).toEqual(makeResponse())
    expect(mockPromote).toHaveBeenCalledWith('job-1', 'user-b')
  })

  it('transitions to error state on 409 conflict', async () => {
    const err = new Error('An Official EOD already exists for portfolio port-1 on 2026-03-13') as Error & { status: number }
    err.status = 409
    mockPromote.mockRejectedValue(err)
    const { result } = renderHook(() => useEodPromotion())

    await act(async () => {
      await result.current.promote('job-1', 'user-b')
    })

    expect(result.current.state).toBe('error')
    expect(result.current.error).toContain('already exists')
    expect(result.current.errorStatus).toBe(409)
  })

  it('transitions to error state on 403 self-promotion', async () => {
    const err = new Error('Separation of duties: user user-a cannot promote their own run') as Error & { status: number }
    err.status = 403
    mockPromote.mockRejectedValue(err)
    const { result } = renderHook(() => useEodPromotion())

    await act(async () => {
      await result.current.promote('job-1', 'user-a')
    })

    expect(result.current.state).toBe('error')
    expect(result.current.error).toContain('Separation of duties')
    expect(result.current.errorStatus).toBe(403)
  })

  it('demote transitions back to idle on success', async () => {
    mockDemote.mockResolvedValue(makeResponse({ runLabel: 'ADHOC', promotedAt: null, promotedBy: null }))
    const { result } = renderHook(() => useEodPromotion())

    await act(async () => {
      await result.current.demote('job-1', 'user-c')
    })

    expect(result.current.state).toBe('idle')
    expect(mockDemote).toHaveBeenCalledWith('job-1', 'user-c')
  })

  it('demote transitions to error on failure', async () => {
    const err = new Error('Job not found') as Error & { status: number }
    err.status = 404
    mockDemote.mockRejectedValue(err)
    const { result } = renderHook(() => useEodPromotion())

    await act(async () => {
      await result.current.demote('job-1', 'user-c')
    })

    expect(result.current.state).toBe('error')
    expect(result.current.errorStatus).toBe(404)
  })

  it('reset clears error state', async () => {
    const err = new Error('fail') as Error & { status: number }
    err.status = 500
    mockPromote.mockRejectedValue(err)
    const { result } = renderHook(() => useEodPromotion())

    await act(async () => {
      await result.current.promote('job-1', 'user-b')
    })
    expect(result.current.state).toBe('error')

    act(() => {
      result.current.reset()
    })
    expect(result.current.state).toBe('idle')
    expect(result.current.error).toBeNull()
    expect(result.current.errorStatus).toBeNull()
  })
})
