import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ScenarioComparisonTable } from './ScenarioComparisonTable'
import { ALL_STRESS_RESULTS, makeStressResult } from '../test-utils/stressMocks'

const defaultProps = {
  results: ALL_STRESS_RESULTS,
  selectedScenario: null,
  onSelectScenario: vi.fn(),
}

describe('ScenarioComparisonTable', () => {
  it('should render column headers: Scenario, Base VaR, Stressed VaR, VaR Multiplier, P&L Impact', () => {
    render(<ScenarioComparisonTable {...defaultProps} />)

    expect(screen.getByText('Scenario')).toBeInTheDocument()
    expect(screen.getByText('Base VaR')).toBeInTheDocument()
    expect(screen.getByText('Stressed VaR')).toBeInTheDocument()
    expect(screen.getByText('VaR Multiplier')).toBeInTheDocument()
  })

  it('should render one row per scenario result', () => {
    render(<ScenarioComparisonTable {...defaultProps} />)

    const rows = screen.getAllByTestId('scenario-row')
    expect(rows).toHaveLength(4)
  })

  it('should display results with scenario names formatted with spaces', () => {
    render(<ScenarioComparisonTable {...defaultProps} />)

    expect(screen.getByText('GFC 2008')).toBeInTheDocument()
    expect(screen.getByText('COVID 2020')).toBeInTheDocument()
    expect(screen.getByText('TAPER TANTRUM 2013')).toBeInTheDocument()
    expect(screen.getByText('EURO CRISIS 2011')).toBeInTheDocument()
  })

  it('should format VaR Multiplier as Stressed/Base ratio', () => {
    render(<ScenarioComparisonTable {...defaultProps} />)

    const multipliers = screen.getAllByTestId('var-multiplier')
    // GFC: stressedVar=300000, baseVar=100000 => 3.0x
    expect(multipliers[0]).toHaveTextContent('3.0x')
  })

  it('should display P&L Impact with red text for losses', () => {
    render(<ScenarioComparisonTable {...defaultProps} />)

    const impacts = screen.getAllByTestId('pnl-impact')
    impacts.forEach((impact) => {
      expect(impact.className).toContain('text-red-600')
    })
  })

  it('should highlight selected scenario row on click', () => {
    const onSelect = vi.fn()
    render(<ScenarioComparisonTable {...defaultProps} onSelectScenario={onSelect} />)

    const rows = screen.getAllByTestId('scenario-row')
    fireEvent.click(rows[0])
    expect(onSelect).toHaveBeenCalledWith('GFC_2008')
  })

  it('should deselect when clicking an already selected scenario', () => {
    const onSelect = vi.fn()
    render(
      <ScenarioComparisonTable
        {...defaultProps}
        selectedScenario="GFC_2008"
        onSelectScenario={onSelect}
      />,
    )

    const rows = screen.getAllByTestId('scenario-row')
    fireEvent.click(rows[0])
    expect(onSelect).toHaveBeenCalledWith(null)
  })

  it('should handle zero Base VaR without division by zero', () => {
    const results = [makeStressResult({ baseVar: '0.00', stressedVar: '100000.00' })]
    render(<ScenarioComparisonTable results={results} selectedScenario={null} onSelectScenario={vi.fn()} />)

    const multipliers = screen.getAllByTestId('var-multiplier')
    expect(multipliers[0]).toHaveTextContent('-')
  })

  it('should show empty state message when no results exist', () => {
    render(<ScenarioComparisonTable results={[]} selectedScenario={null} onSelectScenario={vi.fn()} />)

    expect(screen.getByTestId('no-results')).toBeInTheDocument()
  })
})
