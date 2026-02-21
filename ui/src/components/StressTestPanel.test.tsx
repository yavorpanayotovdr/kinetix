import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { StressTestResultDto } from '../types'
import { StressTestPanel } from './StressTestPanel'

const scenarios = ['GFC_2008', 'COVID_2020', 'TAPER_TANTRUM_2013', 'EURO_CRISIS_2011']

const stressResult: StressTestResultDto = {
  scenarioName: 'GFC_2008',
  baseVar: '100000.00',
  stressedVar: '300000.00',
  pnlImpact: '-550000.00',
  assetClassImpacts: [
    { assetClass: 'EQUITY', baseExposure: '1000000.00', stressedExposure: '600000.00', pnlImpact: '-400000.00' },
    { assetClass: 'COMMODITY', baseExposure: '500000.00', stressedExposure: '350000.00', pnlImpact: '-150000.00' },
  ],
  calculatedAt: '2025-01-15T10:00:00Z',
}

describe('StressTestPanel', () => {
  it('renders scenario dropdown', () => {
    render(
      <StressTestPanel
        scenarios={scenarios}
        result={null}
        loading={false}
        error={null}
        selectedScenario="GFC_2008"
        onScenarioChange={() => {}}
        onRun={() => {}}
      />,
    )

    const dropdown = screen.getByTestId('scenario-dropdown')
    expect(dropdown).toBeInTheDocument()
    expect(dropdown).toHaveValue('GFC_2008')
  })

  it('renders results table after data loads', () => {
    render(
      <StressTestPanel
        scenarios={scenarios}
        result={stressResult}
        loading={false}
        error={null}
        selectedScenario="GFC_2008"
        onScenarioChange={() => {}}
        onRun={() => {}}
      />,
    )

    expect(screen.getByTestId('stress-results')).toBeInTheDocument()
    expect(screen.getByTestId('stress-results-table')).toBeInTheDocument()
    expect(screen.getByTestId('stress-impact-chart')).toBeInTheDocument()
  })

  it('shows loading state', () => {
    render(
      <StressTestPanel
        scenarios={scenarios}
        result={null}
        loading={true}
        error={null}
        selectedScenario="GFC_2008"
        onScenarioChange={() => {}}
        onRun={() => {}}
      />,
    )

    expect(screen.getByTestId('stress-loading')).toBeInTheDocument()
  })

  it('shows error state', () => {
    render(
      <StressTestPanel
        scenarios={scenarios}
        result={null}
        loading={false}
        error="Something went wrong"
        selectedScenario="GFC_2008"
        onScenarioChange={() => {}}
        onRun={() => {}}
      />,
    )

    expect(screen.getByTestId('stress-error')).toBeInTheDocument()
    expect(screen.getByTestId('stress-error')).toHaveTextContent('Something went wrong')
  })
})
