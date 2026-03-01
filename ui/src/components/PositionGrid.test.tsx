import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PositionDto, PositionRiskDto } from '../types'
import { PositionGrid } from './PositionGrid'

const makePosition = (overrides: Partial<PositionDto> = {}): PositionDto => ({
  portfolioId: 'port-1',
  instrumentId: 'AAPL',
  assetClass: 'EQUITY',
  quantity: '100',
  averageCost: { amount: '150.00', currency: 'USD' },
  marketPrice: { amount: '155.00', currency: 'USD' },
  marketValue: { amount: '15500.00', currency: 'USD' },
  unrealizedPnl: { amount: '500.00', currency: 'USD' },
  ...overrides,
})

describe('PositionGrid', () => {
  it('renders table headers', () => {
    render(<PositionGrid positions={[makePosition()]} />)

    const headers = [
      'Instrument',
      'Asset Class',
      'Quantity',
      'Avg Cost',
      'Market Price',
      'Market Value',
      'Unrealized P&L',
    ]
    headers.forEach((header) => {
      expect(
        screen.getByRole('columnheader', { name: header }),
      ).toBeInTheDocument()
    })
  })

  it('renders position row data', () => {
    render(<PositionGrid positions={[makePosition()]} />)

    const row = screen.getByTestId('position-row-AAPL')
    expect(within(row).getByText('AAPL')).toBeInTheDocument()
    expect(within(row).getByText('EQUITY')).toBeInTheDocument()
    expect(within(row).getByText('100')).toBeInTheDocument()
  })

  it('formats money values correctly', () => {
    render(<PositionGrid positions={[makePosition()]} />)

    const row = screen.getByTestId('position-row-AAPL')
    expect(within(row).getByText('$150.00')).toBeInTheDocument()
    expect(within(row).getByText('$155.00')).toBeInTheDocument()
    expect(within(row).getByText('$15,500.00')).toBeInTheDocument()
    expect(within(row).getByText('$500.00')).toBeInTheDocument()
  })

  it('applies green color to positive P&L', () => {
    render(
      <PositionGrid
        positions={[
          makePosition({
            unrealizedPnl: { amount: '500.00', currency: 'USD' },
          }),
        ]}
      />,
    )

    const pnlCell = screen.getByTestId('pnl-AAPL')
    expect(pnlCell).toHaveClass('text-green-600')
  })

  it('applies red color to negative P&L', () => {
    render(
      <PositionGrid
        positions={[
          makePosition({
            instrumentId: 'TSLA',
            unrealizedPnl: { amount: '-200.00', currency: 'USD' },
          }),
        ]}
      />,
    )

    const pnlCell = screen.getByTestId('pnl-TSLA')
    expect(pnlCell).toHaveClass('text-red-600')
  })

  it('renders empty state when no positions', () => {
    render(<PositionGrid positions={[]} />)

    expect(screen.getByText('No positions to display.')).toBeInTheDocument()
  })

  it('shows Live status when connected', () => {
    render(<PositionGrid positions={[makePosition()]} connected={true} />)

    const status = screen.getByTestId('connection-status')
    expect(status).toHaveTextContent('Live')
  })

  it('shows Disconnected status when not connected', () => {
    render(<PositionGrid positions={[makePosition()]} connected={false} />)

    const status = screen.getByTestId('connection-status')
    expect(status).toHaveTextContent('Disconnected')
  })

  it('renders multiple positions', () => {
    const positions = [
      makePosition({ instrumentId: 'AAPL' }),
      makePosition({ instrumentId: 'GOOGL', assetClass: 'EQUITY' }),
    ]
    render(<PositionGrid positions={positions} />)

    expect(screen.getByTestId('position-row-AAPL')).toBeInTheDocument()
    expect(screen.getByTestId('position-row-GOOGL')).toBeInTheDocument()
  })

  it('renders portfolio summary bar with totals', () => {
    const positions = [
      makePosition({
        instrumentId: 'AAPL',
        marketValue: { amount: '15500.00', currency: 'USD' },
        unrealizedPnl: { amount: '500.00', currency: 'USD' },
      }),
      makePosition({
        instrumentId: 'GOOGL',
        marketValue: { amount: '10000.00', currency: 'USD' },
        unrealizedPnl: { amount: '-200.00', currency: 'USD' },
      }),
    ]
    render(<PositionGrid positions={positions} />)

    const summary = screen.getByTestId('portfolio-summary')
    expect(summary).toBeInTheDocument()
    expect(within(summary).getByText('2')).toBeInTheDocument()
    expect(within(summary).getByText('$25,500.00')).toBeInTheDocument()
    expect(within(summary).getByText('$300.00')).toBeInTheDocument()
  })

  it('formats quantity values cleanly', () => {
    render(
      <PositionGrid
        positions={[makePosition({ quantity: '150.000000000000' })]}
      />,
    )

    const row = screen.getByTestId('position-row-AAPL')
    expect(within(row).getByText('150')).toBeInTheDocument()
  })

  describe('with position risk data', () => {
    const makeRisk = (overrides: Partial<PositionRiskDto> = {}): PositionRiskDto => ({
      instrumentId: 'AAPL',
      assetClass: 'EQUITY',
      marketValue: '15500.00',
      delta: '1234.56',
      gamma: '45.67',
      vega: '89.01',
      theta: null,
      rho: null,
      varContribution: '800.00',
      esContribution: '1000.00',
      percentageOfTotal: '64.85',
      ...overrides,
    })

    it('renders risk metric column headers when positionRisk is provided', () => {
      render(
        <PositionGrid
          positions={[makePosition()]}
          positionRisk={[makeRisk()]}
        />,
      )

      expect(screen.getByRole('columnheader', { name: 'Delta' })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: 'Gamma' })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: 'Vega' })).toBeInTheDocument()
      expect(screen.getByRole('columnheader', { name: 'VaR Contrib %' })).toBeInTheDocument()
    })

    it('does not render risk columns when positionRisk is absent', () => {
      render(<PositionGrid positions={[makePosition()]} />)

      expect(screen.queryByRole('columnheader', { name: 'Delta' })).not.toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'Gamma' })).not.toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'Vega' })).not.toBeInTheDocument()
      expect(screen.queryByRole('columnheader', { name: 'VaR Contrib %' })).not.toBeInTheDocument()
    })

    it('renders risk values in the row matched by instrumentId', () => {
      render(
        <PositionGrid
          positions={[makePosition()]}
          positionRisk={[makeRisk()]}
        />,
      )

      const row = screen.getByTestId('position-row-AAPL')
      expect(within(row).getByTestId('delta-AAPL')).toHaveTextContent('1,234.56')
      expect(within(row).getByTestId('gamma-AAPL')).toHaveTextContent('45.67')
      expect(within(row).getByTestId('vega-AAPL')).toHaveTextContent('89.01')
      expect(within(row).getByTestId('var-pct-AAPL')).toHaveTextContent('64.85%')
    })

    it('shows dash when risk data is missing for a position', () => {
      render(
        <PositionGrid
          positions={[
            makePosition({ instrumentId: 'AAPL' }),
            makePosition({ instrumentId: 'GOOGL' }),
          ]}
          positionRisk={[makeRisk({ instrumentId: 'AAPL' })]}
        />,
      )

      const googlRow = screen.getByTestId('position-row-GOOGL')
      expect(within(googlRow).getByTestId('delta-GOOGL')).toHaveTextContent('\u2014')
      expect(within(googlRow).getByTestId('gamma-GOOGL')).toHaveTextContent('\u2014')
      expect(within(googlRow).getByTestId('vega-GOOGL')).toHaveTextContent('\u2014')
      expect(within(googlRow).getByTestId('var-pct-GOOGL')).toHaveTextContent('\u2014')
    })

    it('shows dash when greek value is null', () => {
      render(
        <PositionGrid
          positions={[makePosition()]}
          positionRisk={[makeRisk({ delta: null, gamma: null, vega: null })]}
        />,
      )

      const row = screen.getByTestId('position-row-AAPL')
      expect(within(row).getByTestId('delta-AAPL')).toHaveTextContent('\u2014')
      expect(within(row).getByTestId('gamma-AAPL')).toHaveTextContent('\u2014')
      expect(within(row).getByTestId('vega-AAPL')).toHaveTextContent('\u2014')
      // VaR contribution should still show
      expect(within(row).getByTestId('var-pct-AAPL')).toHaveTextContent('64.85%')
    })

    it('renders grouped header rows for Position Details and Risk Metrics', () => {
      render(
        <PositionGrid
          positions={[makePosition()]}
          positionRisk={[makeRisk()]}
        />,
      )

      expect(screen.getByTestId('header-group-position')).toHaveTextContent('Position Details')
      expect(screen.getByTestId('header-group-risk')).toHaveTextContent('Risk Metrics')
    })

    it('applies indigo tint to risk metrics header group', () => {
      render(
        <PositionGrid
          positions={[makePosition()]}
          positionRisk={[makeRisk()]}
        />,
      )

      expect(screen.getByTestId('header-group-risk')).toHaveClass('bg-indigo-50')
    })

    it('sorts by VaR contribution when header is clicked', async () => {
      const user = userEvent.setup()
      const positions = [
        makePosition({ instrumentId: 'AAPL' }),
        makePosition({ instrumentId: 'MSFT' }),
        makePosition({ instrumentId: 'GOOGL' }),
      ]
      const risk = [
        makeRisk({ instrumentId: 'AAPL', percentageOfTotal: '20.00' }),
        makeRisk({ instrumentId: 'MSFT', percentageOfTotal: '50.00' }),
        makeRisk({ instrumentId: 'GOOGL', percentageOfTotal: '30.00' }),
      ]

      render(<PositionGrid positions={positions} positionRisk={risk} />)

      const varHeader = screen.getByTestId('sort-var-pct')
      await user.click(varHeader)

      // After clicking, should sort descending: MSFT(50) > GOOGL(30) > AAPL(20)
      const rows = screen.getAllByTestId(/^position-row-/)
      expect(rows[0]).toHaveAttribute('data-testid', 'position-row-MSFT')
      expect(rows[1]).toHaveAttribute('data-testid', 'position-row-GOOGL')
      expect(rows[2]).toHaveAttribute('data-testid', 'position-row-AAPL')
    })

    it('toggles sort direction on second click', async () => {
      const user = userEvent.setup()
      const positions = [
        makePosition({ instrumentId: 'AAPL' }),
        makePosition({ instrumentId: 'MSFT' }),
      ]
      const risk = [
        makeRisk({ instrumentId: 'AAPL', percentageOfTotal: '20.00' }),
        makeRisk({ instrumentId: 'MSFT', percentageOfTotal: '50.00' }),
      ]

      render(<PositionGrid positions={positions} positionRisk={risk} />)

      const varHeader = screen.getByTestId('sort-var-pct')

      // First click: descending
      await user.click(varHeader)
      let rows = screen.getAllByTestId(/^position-row-/)
      expect(rows[0]).toHaveAttribute('data-testid', 'position-row-MSFT')

      // Second click: ascending
      await user.click(varHeader)
      rows = screen.getAllByTestId(/^position-row-/)
      expect(rows[0]).toHaveAttribute('data-testid', 'position-row-AAPL')
    })

    it('renders Portfolio Delta and Portfolio VaR summary cards when risk data is provided', () => {
      const positions = [
        makePosition({ instrumentId: 'AAPL' }),
        makePosition({ instrumentId: 'MSFT' }),
      ]
      const risk = [
        makeRisk({ instrumentId: 'AAPL', delta: '1000.00', varContribution: '800.00', percentageOfTotal: '60.00' }),
        makeRisk({ instrumentId: 'MSFT', delta: '500.00', varContribution: '400.00', percentageOfTotal: '40.00' }),
      ]

      render(<PositionGrid positions={positions} positionRisk={risk} />)

      const summary = screen.getByTestId('portfolio-summary')
      expect(within(summary).getByTestId('summary-portfolio-delta')).toBeInTheDocument()
      expect(within(summary).getByTestId('summary-portfolio-var')).toBeInTheDocument()
    })
  })

  describe('pagination', () => {
    const makeManyPositions = (count: number): PositionDto[] =>
      Array.from({ length: count }, (_, i) =>
        makePosition({ instrumentId: `INST-${String(i + 1).padStart(3, '0')}` }),
      )

    it('should display only 50 rows per page by default', () => {
      render(<PositionGrid positions={makeManyPositions(75)} />)

      const rows = screen.getAllByTestId(/^position-row-/)
      expect(rows).toHaveLength(50)
    })

    it('should show page navigation controls', () => {
      render(<PositionGrid positions={makeManyPositions(75)} />)

      expect(screen.getByTestId('pagination-controls')).toBeInTheDocument()
      expect(screen.getByTestId('pagination-prev')).toBeInTheDocument()
      expect(screen.getByTestId('pagination-next')).toBeInTheDocument()
    })

    it('should navigate to next page', async () => {
      const user = userEvent.setup()
      render(<PositionGrid positions={makeManyPositions(75)} />)

      await user.click(screen.getByTestId('pagination-next'))

      const rows = screen.getAllByTestId(/^position-row-/)
      expect(rows).toHaveLength(25)
      expect(rows[0]).toHaveAttribute('data-testid', 'position-row-INST-051')
    })

    it('should navigate to previous page', async () => {
      const user = userEvent.setup()
      render(<PositionGrid positions={makeManyPositions(75)} />)

      // Go to page 2
      await user.click(screen.getByTestId('pagination-next'))
      // Go back to page 1
      await user.click(screen.getByTestId('pagination-prev'))

      const rows = screen.getAllByTestId(/^position-row-/)
      expect(rows).toHaveLength(50)
      expect(rows[0]).toHaveAttribute('data-testid', 'position-row-INST-001')
    })

    it('should show current page and total pages', () => {
      render(<PositionGrid positions={makeManyPositions(75)} />)

      expect(screen.getByTestId('pagination-info')).toHaveTextContent('Page 1 of 2')
    })

    it('should disable previous on first page', () => {
      render(<PositionGrid positions={makeManyPositions(75)} />)

      expect(screen.getByTestId('pagination-prev')).toBeDisabled()
    })

    it('should disable next on last page', async () => {
      const user = userEvent.setup()
      render(<PositionGrid positions={makeManyPositions(75)} />)

      await user.click(screen.getByTestId('pagination-next'))

      expect(screen.getByTestId('pagination-next')).toBeDisabled()
    })

    it('should not show pagination when positions fit on one page', () => {
      render(<PositionGrid positions={makeManyPositions(30)} />)

      expect(screen.queryByTestId('pagination-controls')).not.toBeInTheDocument()
    })
  })
})
