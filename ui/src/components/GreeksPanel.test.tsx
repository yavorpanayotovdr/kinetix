import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { GreeksResultDto } from '../types'
import { GreeksPanel } from './GreeksPanel'

const greeksResult: GreeksResultDto = {
  portfolioId: 'port-1',
  assetClassGreeks: [
    { assetClass: 'EQUITY', delta: '1234.560000', gamma: '78.900000', vega: '5678.120000' },
    { assetClass: 'COMMODITY', delta: '567.890000', gamma: '12.340000', vega: '2345.670000' },
  ],
  theta: '-123.450000',
  rho: '456.780000',
  calculatedAt: '2025-01-15T10:00:00Z',
}

describe('GreeksPanel', () => {
  it('renders greeks heatmap with asset class rows', () => {
    render(
      <GreeksPanel
        greeksResult={greeksResult}
        loading={false}
        error={null}
        volBump={0}
        onVolBumpChange={() => {}}
      />,
    )

    expect(screen.getByTestId('greeks-heatmap')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-row-EQUITY')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-row-COMMODITY')).toBeInTheDocument()
  })

  it('renders theta and rho summary', () => {
    render(
      <GreeksPanel
        greeksResult={greeksResult}
        loading={false}
        error={null}
        volBump={0}
        onVolBumpChange={() => {}}
      />,
    )

    const summary = screen.getByTestId('greeks-summary')
    expect(summary).toBeInTheDocument()
    expect(summary).toHaveTextContent('Theta')
    expect(summary).toHaveTextContent('Rho')
  })

  it('shows loading state', () => {
    render(
      <GreeksPanel
        greeksResult={null}
        loading={true}
        error={null}
        volBump={0}
        onVolBumpChange={() => {}}
      />,
    )

    expect(screen.getByTestId('greeks-loading')).toBeInTheDocument()
  })

  it('shows error state', () => {
    render(
      <GreeksPanel
        greeksResult={null}
        loading={false}
        error="Failed to calculate Greeks"
        volBump={0}
        onVolBumpChange={() => {}}
      />,
    )

    expect(screen.getByTestId('greeks-error')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-error')).toHaveTextContent('Failed to calculate Greeks')
  })
})
