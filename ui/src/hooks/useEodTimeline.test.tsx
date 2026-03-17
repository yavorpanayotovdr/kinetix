import { renderHook, act, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { useEodTimeline } from './useEodTimeline'
import * as eodTimelineApi from '../api/eodTimeline'
import type { EodTimelineResponseDto } from '../types'

function makeResponse(overrides: Partial<EodTimelineResponseDto> = {}): EodTimelineResponseDto {
  return {
    bookId: 'book-1',
    from: '2026-02-01',
    to: '2026-03-15',
    entries: [
      {
        valuationDate: '2026-03-14',
        jobId: 'job-1',
        varValue: 100000,
        expectedShortfall: 150000,
        pvValue: 5000000,
        delta: 0.5,
        gamma: 0.01,
        vega: 200,
        theta: -50,
        rho: 25,
        promotedAt: '2026-03-14T19:00:00Z',
        promotedBy: 'risk-manager',
        varChange: 5000,
        varChangePct: 5.0,
        esChange: 7500,
        calculationType: 'PARAMETRIC',
        confidenceLevel: 0.99,
      },
    ],
    ...overrides,
  }
}

describe('useEodTimeline', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('fetches timeline on mount with default date range', async () => {
    const spy = vi.spyOn(eodTimelineApi, 'fetchEodTimeline').mockResolvedValueOnce(makeResponse())

    const { result } = renderHook(() => useEodTimeline('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(spy).toHaveBeenCalledOnce()
    expect(spy).toHaveBeenCalledWith('book-1', expect.any(String), expect.any(String))
    expect(result.current.entries).toHaveLength(1)
    expect(result.current.entries[0].valuationDate).toBe('2026-03-14')
    expect(result.current.error).toBeNull()
  })

  it('returns loading true while fetch is in-flight', async () => {
    let resolve: (v: EodTimelineResponseDto) => void = () => {}
    vi.spyOn(eodTimelineApi, 'fetchEodTimeline').mockReturnValueOnce(
      new Promise((r) => { resolve = r }),
    )

    const { result } = renderHook(() => useEodTimeline('book-1'))

    expect(result.current.loading).toBe(true)

    act(() => resolve(makeResponse()))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })
  })

  it('returns error when API throws', async () => {
    vi.spyOn(eodTimelineApi, 'fetchEodTimeline').mockRejectedValueOnce(
      new Error('Failed to fetch EOD timeline: 500'),
    )

    const { result } = renderHook(() => useEodTimeline('book-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Failed to fetch EOD timeline: 500')
    expect(result.current.entries).toHaveLength(0)
  })

  it('does not fetch when bookId is null', () => {
    const spy = vi.spyOn(eodTimelineApi, 'fetchEodTimeline')
    renderHook(() => useEodTimeline(null))
    expect(spy).not.toHaveBeenCalled()
  })

  it('re-fetches when from/to date range changes', async () => {
    const spy = vi.spyOn(eodTimelineApi, 'fetchEodTimeline').mockResolvedValue(makeResponse())

    const { result } = renderHook(() => useEodTimeline('book-1'))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(spy).toHaveBeenCalledOnce()

    act(() => result.current.setFrom('2026-01-01'))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(spy).toHaveBeenCalledTimes(2)
  })

  it('refresh triggers a new fetch', async () => {
    const spy = vi.spyOn(eodTimelineApi, 'fetchEodTimeline').mockResolvedValue(makeResponse())

    const { result } = renderHook(() => useEodTimeline('book-1'))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(spy).toHaveBeenCalledOnce()

    act(() => result.current.refresh())

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(spy).toHaveBeenCalledTimes(2)
  })

  it('exposes from and to state', () => {
    vi.spyOn(eodTimelineApi, 'fetchEodTimeline').mockResolvedValue(makeResponse())
    const { result } = renderHook(() => useEodTimeline('book-1'))
    expect(result.current.from).toBeTruthy()
    expect(result.current.to).toBeTruthy()
  })
})
