import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../hooks/useStressTest')

import { ScenariosTab } from './ScenariosTab'
import { useStressTest } from '../hooks/useStressTest'

const mockUseStressTest = vi.mocked(useStressTest)

describe('ScenariosTab', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockUseStressTest.mockReturnValue({
      scenarios: ['MARKET_CRASH', 'RATE_SHOCK'],
      selectedScenario: 'MARKET_CRASH',
      setSelectedScenario: vi.fn(),
      result: null,
      loading: false,
      error: null,
      run: vi.fn(),
    })
  })

  it('calls useStressTest with the given portfolioId', () => {
    render(<ScenariosTab portfolioId="port-1" />)

    expect(mockUseStressTest).toHaveBeenCalledWith('port-1')
  })

  it('renders the stress test panel', () => {
    render(<ScenariosTab portfolioId="port-1" />)

    expect(screen.getByTestId('stress-test-panel')).toBeInTheDocument()
  })
})
