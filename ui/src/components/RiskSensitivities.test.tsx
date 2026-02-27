import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { GreeksResultDto } from '../types'
import { RiskSensitivities } from './RiskSensitivities'

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

describe('RiskSensitivities', () => {
  it('renders the heatmap with asset class rows', () => {
    render(<RiskSensitivities greeksResult={greeksResult} />)

    expect(screen.getByTestId('risk-sensitivities')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-heatmap')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-row-EQUITY')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-row-COMMODITY')).toBeInTheDocument()
  })

  it('renders theta and rho summary', () => {
    render(<RiskSensitivities greeksResult={greeksResult} />)

    const summary = screen.getByTestId('greeks-summary')
    expect(summary).toBeInTheDocument()
    expect(summary).toHaveTextContent('Theta')
    expect(summary).toHaveTextContent('Rho')
  })

  it('formats greek values with commas and decimals', () => {
    render(<RiskSensitivities greeksResult={greeksResult} />)

    const equityRow = screen.getByTestId('greeks-row-EQUITY')
    expect(equityRow).toHaveTextContent('1,234.56')
    expect(equityRow).toHaveTextContent('78.90')
    expect(equityRow).toHaveTextContent('5,678.12')
  })

  it('displays PV when pvValue is provided', () => {
    render(<RiskSensitivities greeksResult={greeksResult} pvValue="1800000.00" />)

    const pvDisplay = screen.getByTestId('pv-display')
    expect(pvDisplay).toBeInTheDocument()
    expect(pvDisplay).toHaveTextContent('Portfolio Value')
    expect(pvDisplay).toHaveTextContent('$1.8M')
  })

  it('does not display PV when pvValue is null', () => {
    render(<RiskSensitivities greeksResult={greeksResult} pvValue={null} />)

    expect(screen.queryByTestId('pv-display')).not.toBeInTheDocument()
  })

  it('does not display PV when pvValue is omitted', () => {
    render(<RiskSensitivities greeksResult={greeksResult} />)

    expect(screen.queryByTestId('pv-display')).not.toBeInTheDocument()
  })
})
