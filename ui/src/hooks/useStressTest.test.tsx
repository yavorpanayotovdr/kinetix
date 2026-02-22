import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StressTestResultDto } from '../types'

vi.mock('../api/stress')

import { fetchScenarios, runStressTest } from '../api/stress'
import { useStressTest } from './useStressTest'

const mockFetchScenarios = vi.mocked(fetchScenarios)
const mockRunStressTest = vi.mocked(runStressTest)

const stressResult: StressTestResultDto = {
  scenarioName: 'MARKET_CRASH',
  baseVar: '1000000',
  stressedVar: '2500000',
  pnlImpact: '-1500000',
  assetClassImpacts: [
    {
      assetClass: 'EQUITY',
      baseExposure: '5000000',
      stressedExposure: '3500000',
      pnlImpact: '-1500000',
    },
  ],
  calculatedAt: '2025-01-15T10:30:00Z',
}

describe('useStressTest', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('loads scenarios on mount', async () => {
    mockFetchScenarios.mockResolvedValue(['MARKET_CRASH', 'RATE_SHOCK'])

    const { result } = renderHook(() => useStressTest('port-1'))

    await waitFor(() => {
      expect(result.current.scenarios).toEqual(['MARKET_CRASH', 'RATE_SHOCK'])
    })

    expect(result.current.selectedScenario).toBe('MARKET_CRASH')
    expect(result.current.result).toBeNull()
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('runs stress test and returns result', async () => {
    mockFetchScenarios.mockResolvedValue(['MARKET_CRASH'])
    mockRunStressTest.mockResolvedValue(stressResult)

    const { result } = renderHook(() => useStressTest('port-1'))

    await waitFor(() => {
      expect(result.current.scenarios.length).toBeGreaterThan(0)
    })

    await act(async () => {
      result.current.run()
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.result).toEqual(stressResult)
    expect(mockRunStressTest).toHaveBeenCalledWith('port-1', 'MARKET_CRASH')
  })

  it('sets error when scenario fetch fails', async () => {
    mockFetchScenarios.mockRejectedValue(new Error('Scenario fetch failed'))

    const { result } = renderHook(() => useStressTest('port-1'))

    await waitFor(() => {
      expect(result.current.error).toBe('Scenario fetch failed')
    })
  })

  it('sets error when stress test run fails', async () => {
    mockFetchScenarios.mockResolvedValue(['MARKET_CRASH'])
    mockRunStressTest.mockRejectedValue(new Error('Stress test failed'))

    const { result } = renderHook(() => useStressTest('port-1'))

    await waitFor(() => {
      expect(result.current.scenarios.length).toBeGreaterThan(0)
    })

    await act(async () => {
      result.current.run()
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Stress test failed')
  })

  it('clears result when portfolioId changes', async () => {
    mockFetchScenarios.mockResolvedValue(['MARKET_CRASH'])
    mockRunStressTest.mockResolvedValue(stressResult)

    const { result, rerender } = renderHook(
      ({ portfolioId }) => useStressTest(portfolioId),
      { initialProps: { portfolioId: 'port-1' as string | null } },
    )

    await waitFor(() => {
      expect(result.current.scenarios.length).toBeGreaterThan(0)
    })

    await act(async () => {
      result.current.run()
    })

    await waitFor(() => {
      expect(result.current.result).toEqual(stressResult)
    })

    rerender({ portfolioId: 'port-2' })

    expect(result.current.result).toBeNull()
  })
})
