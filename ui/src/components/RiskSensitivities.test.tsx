import { render, screen, fireEvent } from '@testing-library/react'
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
    expect(pvDisplay).toHaveTextContent('PV')
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

  describe('Greek info popovers', () => {
    it('shows delta explanation when info icon is clicked', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-delta'))

      const popover = screen.getByTestId('greek-popover-delta')
      expect(popover).toBeInTheDocument()
      expect(popover).toHaveTextContent('underlying asset(s) move up by $1')
    })

    it('shows gamma explanation when info icon is clicked', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-gamma'))

      const popover = screen.getByTestId('greek-popover-gamma')
      expect(popover).toBeInTheDocument()
      expect(popover).toHaveTextContent('delta itself is expected to change')
    })

    it('shows vega explanation when info icon is clicked', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-vega'))

      const popover = screen.getByTestId('greek-popover-vega')
      expect(popover).toBeInTheDocument()
      expect(popover).toHaveTextContent('implied volatility rises by 1 percentage point')
    })

    it('closes popover when the same info icon is clicked again', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-delta'))
      expect(screen.getByTestId('greek-popover-delta')).toBeInTheDocument()

      fireEvent.click(screen.getByTestId('greek-info-delta'))
      expect(screen.queryByTestId('greek-popover-delta')).not.toBeInTheDocument()
    })

    it('closes popover when Escape is pressed', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-delta'))
      expect(screen.getByTestId('greek-popover-delta')).toBeInTheDocument()

      fireEvent.keyDown(document, { key: 'Escape' })
      expect(screen.queryByTestId('greek-popover-delta')).not.toBeInTheDocument()
    })

    it('shows only one popover at a time', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-delta'))
      expect(screen.getByTestId('greek-popover-delta')).toBeInTheDocument()

      fireEvent.click(screen.getByTestId('greek-info-gamma'))
      expect(screen.queryByTestId('greek-popover-delta')).not.toBeInTheDocument()
      expect(screen.getByTestId('greek-popover-gamma')).toBeInTheDocument()
    })

    it('closes popover when clicking outside', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-delta'))
      expect(screen.getByTestId('greek-popover-delta')).toBeInTheDocument()

      fireEvent.mouseDown(document.body)
      expect(screen.queryByTestId('greek-popover-delta')).not.toBeInTheDocument()
    })

    it('closes popover when close button is clicked', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-delta'))
      expect(screen.getByTestId('greek-popover-delta')).toBeInTheDocument()

      fireEvent.click(screen.getByTestId('greek-popover-delta-close'))
      expect(screen.queryByTestId('greek-popover-delta')).not.toBeInTheDocument()
    })

    it('shows theta explanation when info icon is clicked', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-theta'))

      const popover = screen.getByTestId('greek-popover-theta')
      expect(popover).toBeInTheDocument()
      expect(popover).toHaveTextContent('passage of time')
    })

    it('shows rho explanation when info icon is clicked', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-rho'))

      const popover = screen.getByTestId('greek-popover-rho')
      expect(popover).toBeInTheDocument()
      expect(popover).toHaveTextContent('interest rates')
    })

    it('closes theta/rho popover when a header popover is opened', () => {
      render(<RiskSensitivities greeksResult={greeksResult} />)

      fireEvent.click(screen.getByTestId('greek-info-theta'))
      expect(screen.getByTestId('greek-popover-theta')).toBeInTheDocument()

      fireEvent.click(screen.getByTestId('greek-info-delta'))
      expect(screen.queryByTestId('greek-popover-theta')).not.toBeInTheDocument()
      expect(screen.getByTestId('greek-popover-delta')).toBeInTheDocument()
    })
  })
})
