import { fireEvent, render, screen } from '@testing-library/react'
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

function renderPanel(overrides: Partial<Parameters<typeof GreeksPanel>[0]> = {}) {
  return render(
    <GreeksPanel
      greeksResult={greeksResult}
      loading={false}
      error={null}
      volBump={0}
      onVolBumpChange={() => {}}
      {...overrides}
    />,
  )
}

describe('GreeksPanel', () => {
  it('is collapsed by default and hides content', () => {
    renderPanel()

    expect(screen.getByTestId('greeks-panel')).toBeInTheDocument()
    expect(screen.queryByTestId('greeks-heatmap')).not.toBeInTheDocument()
  })

  it('expands when the toggle is clicked', () => {
    renderPanel()

    fireEvent.click(screen.getByTestId('greeks-toggle'))

    expect(screen.getByTestId('greeks-heatmap')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-summary')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-whatif')).toBeInTheDocument()
  })

  it('collapses again on a second click', () => {
    renderPanel()

    fireEvent.click(screen.getByTestId('greeks-toggle'))
    fireEvent.click(screen.getByTestId('greeks-toggle'))

    expect(screen.queryByTestId('greeks-heatmap')).not.toBeInTheDocument()
  })

  it('renders greeks heatmap with asset class rows when expanded', () => {
    renderPanel()

    fireEvent.click(screen.getByTestId('greeks-toggle'))

    expect(screen.getByTestId('greeks-heatmap')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-row-EQUITY')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-row-COMMODITY')).toBeInTheDocument()
  })

  it('renders theta and rho summary when expanded', () => {
    renderPanel()

    fireEvent.click(screen.getByTestId('greeks-toggle'))

    const summary = screen.getByTestId('greeks-summary')
    expect(summary).toBeInTheDocument()
    expect(summary).toHaveTextContent('Theta')
    expect(summary).toHaveTextContent('Rho')
  })

  it('shows loading state', () => {
    renderPanel({ greeksResult: null, loading: true })

    expect(screen.getByTestId('greeks-loading')).toBeInTheDocument()
  })

  it('shows error state', () => {
    renderPanel({ greeksResult: null, error: 'Failed to calculate Greeks' })

    expect(screen.getByTestId('greeks-error')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-error')).toHaveTextContent('Failed to calculate Greeks')
  })
})
