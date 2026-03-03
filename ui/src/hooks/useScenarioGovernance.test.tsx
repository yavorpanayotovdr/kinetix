import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useScenarioGovernance } from './useScenarioGovernance'
import * as scenariosApi from '../api/scenarios'
import { makeScenario } from '../test-utils/stressMocks'

vi.mock('../api/scenarios')

describe('useScenarioGovernance', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should fetch all scenarios with governance metadata', async () => {
    const scenarios = [
      makeScenario({ id: '1', name: 'Scenario A', status: 'APPROVED' }),
      makeScenario({ id: '2', name: 'Scenario B', status: 'DRAFT' }),
    ]
    vi.mocked(scenariosApi.listScenarios).mockResolvedValue(scenarios)

    const { result } = renderHook(() => useScenarioGovernance())

    await waitFor(() => {
      expect(result.current.scenarios).toHaveLength(2)
    })

    expect(result.current.scenarios[0].name).toBe('Scenario A')
    expect(result.current.scenarios[1].status).toBe('DRAFT')
  })

  it('should submit a draft scenario for approval', async () => {
    const draft = makeScenario({ id: '1', status: 'DRAFT' })
    const submitted = { ...draft, status: 'PENDING_APPROVAL' }
    vi.mocked(scenariosApi.listScenarios).mockResolvedValue([draft])
    vi.mocked(scenariosApi.submitScenario).mockResolvedValue(submitted)

    const { result } = renderHook(() => useScenarioGovernance())

    await waitFor(() => {
      expect(result.current.scenarios).toHaveLength(1)
    })

    await act(async () => {
      await result.current.submit('1')
    })

    expect(scenariosApi.submitScenario).toHaveBeenCalledWith('1')
  })

  it('should approve a pending scenario', async () => {
    const pending = makeScenario({ id: '1', status: 'PENDING_APPROVAL' })
    const approved = { ...pending, status: 'APPROVED', approvedBy: 'head@kinetix.com' }
    vi.mocked(scenariosApi.listScenarios).mockResolvedValue([pending])
    vi.mocked(scenariosApi.approveScenario).mockResolvedValue(approved)

    const { result } = renderHook(() => useScenarioGovernance())

    await waitFor(() => {
      expect(result.current.scenarios).toHaveLength(1)
    })

    await act(async () => {
      await result.current.approve('1')
    })

    expect(scenariosApi.approveScenario).toHaveBeenCalledWith('1', 'user')
  })

  it('should retire an approved scenario', async () => {
    const approved = makeScenario({ id: '1', status: 'APPROVED' })
    const retired = { ...approved, status: 'RETIRED' }
    vi.mocked(scenariosApi.listScenarios).mockResolvedValue([approved])
    vi.mocked(scenariosApi.retireScenario).mockResolvedValue(retired)

    const { result } = renderHook(() => useScenarioGovernance())

    await waitFor(() => {
      expect(result.current.scenarios).toHaveLength(1)
    })

    await act(async () => {
      await result.current.retire('1')
    })

    expect(scenariosApi.retireScenario).toHaveBeenCalledWith('1')
  })
})
