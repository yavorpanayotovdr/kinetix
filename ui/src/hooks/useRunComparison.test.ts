import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useRunComparison } from './useRunComparison'

vi.mock('../api/runComparison', () => ({
  compareDayOverDay: vi.fn(),
  compareByJobIds: vi.fn(),
  compareModelVersions: vi.fn(),
  requestAttribution: vi.fn(),
}))

import * as api from '../api/runComparison'
import type { RunComparisonResponseDto, VaRAttributionDto } from '../types'

const mockCompareDayOverDay = vi.mocked(api.compareDayOverDay)
const mockCompareByJobIds = vi.mocked(api.compareByJobIds)
const mockRequestAttribution = vi.mocked(api.requestAttribution)

function makeComparison(overrides: Partial<RunComparisonResponseDto> = {}): RunComparisonResponseDto {
  return {
    comparisonId: 'cmp-1',
    comparisonType: 'DAILY_VAR',
    portfolioId: 'port-1',
    baseRun: {
      jobId: 'job-base',
      label: 'Base',
      valuationDate: '2025-01-14',
      calcType: 'PARAMETRIC',
      confLevel: 'CL_95',
      varValue: '5000',
      es: '6250',
      pv: '100000',
      delta: '0.5',
      gamma: '0.01',
      vega: '100',
      theta: '-50',
      rho: '25',
      componentBreakdown: [],
      positionRisk: [],
      modelVersion: null,
      parameters: {},
      calculatedAt: '2025-01-14T10:00:00Z',
    },
    targetRun: {
      jobId: 'job-target',
      label: 'Target',
      valuationDate: '2025-01-15',
      calcType: 'PARAMETRIC',
      confLevel: 'CL_95',
      varValue: '5500',
      es: '6875',
      pv: '101000',
      delta: '0.52',
      gamma: '0.011',
      vega: '105',
      theta: '-52',
      rho: '26',
      componentBreakdown: [],
      positionRisk: [],
      modelVersion: null,
      parameters: {},
      calculatedAt: '2025-01-15T10:00:00Z',
    },
    portfolioDiff: {
      varChange: '500',
      varChangePercent: '10',
      esChange: '625',
      esChangePercent: '10',
      pvChange: '1000',
      deltaChange: '0.02',
      gammaChange: '0.001',
      vegaChange: '5',
      thetaChange: '-2',
      rhoChange: '1',
    },
    componentDiffs: [],
    positionDiffs: [],
    parameterDiffs: [],
    attribution: null,
    inputChanges: null,
    ...overrides,
  }
}

describe('useRunComparison', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('starts with null comparison and no loading', () => {
    const { result } = renderHook(() => useRunComparison())

    expect(result.current.comparison).toBeNull()
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
    expect(result.current.attribution).toBeNull()
    expect(result.current.threshold).toBe(0)
    expect(result.current.mode).toBe('DAILY_VAR')
  })

  it('loadDayOverDay sets loading and returns comparison', async () => {
    const comparison = makeComparison()
    mockCompareDayOverDay.mockResolvedValue(comparison)

    const { result } = renderHook(() => useRunComparison())

    act(() => {
      void result.current.loadDayOverDay('port-1', '2025-01-15', '2025-01-14')
    })

    expect(result.current.loading).toBe(true)

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.comparison).toEqual(comparison)
    expect(result.current.error).toBeNull()
    expect(mockCompareDayOverDay).toHaveBeenCalledWith('port-1', '2025-01-15', '2025-01-14')
  })

  it('loadDayOverDay sets error on failure', async () => {
    mockCompareDayOverDay.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useRunComparison())

    await act(async () => {
      await result.current.loadDayOverDay('port-1')
    })

    expect(result.current.loading).toBe(false)
    expect(result.current.comparison).toBeNull()
    expect(result.current.error).toBe('Network error')
  })

  it('compareJobs loads comparison by job IDs', async () => {
    const comparison = makeComparison()
    mockCompareByJobIds.mockResolvedValue(comparison)

    const { result } = renderHook(() => useRunComparison())

    await act(async () => {
      await result.current.compareJobs('port-1', 'job-base', 'job-target')
    })

    expect(result.current.comparison).toEqual(comparison)
    expect(result.current.error).toBeNull()
    expect(mockCompareByJobIds).toHaveBeenCalledWith('port-1', 'job-base', 'job-target')
  })

  it('requestAttribution loads attribution data', async () => {
    const attribution: VaRAttributionDto = {
      totalChange: '500',
      positionEffect: '300',
      volEffect: '150',
      corrEffect: '30',
      timeDecayEffect: '-20',
      unexplained: '40',
      effectMagnitudes: { position: 'MEDIUM', unexplained: 'SMALL' },
      caveats: [],
    }
    mockRequestAttribution.mockResolvedValue(attribution)

    const { result } = renderHook(() => useRunComparison())

    await act(async () => {
      await result.current.loadAttribution('port-1', '2025-01-15', '2025-01-14')
    })

    expect(result.current.attribution).toEqual(attribution)
    expect(result.current.attributionLoading).toBe(false)
    expect(mockRequestAttribution).toHaveBeenCalledWith('port-1', '2025-01-15', '2025-01-14')
  })

  it('setThreshold updates threshold state', () => {
    const { result } = renderHook(() => useRunComparison())

    act(() => {
      result.current.setThreshold(500)
    })

    expect(result.current.threshold).toBe(500)
  })

  it('reset clears comparison, attribution, error and threshold', async () => {
    const comparison = makeComparison()
    mockCompareDayOverDay.mockResolvedValue(comparison)

    const { result } = renderHook(() => useRunComparison())

    await act(async () => {
      await result.current.loadDayOverDay('port-1')
    })

    act(() => {
      result.current.setThreshold(1000)
    })

    expect(result.current.comparison).not.toBeNull()
    expect(result.current.threshold).toBe(1000)

    act(() => {
      result.current.reset()
    })

    expect(result.current.comparison).toBeNull()
    expect(result.current.attribution).toBeNull()
    expect(result.current.error).toBeNull()
    expect(result.current.threshold).toBe(0)
  })

  it('setMode updates mode state', () => {
    const { result } = renderHook(() => useRunComparison())

    act(() => {
      result.current.setMode('MODEL')
    })

    expect(result.current.mode).toBe('MODEL')
  })
})
