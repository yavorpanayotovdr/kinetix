import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useVolSurface } from './useVolSurface'

vi.mock('../api/volSurface', () => ({
  fetchVolSurface: vi.fn(),
  fetchVolSurfaceDiff: vi.fn(),
}))

import { fetchVolSurface, fetchVolSurfaceDiff } from '../api/volSurface'

const mockFetchVolSurface = vi.mocked(fetchVolSurface)
const mockFetchVolSurfaceDiff = vi.mocked(fetchVolSurfaceDiff)

const SURFACE_DATA = {
  instrumentId: 'AAPL',
  asOfDate: '2026-03-25T10:00:00Z',
  source: 'BLOOMBERG',
  points: [
    { strike: 100, maturityDays: 30, impliedVol: 0.25 },
    { strike: 110, maturityDays: 30, impliedVol: 0.22 },
    { strike: 100, maturityDays: 90, impliedVol: 0.28 },
  ],
}

const DIFF_DATA = {
  instrumentId: 'AAPL',
  baseDate: '2026-03-25T10:00:00Z',
  compareDate: '2026-03-24T10:00:00Z',
  diffs: [
    { strike: 100, maturityDays: 30, baseVol: 0.25, compareVol: 0.24, diff: 0.01 },
  ],
}

describe('useVolSurface', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('returns null surface and is not loading when no instrumentId is provided', () => {
    const { result } = renderHook(() => useVolSurface(null))

    expect(result.current.surface).toBeNull()
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
    expect(mockFetchVolSurface).not.toHaveBeenCalled()
  })

  it('fetches vol surface when instrumentId is provided', async () => {
    mockFetchVolSurface.mockResolvedValue(SURFACE_DATA)

    const { result } = renderHook(() => useVolSurface('AAPL'))

    expect(result.current.loading).toBe(true)

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchVolSurface).toHaveBeenCalledWith('AAPL')
    expect(result.current.surface).toEqual(SURFACE_DATA)
    expect(result.current.error).toBeNull()
  })

  it('returns null surface when instrument has no vol surface', async () => {
    mockFetchVolSurface.mockResolvedValue(null)

    const { result } = renderHook(() => useVolSurface('UNKNOWN'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.surface).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('sets error when fetch fails', async () => {
    mockFetchVolSurface.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useVolSurface('AAPL'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.surface).toBeNull()
  })

  it('re-fetches when instrumentId changes', async () => {
    mockFetchVolSurface.mockResolvedValue(SURFACE_DATA)

    const { result, rerender } = renderHook(
      ({ id }) => useVolSurface(id),
      { initialProps: { id: 'AAPL' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchVolSurface).toHaveBeenCalledWith('AAPL')

    rerender({ id: 'SPX' })

    await waitFor(() => {
      expect(mockFetchVolSurface).toHaveBeenCalledWith('SPX')
    })
  })

  it('does not fetch diff when compareDate is not provided', async () => {
    mockFetchVolSurface.mockResolvedValue(SURFACE_DATA)

    const { result } = renderHook(() => useVolSurface('AAPL'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchVolSurfaceDiff).not.toHaveBeenCalled()
    expect(result.current.diff).toBeNull()
  })

  it('fetches diff when compareDate is provided', async () => {
    mockFetchVolSurface.mockResolvedValue(SURFACE_DATA)
    mockFetchVolSurfaceDiff.mockResolvedValue(DIFF_DATA)

    const { result } = renderHook(() =>
      useVolSurface('AAPL', '2026-03-24T10:00:00Z')
    )

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchVolSurfaceDiff).toHaveBeenCalledWith('AAPL', '2026-03-24T10:00:00Z')
    expect(result.current.diff).toEqual(DIFF_DATA)
  })

  it('clears data when instrumentId is set to null', async () => {
    mockFetchVolSurface.mockResolvedValue(SURFACE_DATA)

    const { result, rerender } = renderHook(
      ({ id }) => useVolSurface(id),
      { initialProps: { id: 'AAPL' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.surface).toEqual(SURFACE_DATA)
    })

    rerender({ id: null })

    expect(result.current.surface).toBeNull()
    expect(result.current.loading).toBe(false)
  })

  it('exposes unique maturity days from loaded surface', async () => {
    mockFetchVolSurface.mockResolvedValue(SURFACE_DATA)

    const { result } = renderHook(() => useVolSurface('AAPL'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.maturities).toEqual([30, 90])
  })
})
