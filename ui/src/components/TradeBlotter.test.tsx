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
    bookId: 'book-1',
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    side: 'BUY',
    quantity: '100',
    price: { amount: '150.00', currency: 'USD' },
    tradedAt: '2025-01-15T10:00:00Z',
  },
  {
    tradeId: 't-2',
    bookId: 'book-1',
    instrumentId: 'MSFT',
    assetClass: 'EQUITY',
    side: 'SELL',
    quantity: '50',
    price: { amount: '300.00', currency: 'USD' },
    tradedAt: '2025-01-15T11:00:00Z',
  },
  {
    tradeId: 't-3',
    bookId: 'book-1',
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
    render(<TradeBlotter bookId="book-1" />)

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
    render(<TradeBlotter bookId="book-1" />)

    expect(screen.getByTestId('trade-row-t-1')).toBeInTheDocument()
    expect(screen.getByTestId('trade-row-t-2')).toBeInTheDocument()
    expect(screen.getByTestId('trade-row-t-3')).toBeInTheDocument()
  })

  it('shows empty state when no trades exist', () => {
    setupDefaults({ trades: [] })
    render(<TradeBlotter bookId="book-1" />)

    expect(screen.getByText('No trades to display.')).toBeInTheDocument()
  })

  it('shows loading state', () => {
    setupDefaults({ loading: true, trades: [] })
    render(<TradeBlotter bookId="book-1" />)

    expect(screen.getByText('Loading trades...')).toBeInTheDocument()
  })

  it('shows error state', () => {
    setupDefaults({ error: 'Failed to load', trades: [] })
    render(<TradeBlotter bookId="book-1" />)

    expect(screen.getByText('Failed to load')).toBeInTheDocument()
  })

  it('color-codes BUY trades in green and SELL trades in red', () => {
    setupDefaults()
    render(<TradeBlotter bookId="book-1" />)

    const buyRow = screen.getByTestId('trade-row-t-1')
    const sideCell = within(buyRow).getByTestId('trade-side-t-1')
    expect(sideCell.className).toContain('text-green')

    const sellRow = screen.getByTestId('trade-row-t-2')
    const sellSideCell = within(sellRow).getByTestId('trade-side-t-2')
    expect(sellSideCell.className).toContain('text-red')
  })

  it('sorts trades by time descending by default', () => {
    setupDefaults()
    render(<TradeBlotter bookId="book-1" />)

    const rows = screen.getAllByTestId(/^trade-row-/)
    expect(rows[0]).toHaveAttribute('data-testid', 'trade-row-t-2')
    expect(rows[1]).toHaveAttribute('data-testid', 'trade-row-t-1')
    expect(rows[2]).toHaveAttribute('data-testid', 'trade-row-t-3')
  })

  it('filters by instrument text input', () => {
    setupDefaults()
    render(<TradeBlotter bookId="book-1" />)

    const input = screen.getByTestId('filter-instrument')
    fireEvent.change(input, { target: { value: 'MSFT' } })

    expect(screen.getByTestId('trade-row-t-2')).toBeInTheDocument()
    expect(screen.queryByTestId('trade-row-t-1')).not.toBeInTheDocument()
    expect(screen.queryByTestId('trade-row-t-3')).not.toBeInTheDocument()
  })

  it('filters by side dropdown', () => {
    setupDefaults()
    render(<TradeBlotter bookId="book-1" />)

    const select = screen.getByTestId('filter-side')
    fireEvent.change(select, { target: { value: 'SELL' } })

    expect(screen.getByTestId('trade-row-t-2')).toBeInTheDocument()
    expect(screen.queryByTestId('trade-row-t-1')).not.toBeInTheDocument()
    expect(screen.queryByTestId('trade-row-t-3')).not.toBeInTheDocument()
  })

  it('renders CSV export button', () => {
    setupDefaults()
    render(<TradeBlotter bookId="book-1" />)

    expect(screen.getByTestId('csv-export-button')).toBeInTheDocument()
  })

  it('displays notional value as quantity times price', () => {
    setupDefaults()
    render(<TradeBlotter bookId="book-1" />)

    const row = screen.getByTestId('trade-row-t-1')
    const notional = within(row).getByTestId('trade-notional-t-1')
    // 100 * 150.00 = 15000 -> compact: $15K
    expect(notional.textContent).toContain('$15K')
  })

  describe('instrument type filter', () => {
    const tradesWithTypes: TradeHistoryDto[] = [
      {
        tradeId: 'tx-1',
        bookId: 'book-1',
        instrumentId: 'AAPL',
        assetClass: 'EQUITY',
        side: 'BUY',
        quantity: '100',
        price: { amount: '150.00', currency: 'USD' },
        tradedAt: '2025-01-15T10:00:00Z',
        instrumentType: 'CASH_EQUITY',
      },
      {
        tradeId: 'tx-2',
        bookId: 'book-1',
        instrumentId: 'AAPL-OPT',
        assetClass: 'EQUITY',
        side: 'BUY',
        quantity: '10',
        price: { amount: '5.00', currency: 'USD' },
        tradedAt: '2025-01-15T11:00:00Z',
        instrumentType: 'EQUITY_OPTION',
      },
      {
        tradeId: 'tx-3',
        bookId: 'book-1',
        instrumentId: 'US10Y',
        assetClass: 'FIXED_INCOME',
        side: 'SELL',
        quantity: '500',
        price: { amount: '980.00', currency: 'USD' },
        tradedAt: '2025-01-15T12:00:00Z',
        instrumentType: 'GOVERNMENT_BOND',
      },
    ]

    function setupWithTypes() {
      mockUseTradeHistory.mockReturnValue({
        trades: tradesWithTypes,
        loading: false,
        error: null,
        refetch: vi.fn(),
      })
    }

    it('renders an instrument type filter dropdown', () => {
      setupWithTypes()
      render(<TradeBlotter bookId="book-1" />)

      expect(screen.getByTestId('filter-instrument-type')).toBeInTheDocument()
    })

    it('defaults to showing all trades', () => {
      setupWithTypes()
      render(<TradeBlotter bookId="book-1" />)

      const rows = screen.getAllByTestId(/^trade-row-/)
      expect(rows).toHaveLength(3)
    })

    it('filters trades to the selected instrument type', () => {
      setupWithTypes()
      render(<TradeBlotter bookId="book-1" />)

      fireEvent.change(screen.getByTestId('filter-instrument-type'), { target: { value: 'EQUITY_OPTION' } })

      expect(screen.getByTestId('trade-row-tx-2')).toBeInTheDocument()
      expect(screen.queryByTestId('trade-row-tx-1')).not.toBeInTheDocument()
      expect(screen.queryByTestId('trade-row-tx-3')).not.toBeInTheDocument()
    })

    it('restores all trades when filter is reset to All', () => {
      setupWithTypes()
      render(<TradeBlotter bookId="book-1" />)

      fireEvent.change(screen.getByTestId('filter-instrument-type'), { target: { value: 'EQUITY_OPTION' } })
      fireEvent.change(screen.getByTestId('filter-instrument-type'), { target: { value: '' } })

      const rows = screen.getAllByTestId(/^trade-row-/)
      expect(rows).toHaveLength(3)
    })

    it('works in combination with instrument text and side filters', () => {
      setupWithTypes()
      render(<TradeBlotter bookId="book-1" />)

      fireEvent.change(screen.getByTestId('filter-instrument-type'), { target: { value: 'CASH_EQUITY' } })
      fireEvent.change(screen.getByTestId('filter-side'), { target: { value: 'BUY' } })

      expect(screen.getByTestId('trade-row-tx-1')).toBeInTheDocument()
      expect(screen.queryByTestId('trade-row-tx-2')).not.toBeInTheDocument()
      expect(screen.queryByTestId('trade-row-tx-3')).not.toBeInTheDocument()
    })

    it('renders instrument type badges in the type column', () => {
      setupWithTypes()
      render(<TradeBlotter bookId="book-1" />)

      expect(screen.getByText('Cash Equity')).toBeInTheDocument()
      expect(screen.getByText('Equity Option')).toBeInTheDocument()
      expect(screen.getByText('Government Bond')).toBeInTheDocument()
    })
  })
})
