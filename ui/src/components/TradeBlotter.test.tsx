import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { TradeHistoryDto } from '../types'

vi.mock('../hooks/useTradeHistory')

import { useTradeHistory } from '../hooks/useTradeHistory'
import { TradeBlotter } from './TradeBlotter'

const mockUseTradeHistory = vi.mocked(useTradeHistory)

const trades: TradeHistoryDto[] = [
  {
    tradeId: 't-1',
    portfolioId: 'port-1',
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    side: 'BUY',
    quantity: '100',
    price: { amount: '150.00', currency: 'USD' },
    tradedAt: '2025-01-15T10:00:00Z',
  },
  {
    tradeId: 't-2',
    portfolioId: 'port-1',
    instrumentId: 'MSFT',
    assetClass: 'EQUITY',
    side: 'SELL',
    quantity: '50',
    price: { amount: '300.00', currency: 'USD' },
    tradedAt: '2025-01-15T11:00:00Z',
  },
  {
    tradeId: 't-3',
    portfolioId: 'port-1',
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    side: 'BUY',
    quantity: '200',
    price: { amount: '148.00', currency: 'USD' },
    tradedAt: '2025-01-14T09:00:00Z',
  },
]

function setupDefaults(overrides?: Partial<ReturnType<typeof useTradeHistory>>) {
  mockUseTradeHistory.mockReturnValue({
    trades,
    loading: false,
    error: null,
    refetch: vi.fn(),
    ...overrides,
  })
}

describe('TradeBlotter', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders trade history table with correct columns', () => {
    setupDefaults()
    render(<TradeBlotter portfolioId="port-1" />)

    expect(screen.getByText('Time')).toBeInTheDocument()
    expect(screen.getByText('Instrument')).toBeInTheDocument()
    expect(screen.getByText('Side')).toBeInTheDocument()
    expect(screen.getByText('Qty')).toBeInTheDocument()
    expect(screen.getByText('Price')).toBeInTheDocument()
    expect(screen.getByText('Notional')).toBeInTheDocument()
    expect(screen.getByText('Status')).toBeInTheDocument()
  })

  it('renders trade rows with correct data', () => {
    setupDefaults()
    render(<TradeBlotter portfolioId="port-1" />)

    expect(screen.getByTestId('trade-row-t-1')).toBeInTheDocument()
    expect(screen.getByTestId('trade-row-t-2')).toBeInTheDocument()
    expect(screen.getByTestId('trade-row-t-3')).toBeInTheDocument()
  })

  it('shows empty state when no trades exist', () => {
    setupDefaults({ trades: [] })
    render(<TradeBlotter portfolioId="port-1" />)

    expect(screen.getByText('No trades to display.')).toBeInTheDocument()
  })

  it('shows loading state', () => {
    setupDefaults({ loading: true, trades: [] })
    render(<TradeBlotter portfolioId="port-1" />)

    expect(screen.getByText('Loading trades...')).toBeInTheDocument()
  })

  it('shows error state', () => {
    setupDefaults({ error: 'Failed to load', trades: [] })
    render(<TradeBlotter portfolioId="port-1" />)

    expect(screen.getByText('Failed to load')).toBeInTheDocument()
  })

  it('color-codes BUY trades in green and SELL trades in red', () => {
    setupDefaults()
    render(<TradeBlotter portfolioId="port-1" />)

    const buyRow = screen.getByTestId('trade-row-t-1')
    const sideCell = within(buyRow).getByTestId('trade-side-t-1')
    expect(sideCell.className).toContain('text-green')

    const sellRow = screen.getByTestId('trade-row-t-2')
    const sellSideCell = within(sellRow).getByTestId('trade-side-t-2')
    expect(sellSideCell.className).toContain('text-red')
  })

  it('sorts trades by time descending by default', () => {
    setupDefaults()
    render(<TradeBlotter portfolioId="port-1" />)

    const rows = screen.getAllByTestId(/^trade-row-/)
    expect(rows[0]).toHaveAttribute('data-testid', 'trade-row-t-2')
    expect(rows[1]).toHaveAttribute('data-testid', 'trade-row-t-1')
    expect(rows[2]).toHaveAttribute('data-testid', 'trade-row-t-3')
  })

  it('filters by instrument text input', () => {
    setupDefaults()
    render(<TradeBlotter portfolioId="port-1" />)

    const input = screen.getByTestId('filter-instrument')
    fireEvent.change(input, { target: { value: 'MSFT' } })

    expect(screen.getByTestId('trade-row-t-2')).toBeInTheDocument()
    expect(screen.queryByTestId('trade-row-t-1')).not.toBeInTheDocument()
    expect(screen.queryByTestId('trade-row-t-3')).not.toBeInTheDocument()
  })

  it('filters by side dropdown', () => {
    setupDefaults()
    render(<TradeBlotter portfolioId="port-1" />)

    const select = screen.getByTestId('filter-side')
    fireEvent.change(select, { target: { value: 'SELL' } })

    expect(screen.getByTestId('trade-row-t-2')).toBeInTheDocument()
    expect(screen.queryByTestId('trade-row-t-1')).not.toBeInTheDocument()
    expect(screen.queryByTestId('trade-row-t-3')).not.toBeInTheDocument()
  })

  it('renders CSV export button', () => {
    setupDefaults()
    render(<TradeBlotter portfolioId="port-1" />)

    expect(screen.getByTestId('csv-export-button')).toBeInTheDocument()
  })

  it('displays notional value as quantity times price', () => {
    setupDefaults()
    render(<TradeBlotter portfolioId="port-1" />)

    const row = screen.getByTestId('trade-row-t-1')
    const notional = within(row).getByTestId('trade-notional-t-1')
    // 100 * 150.00 = 15000.00
    expect(notional.textContent).toContain('15,000')
  })
})
