import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { StressPositionTable } from './StressPositionTable'
import { makePositionImpact } from '../test-utils/stressMocks'

const positions = [
  makePositionImpact({ instrumentId: 'AAPL', assetClass: 'EQUITY', baseMarketValue: '500000.00', stressedMarketValue: '300000.00', pnlImpact: '-200000.00', percentageOfTotal: '40.00' }),
  makePositionImpact({ instrumentId: 'GOLD', assetClass: 'COMMODITY', baseMarketValue: '500000.00', stressedMarketValue: '350000.00', pnlImpact: '-150000.00', percentageOfTotal: '30.00' }),
  makePositionImpact({ instrumentId: 'MSFT', assetClass: 'EQUITY', baseMarketValue: '300000.00', stressedMarketValue: '200000.00', pnlImpact: '-100000.00', percentageOfTotal: '20.00' }),
  makePositionImpact({ instrumentId: 'OIL', assetClass: 'COMMODITY', baseMarketValue: '200000.00', stressedMarketValue: '150000.00', pnlImpact: '-50000.00', percentageOfTotal: '10.00' }),
]

describe('StressPositionTable', () => {
  it('should render columns: Instrument, Asset Class, Base MV, Stressed MV, P&L Impact, % of Total', () => {
    render(<StressPositionTable positions={positions} />)

    expect(screen.getByText('Instrument')).toBeInTheDocument()
    expect(screen.getByText('Asset Class')).toBeInTheDocument()
    expect(screen.getByText('Base MV')).toBeInTheDocument()
    expect(screen.getByText('Stressed MV')).toBeInTheDocument()
    expect(screen.getByText('P&L Impact')).toBeInTheDocument()
    expect(screen.getByText('% of Total')).toBeInTheDocument()
  })

  it('should display positions sorted by absolute P&L impact descending', () => {
    render(<StressPositionTable positions={positions} />)

    const rows = screen.getAllByTestId('position-row')
    expect(rows[0]).toHaveTextContent('AAPL')
    expect(rows[1]).toHaveTextContent('GOLD')
    expect(rows[2]).toHaveTextContent('MSFT')
    expect(rows[3]).toHaveTextContent('OIL')
  })

  it('should format all monetary values as currency', () => {
    render(<StressPositionTable positions={positions} />)

    const rows = screen.getAllByTestId('position-row')
    expect(rows[0]).toHaveTextContent('$500,000')
    expect(rows[0]).toHaveTextContent('$300,000')
    expect(rows[0]).toHaveTextContent('-$200,000')
  })

  it('should display percentage contribution with % suffix', () => {
    render(<StressPositionTable positions={positions} />)

    const percentages = screen.getAllByTestId('pct-of-total')
    expect(percentages[0]).toHaveTextContent('40.0%')
    expect(percentages[1]).toHaveTextContent('30.0%')
  })

  it('should show total row at bottom summing P&L impacts', () => {
    render(<StressPositionTable positions={positions} />)

    const totalRow = screen.getByTestId('total-row')
    expect(totalRow).toHaveTextContent('Total')
    expect(totalRow).toHaveTextContent('-$500,000')
  })

  it('should filter to show only positions of the selected asset class', () => {
    render(<StressPositionTable positions={positions} assetClassFilter="EQUITY" />)

    const rows = screen.getAllByTestId('position-row')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('AAPL')
    expect(rows[1]).toHaveTextContent('MSFT')
  })

  it('should show filter pill and clear button when asset class filter is active', () => {
    const onClear = vi.fn()
    render(<StressPositionTable positions={positions} assetClassFilter="EQUITY" onClearFilter={onClear} />)

    const pill = screen.getByTestId('filter-pill')
    expect(pill).toHaveTextContent('EQUITY')

    fireEvent.click(screen.getByTestId('clear-filter'))
    expect(onClear).toHaveBeenCalledOnce()
  })

  it('should not show filter pill when no asset class filter is active', () => {
    render(<StressPositionTable positions={positions} />)

    expect(screen.queryByTestId('filter-pill')).not.toBeInTheDocument()
  })

  it('should show empty state when positions array is empty', () => {
    render(<StressPositionTable positions={[]} />)

    expect(screen.getByTestId('no-positions')).toBeInTheDocument()
  })

  it('should handle positions with zero base market value', () => {
    const zero = [makePositionImpact({ baseMarketValue: '0.00', stressedMarketValue: '100000.00', pnlImpact: '100000.00' })]
    render(<StressPositionTable positions={zero} />)

    const rows = screen.getAllByTestId('position-row')
    expect(rows).toHaveLength(1)
    expect(rows[0]).toHaveTextContent('$0.00')
  })
})
