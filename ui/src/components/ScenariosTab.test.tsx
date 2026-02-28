import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ScenariosTab } from './ScenariosTab'

const defaultProps = {
  scenarios: ['MARKET_CRASH', 'RATE_SHOCK'],
  result: null,
  loading: false,
  error: null,
  selectedScenario: 'MARKET_CRASH',
  onScenarioChange: vi.fn(),
  onRun: vi.fn(),
}

describe('ScenariosTab', () => {
  it('renders the stress test panel', () => {
    render(<ScenariosTab {...defaultProps} />)

    expect(screen.getByTestId('stress-test-panel')).toBeInTheDocument()
  })

  it('passes scenarios to StressTestPanel', () => {
    render(<ScenariosTab {...defaultProps} />)

    const dropdown = screen.getByTestId('scenario-dropdown')
    expect(dropdown).toHaveValue('MARKET_CRASH')
  })
})
