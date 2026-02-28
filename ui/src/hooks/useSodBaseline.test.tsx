import { renderHook, waitFor, act } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/sodSnapshot')

import { useSodBaseline } from './useSodBaseline'
import {
  fetchSodBaselineStatus,
  createSodSnapshot,
  resetSodBaseline,
  computePnlAttribution,
} from '../api/sodSnapshot'

const mockFetchStatus = vi.mocked(fetchSodBaselineStatus)
const mockCreateSnapshot = vi.mocked(createSodSnapshot)
const mockResetBaseline = vi.mocked(resetSodBaseline)
const mockComputePnl = vi.mocked(computePnlAttribution)

describe('useSodBaseline', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('fetches status on mount when portfolioId is provided', async () => {
    const statusData = {
      exists: true,
      baselineDate: '2025-01-15',
      snapshotType: 'MANUAL',
      createdAt: '2025-01-15T08:00:00Z',
    }
    mockFetchStatus.mockResolvedValue(statusData)

    const { result } = renderHook(() => useSodBaseline('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.status).toEqual(statusData)
    expect(mockFetchStatus).toHaveBeenCalledWith('port-1')
  })

  it('returns null status when portfolioId is null', () => {
    const { result } = renderHook(() => useSodBaseline(null))

    expect(result.current.status).toBeNull()
    expect(result.current.loading).toBe(false)
    expect(mockFetchStatus).not.toHaveBeenCalled()
  })

  it('sets error on fetch failure', async () => {
    mockFetchStatus.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useSodBaseline('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
  })

  it('createSnapshot calls API and updates status', async () => {
    const initialStatus = {
      exists: false,
      baselineDate: null,
      snapshotType: null,
      createdAt: null,
    }
    const updatedStatus = {
      exists: true,
      baselineDate: '2025-01-15',
      snapshotType: 'MANUAL',
      createdAt: '2025-01-15T09:30:00Z',
    }
    mockFetchStatus.mockResolvedValue(initialStatus)
    mockCreateSnapshot.mockResolvedValue(updatedStatus)

    const { result } = renderHook(() => useSodBaseline('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    await act(async () => {
      await result.current.createSnapshot()
    })

    expect(result.current.status).toEqual(updatedStatus)
    expect(result.current.creating).toBe(false)
  })

  it('resetBaseline calls API and clears status', async () => {
    const initialStatus = {
      exists: true,
      baselineDate: '2025-01-15',
      snapshotType: 'MANUAL',
      createdAt: '2025-01-15T08:00:00Z',
    }
    mockFetchStatus.mockResolvedValue(initialStatus)
    mockResetBaseline.mockResolvedValue(undefined)

    const { result } = renderHook(() => useSodBaseline('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    await act(async () => {
      await result.current.resetBaseline()
    })

    expect(result.current.status?.exists).toBe(false)
    expect(result.current.resetting).toBe(false)
  })

  it('createSnapshot forwards jobId parameter to API', async () => {
    const initialStatus = {
      exists: false,
      baselineDate: null,
      snapshotType: null,
      createdAt: null,
    }
    const updatedStatus = {
      exists: true,
      baselineDate: '2025-01-15',
      snapshotType: 'MANUAL',
      createdAt: '2025-01-15T09:30:00Z',
      sourceJobId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      calculationType: 'PARAMETRIC',
    }
    mockFetchStatus.mockResolvedValue(initialStatus)
    mockCreateSnapshot.mockResolvedValue(updatedStatus)

    const { result } = renderHook(() => useSodBaseline('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    await act(async () => {
      await result.current.createSnapshot('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
    })

    expect(mockCreateSnapshot).toHaveBeenCalledWith('port-1', 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
    expect(result.current.status).toEqual(updatedStatus)
  })

  it('computeAttribution calls API and returns result', async () => {
    const statusData = {
      exists: true,
      baselineDate: '2025-01-15',
      snapshotType: 'MANUAL',
      createdAt: '2025-01-15T08:00:00Z',
    }
    const pnlData = {
      portfolioId: 'port-1',
      date: '2025-01-15',
      totalPnl: '15000.00',
      deltaPnl: '8000.00',
      gammaPnl: '2500.00',
      vegaPnl: '3000.00',
      thetaPnl: '-1500.00',
      rhoPnl: '500.00',
      unexplainedPnl: '2500.00',
      positionAttributions: [],
      calculatedAt: '2025-01-15T10:30:00Z',
    }
    mockFetchStatus.mockResolvedValue(statusData)
    mockComputePnl.mockResolvedValue(pnlData)

    const { result } = renderHook(() => useSodBaseline('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    let pnlResult: unknown
    await act(async () => {
      pnlResult = await result.current.computeAttribution()
    })

    expect(pnlResult).toEqual(pnlData)
    expect(result.current.computing).toBe(false)
  })
})
