import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ScenarioComparisonView } from './ScenarioComparisonView'
import { makeStressResult } from '../test-utils/stressMocks'

const scenario1 = makeStressResult({
  scenarioName: 'GFC_2008',
  baseVar: '100000.00',
  stressedVar: '300000.00',
  pnlImpact: '-500000.00',
})
const scenario2 = makeStressResult({
  scenarioName: 'COVID_2020',
  baseVar: '100000.00',
  stressedVar: '250000.00',
  pnlImpact: '-350000.00',
})
const scenario3 = makeStressResult({
  scenarioName: 'TAPER_TANTRUM_2013',
  baseVar: '100000.00',
  stressedVar: '150000.00',
  pnlImpact: '-100000.00',
})

describe('ScenarioComparisonView', () => {
  it('should render columns for each selected scenario', () => {
    render(<ScenarioComparisonView scenarios={[scenario1, scenario2]} />)

    expect(screen.getAllByText('GFC 2008').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('COVID 2020').length).toBeGreaterThanOrEqual(1)
  })

  it('should display metrics in rows aligned across scenarios', () => {
    render(<ScenarioComparisonView scenarios={[scenario1, scenario2]} />)

    expect(screen.getByText('Base VaR')).toBeInTheDocument()
    expect(screen.getByText('Stressed VaR')).toBeInTheDocument()
    expect(screen.getByText('P&L Impact')).toBeInTheDocument()
    expect(screen.getByText('VaR Multiplier')).toBeInTheDocument()
  })

  it('should render asset class impact section for each scenario', () => {
    render(<ScenarioComparisonView scenarios={[scenario1, scenario2]} />)

    expect(screen.getByTestId('comparison-view')).toBeInTheDocument()
    // Asset class impacts are present
    expect(screen.getAllByText('EQUITY').length).toBeGreaterThanOrEqual(1)
  })

  it('should show empty state when fewer than 2 scenarios selected', () => {
    render(<ScenarioComparisonView scenarios={[scenario1]} />)

    expect(screen.getByText(/Select 2-3 scenarios/)).toBeInTheDocument()
  })

  it('should limit to maximum 3 scenarios for comparison', () => {
    const scenario4 = makeStressResult({ scenarioName: 'EXTRA_SCENARIO' })
    render(
      <ScenarioComparisonView scenarios={[scenario1, scenario2, scenario3, scenario4]} />,
    )

    // Should only render the first 3
    expect(screen.getAllByText('GFC 2008').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('COVID 2020').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('TAPER TANTRUM 2013').length).toBeGreaterThanOrEqual(1)
    expect(screen.queryByText('EXTRA SCENARIO')).not.toBeInTheDocument()
  })
})
