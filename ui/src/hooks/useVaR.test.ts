import { renderHook, waitFor, act } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useVaR } from './useVaR'

vi.mock('../api/risk', () => ({
  fetchVaR: vi.fn(),
  triggerVaRCalculation: vi.fn(),
}))

vi.mock('../api/jobHistory', () => ({
  fetchValuationJobsForChart: vi.fn(),
}))

import { fetchVaR } from '../api/risk'
import { fetchValuationJobsForChart } from '../api/jobHistory'
import type { ValuationJobSummaryDto } from '../types'

const mockFetchVaR = vi.mocked(fetchVaR)
const mockFetchChartJobs = vi.mocked(fetchValuationJobsForChart)

describe('useVaR', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('populates history from job history on initial load', async () => {
    mockFetchChartJobs.mockResolvedValue([
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
        {
          jobId: 'j2',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1300000,
          expectedShortfall: 1600000,
          pvValue: 11000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
      ])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchChartJobs).toHaveBeenCalledWith(
      'port-1',
      expect.any(String),
      expect.any(String),
    )
    expect(result.current.history).toHaveLength(2)
    expect(result.current.history[0].varValue).toBe(1200000)
    expect(result.current.history[1].varValue).toBe(1300000)
  })

  it('filters out non-COMPLETED jobs and jobs with null varValue', async () => {
    mockFetchChartJobs.mockResolvedValue([
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
        {
          jobId: 'j2',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'FAILED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: null,
          durationMs: null,
          calculationType: null,
          confidenceLevel: null,
          varValue: null,
          expectedShortfall: null,
          pvValue: null,
          delta: null,
          gamma: null,
          vega: null,
          theta: null,
          rho: null,
        },
        {
          jobId: 'j3',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T11:00:00Z',
          completedAt: '2025-01-15T11:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: null,
          expectedShortfall: null,
          pvValue: null,
          delta: null,
          gamma: null,
          vega: null,
          theta: null,
          rho: null,
        },
      ])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(1)
    expect(result.current.history[0].varValue).toBe(1200000)
  })

  it('sorts history by completedAt ascending', async () => {
    mockFetchChartJobs.mockResolvedValue([
        {
          jobId: 'j2',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1300000,
          expectedShortfall: 1600000,
          pvValue: 11000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
      ])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history[0].calculatedAt).toBe('2025-01-15T09:01:00Z')
    expect(result.current.history[1].calculatedAt).toBe('2025-01-15T10:01:00Z')
  })

  it('appends polled VaR result to pre-loaded history without duplicating', async () => {
    mockFetchChartJobs.mockResolvedValue([
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
      ])
    mockFetchVaR.mockResolvedValue({
      portfolioId: 'port-1',
      calculationType: 'HISTORICAL',
      confidenceLevel: 'CL_95',
      varValue: '1400000',
      expectedShortfall: '1700000',
      componentBreakdown: [],
      calculatedAt: '2025-01-15T10:30:00Z',
    })

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(2)
    expect(result.current.history[0].varValue).toBe(1200000)
    expect(result.current.history[1].varValue).toBe(1400000)
  })

  it('does not fetch job history when portfolioId is null', () => {
    const { result } = renderHook(() => useVaR(null))

    expect(mockFetchChartJobs).not.toHaveBeenCalled()
    expect(result.current.history).toHaveLength(0)
    expect(result.current.loading).toBe(false)
  })

  it('gracefully handles job history fetch failure', async () => {
    mockFetchChartJobs.mockRejectedValue(new Error('Network error'))
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(0)
    expect(result.current.error).toBeNull()
  })

  it('accumulates aggregate Greeks into history entry when VaR result includes greeks', async () => {
    mockFetchChartJobs.mockResolvedValue([])
    mockFetchVaR.mockResolvedValue({
      portfolioId: 'port-1',
      calculationType: 'HISTORICAL',
      confidenceLevel: 'CL_95',
      varValue: '1400000',
      expectedShortfall: '1700000',
      componentBreakdown: [],
      calculatedAt: '2025-01-15T10:30:00Z',
      greeks: {
        portfolioId: 'port-1',
        assetClassGreeks: [
          { assetClass: 'EQUITY', delta: '1000.5', gamma: '50.25', vega: '3000.1' },
          { assetClass: 'COMMODITY', delta: '500.3', gamma: '10.75', vega: '2000.9' },
        ],
        theta: '-100',
        rho: '200',
        calculatedAt: '2025-01-15T10:30:00Z',
      },
    })

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(1)
    const entry = result.current.history[0]
    expect(entry.delta).toBeCloseTo(1500.8)
    expect(entry.gamma).toBeCloseTo(61.0)
    expect(entry.vega).toBeCloseTo(5001.0)
    expect(entry.theta).toBeCloseTo(-100)
  })

  it('leaves Greeks fields undefined when VaR result has no greeks', async () => {
    mockFetchChartJobs.mockResolvedValue([])
    mockFetchVaR.mockResolvedValue({
      portfolioId: 'port-1',
      calculationType: 'HISTORICAL',
      confidenceLevel: 'CL_95',
      varValue: '1400000',
      expectedShortfall: '1700000',
      componentBreakdown: [],
      calculatedAt: '2025-01-15T10:30:00Z',
    })

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(1)
    const entry = result.current.history[0]
    expect(entry.delta).toBeUndefined()
    expect(entry.gamma).toBeUndefined()
    expect(entry.vega).toBeUndefined()
    expect(entry.theta).toBeUndefined()
  })

  it('populates Greeks from job history when available', async () => {
    mockFetchChartJobs.mockResolvedValue([
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
          delta: 1500.8,
          gamma: 61.0,
          vega: 5001.0,
          theta: -120.5,
          rho: 200.0,
        },
        {
          jobId: 'j2',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1300000,
          expectedShortfall: 1600000,
          pvValue: 11000000,
          delta: 1600.2,
          gamma: 65.5,
          vega: 5200.3,
          theta: -135.2,
          rho: 210.0,
        },
      ])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(2)
    expect(result.current.history[0].delta).toBeCloseTo(1500.8)
    expect(result.current.history[0].gamma).toBeCloseTo(61.0)
    expect(result.current.history[0].vega).toBeCloseTo(5001.0)
    expect(result.current.history[0].theta).toBeCloseTo(-120.5)
    expect(result.current.history[1].delta).toBeCloseTo(1600.2)
  })

  it('leaves Greeks undefined when job history has null Greeks', async () => {
    mockFetchChartJobs.mockResolvedValue([
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
          delta: null,
          gamma: null,
          vega: null,
          theta: null,
          rho: null,
        },
      ])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history).toHaveLength(1)
    expect(result.current.history[0].delta).toBeUndefined()
    expect(result.current.history[0].gamma).toBeUndefined()
    expect(result.current.history[0].vega).toBeUndefined()
    expect(result.current.history[0].theta).toBeUndefined()
  })

  it('history entries include confidenceLevel from job history', async () => {
    mockFetchChartJobs.mockResolvedValue([
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_99',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
      ])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history[0].confidenceLevel).toBe('CL_99')
  })

  it('defaults confidenceLevel to CL_95 for old jobs without confidenceLevel', async () => {
    mockFetchChartJobs.mockResolvedValue([
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: '2025-01-15T09:00:00Z',
          completedAt: '2025-01-15T09:01:00Z',
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: null,
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
      ])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history[0].confidenceLevel).toBe('CL_95')
  })

  it('history entry from polled VaR result includes confidenceLevel', async () => {
    mockFetchChartJobs.mockResolvedValue([])
    mockFetchVaR.mockResolvedValue({
      portfolioId: 'port-1',
      calculationType: 'HISTORICAL',
      confidenceLevel: 'CL_99',
      varValue: '1400000',
      expectedShortfall: '1700000',
      componentBreakdown: [],
      calculatedAt: '2025-01-15T10:30:00Z',
    })

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.history[0].confidenceLevel).toBe('CL_99')
  })

  it('selectedConfidenceLevel defaults to CL_95', async () => {
    mockFetchChartJobs.mockResolvedValue([])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.selectedConfidenceLevel).toBe('CL_95')
  })

  it('filteredHistory filters by selected confidence level', async () => {
    const now = new Date()
    const recentTime1 = new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString()
    const recentTime2 = new Date(now.getTime() - 1 * 60 * 60 * 1000).toISOString()

    mockFetchChartJobs.mockResolvedValue([
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: recentTime1,
          completedAt: recentTime1,
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1200000,
          expectedShortfall: 1500000,
          pvValue: 10000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
        {
          jobId: 'j2',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: recentTime2,
          completedAt: recentTime2,
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_99',
          varValue: 2500000,
          expectedShortfall: 3000000,
          pvValue: 10000000,
          delta: 0,
          gamma: 0,
          vega: 0,
          theta: 0,
          rho: 0,
        },
      ])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    // Default is CL_95 — should only show CL_95 entries
    expect(result.current.filteredHistory).toHaveLength(1)
    expect(result.current.filteredHistory[0].confidenceLevel).toBe('CL_95')

    // Switch to CL_99
    act(() => {
      result.current.setSelectedConfidenceLevel('CL_99')
    })

    expect(result.current.filteredHistory).toHaveLength(1)
    expect(result.current.filteredHistory[0].confidenceLevel).toBe('CL_99')
  })

  it('changing confidence level resets zoom stack', async () => {
    mockFetchChartJobs.mockResolvedValue([])
    mockFetchVaR.mockResolvedValue({
      portfolioId: 'port-1',
      calculationType: 'HISTORICAL',
      confidenceLevel: 'CL_95',
      varValue: '1400000',
      expectedShortfall: '1700000',
      componentBreakdown: [],
      calculatedAt: '2025-01-15T10:30:00Z',
    })

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    // Zoom in first
    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:00:00Z', to: '2025-01-15T10:30:00Z', label: 'Zoom' })
    })

    expect(result.current.zoomDepth).toBe(1)

    // Change confidence level — should reset zoom
    act(() => {
      result.current.setSelectedConfidenceLevel('CL_99')
    })

    expect(result.current.zoomDepth).toBe(0)
  })

  it('re-fetches history when time range changes', async () => {
    const now = new Date()
    const recent = new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString()

    mockFetchChartJobs.mockResolvedValue([
      {
        jobId: 'j1',
        portfolioId: 'port-1',
        triggerType: 'SCHEDULED',
        status: 'COMPLETED',
        startedAt: recent,
        completedAt: recent,
        durationMs: 60000,
        calculationType: 'HISTORICAL',
        confidenceLevel: 'CL_95',
        varValue: 1200000,
        expectedShortfall: 1500000,
        pvValue: 10000000,
        delta: 0, gamma: 0, vega: 0, theta: 0, rho: 0,
      },
    ])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchChartJobs).toHaveBeenCalledTimes(1)
    expect(result.current.history).toHaveLength(1)

    // Change time range — should re-fetch
    const sevenDaysAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString()
    mockFetchChartJobs.mockResolvedValue([
      {
        jobId: 'j1',
        portfolioId: 'port-1',
        triggerType: 'SCHEDULED',
        status: 'COMPLETED',
        startedAt: recent,
        completedAt: recent,
        durationMs: 60000,
        calculationType: 'HISTORICAL',
        confidenceLevel: 'CL_95',
        varValue: 1200000,
        expectedShortfall: 1500000,
        pvValue: 10000000,
        delta: 0, gamma: 0, vega: 0, theta: 0, rho: 0,
      },
      {
        jobId: 'j-old',
        portfolioId: 'port-1',
        triggerType: 'SCHEDULED',
        status: 'COMPLETED',
        startedAt: sevenDaysAgo,
        completedAt: sevenDaysAgo,
        durationMs: 60000,
        calculationType: 'HISTORICAL',
        confidenceLevel: 'CL_95',
        varValue: 900000,
        expectedShortfall: 1100000,
        pvValue: 8000000,
        delta: 0, gamma: 0, vega: 0, theta: 0, rho: 0,
      },
    ])

    act(() => {
      result.current.setTimeRange({
        from: sevenDaysAgo,
        to: now.toISOString(),
        label: 'Last 7d',
      })
    })

    await waitFor(() => {
      expect(mockFetchChartJobs).toHaveBeenCalledTimes(2)
    })

    await waitFor(() => {
      expect(result.current.history).toHaveLength(2)
    })
  })

  it('re-fetches history on zoom', async () => {
    mockFetchChartJobs.mockResolvedValue([])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(mockFetchChartJobs).toHaveBeenCalledTimes(1)

    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:00:00Z', to: '2025-01-15T11:00:00Z', label: 'Custom' })
    })

    await waitFor(() => {
      expect(mockFetchChartJobs).toHaveBeenCalledTimes(2)
    })
  })

  it('re-fetches history on resetZoom', async () => {
    mockFetchChartJobs.mockResolvedValue([])
    mockFetchVaR.mockResolvedValue(null)

    const { result } = renderHook(() => useVaR('port-1'))

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.zoomIn({ from: '2025-01-15T10:00:00Z', to: '2025-01-15T11:00:00Z', label: 'Custom' })
    })

    await waitFor(() => {
      expect(mockFetchChartJobs).toHaveBeenCalledTimes(2)
    })

    act(() => {
      result.current.resetZoom()
    })

    await waitFor(() => {
      expect(mockFetchChartJobs).toHaveBeenCalledTimes(3)
    })
  })

  it('preserves Greeks from polled entry when historical entry lacks them', async () => {
    const timestamp = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()

    // fetchVaR returns a result with full Greeks
    mockFetchVaR.mockResolvedValue({
      portfolioId: 'port-1',
      calculationType: 'HISTORICAL',
      confidenceLevel: 'CL_95',
      varValue: '1400000',
      expectedShortfall: '1700000',
      componentBreakdown: [],
      calculatedAt: timestamp,
      greeks: {
        portfolioId: 'port-1',
        assetClassGreeks: [
          { assetClass: 'EQUITY', delta: '1000.5', gamma: '50.25', vega: '3000.1' },
        ],
        theta: '-100',
        rho: '200',
        calculatedAt: timestamp,
      },
    })

    // History API returns the same job but with null Greeks (pre-V12 migration)
    let resolveHistory!: (value: ValuationJobSummaryDto[]) => void
    const historyPromise = new Promise<ValuationJobSummaryDto[]>((resolve) => {
      resolveHistory = resolve
    })
    mockFetchChartJobs.mockReturnValue(historyPromise)

    const { result } = renderHook(() => useVaR('port-1'))

    // Wait for fetchVaR to complete — entry with Greeks appears in history
    await waitFor(() => {
      expect(result.current.history).toHaveLength(1)
    })

    expect(result.current.history[0].delta).toBeCloseTo(1000.5)

    // Now resolve history with matching timestamp but null Greeks
    await act(async () => {
      resolveHistory([
        {
          jobId: 'j1',
          portfolioId: 'port-1',
          triggerType: 'SCHEDULED',
          status: 'COMPLETED',
          startedAt: timestamp,
          completedAt: timestamp,
          durationMs: 60000,
          calculationType: 'HISTORICAL',
          confidenceLevel: 'CL_95',
          varValue: 1400000,
          expectedShortfall: 1700000,
          pvValue: 10000000,
          delta: null,
          gamma: null,
          vega: null,
          theta: null,
          rho: null,
        },
      ])
    })

    // After merge, the entry should still retain its Greeks from the polled result
    expect(result.current.history).toHaveLength(1)
    expect(result.current.history[0].delta).toBeCloseTo(1000.5)
    expect(result.current.history[0].gamma).toBeCloseTo(50.25)
    expect(result.current.history[0].vega).toBeCloseTo(3000.1)
    expect(result.current.history[0].theta).toBeCloseTo(-100)
  })

  describe('historical mode (valuationDate)', () => {
    it('isLive is true when valuationDate is null', async () => {
      mockFetchChartJobs.mockResolvedValue([])
      mockFetchVaR.mockResolvedValue(null)

      const { result } = renderHook(() => useVaR('port-1', null))

      await waitFor(() => {
        expect(result.current.loading).toBe(false)
      })

      expect(result.current.isLive).toBe(true)
    })

    it('isLive is true when valuationDate is omitted', async () => {
      mockFetchChartJobs.mockResolvedValue([])
      mockFetchVaR.mockResolvedValue(null)

      const { result } = renderHook(() => useVaR('port-1'))

      await waitFor(() => {
        expect(result.current.loading).toBe(false)
      })

      expect(result.current.isLive).toBe(true)
    })

    it('isLive is false when valuationDate is set', async () => {
      mockFetchChartJobs.mockResolvedValue([])
      mockFetchVaR.mockResolvedValue(null)

      const { result } = renderHook(() => useVaR('port-1', '2025-03-10'))

      await waitFor(() => {
        expect(result.current.loading).toBe(false)
      })

      expect(result.current.isLive).toBe(false)
    })

    it('passes valuationDate to fetchVaR in historical mode', async () => {
      mockFetchChartJobs.mockResolvedValue([])
      mockFetchVaR.mockResolvedValue(null)

      renderHook(() => useVaR('port-1', '2025-03-10'))

      await waitFor(() => {
        expect(mockFetchVaR).toHaveBeenCalledWith('port-1', '2025-03-10')
      })
    })

    it('does not append historical result to history array', async () => {
      mockFetchChartJobs.mockResolvedValue([])
      mockFetchVaR.mockResolvedValue({
        portfolioId: 'port-1',
        calculationType: 'HISTORICAL',
        confidenceLevel: 'CL_95',
        varValue: '1400000',
        expectedShortfall: '1700000',
        componentBreakdown: [],
        calculatedAt: '2025-03-10T06:00:00Z',
        valuationDate: '2025-03-10',
      })

      const { result } = renderHook(() => useVaR('port-1', '2025-03-10'))

      await waitFor(() => {
        expect(result.current.loading).toBe(false)
      })

      expect(result.current.varResult).not.toBeNull()
      expect(result.current.history).toHaveLength(0)
    })
  })

  describe('historical mode — no polling', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('does not poll in historical mode', async () => {
      mockFetchChartJobs.mockResolvedValue([])
      mockFetchVaR.mockResolvedValue(null)

      renderHook(() => useVaR('port-1', '2025-03-10'))

      await act(async () => {
        await vi.advanceTimersByTimeAsync(1)
      })

      expect(mockFetchVaR).toHaveBeenCalledTimes(1)

      // Advance past multiple poll intervals
      await act(async () => {
        await vi.advanceTimersByTimeAsync(90_000)
      })

      // Still only 1 call — no polling
      expect(mockFetchVaR).toHaveBeenCalledTimes(1)
    })
  })

  describe('polling overlap guard', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('skips poll when a previous request is still in flight', async () => {
      mockFetchChartJobs.mockResolvedValue([])

      let resolveSlowFetch: (value: null) => void
      const slowPromise = new Promise<null>((resolve) => {
        resolveSlowFetch = resolve
      })

      // Initial load resolves quickly, second call will be slow
      mockFetchVaR.mockResolvedValueOnce(null).mockReturnValueOnce(slowPromise)

      renderHook(() => useVaR('port-1'))

      // Flush the initial load (microtasks for resolved promises)
      await act(async () => {
        await vi.advanceTimersByTimeAsync(1)
      })

      expect(mockFetchVaR).toHaveBeenCalledTimes(1)

      // Trigger first poll — starts the slow request (call #2)
      await act(async () => {
        await vi.advanceTimersByTimeAsync(30_000)
      })

      expect(mockFetchVaR).toHaveBeenCalledTimes(2)

      // Another poll fires while slow request is still pending — should be skipped
      await act(async () => {
        await vi.advanceTimersByTimeAsync(30_000)
      })

      // Still only 2 calls — the guard prevented call #3
      expect(mockFetchVaR).toHaveBeenCalledTimes(2)

      // Resolve the slow request and set up the next one
      mockFetchVaR.mockResolvedValue(null)
      await act(async () => {
        resolveSlowFetch!(null)
      })

      // Next poll should now proceed (call #3)
      await act(async () => {
        await vi.advanceTimersByTimeAsync(30_000)
      })

      expect(mockFetchVaR).toHaveBeenCalledTimes(3)
    })
  })
})
