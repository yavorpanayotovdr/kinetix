import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { PositionDto } from '../types'
import { formatMoney, pnlColorClass } from '../utils/format'
import { PositionGrid } from './PositionGrid'

describe('formatMoney', () => {
  it('formats USD with dollar sign and commas', () => {
    expect(formatMoney('1500.00', 'USD')).toBe('$1,500.00')
  })

  it('formats EUR with euro sign', () => {
    expect(formatMoney('2500.50', 'EUR')).toBe('\u20ac2,500.50')
  })

  it('formats negative amounts', () => {
    expect(formatMoney('-1234.56', 'USD')).toBe('-$1,234.56')
  })

  it('formats large numbers with thousands separators', () => {
    expect(formatMoney('1234567.89', 'USD')).toBe('$1,234,567.89')
  })

  it('falls back to amount + currency code for unknown currencies', () => {
    expect(formatMoney('100.00', 'XYZ')).toBe('100.00 XYZ')
  })
})

describe('pnlColorClass', () => {
  it('returns green for positive amounts', () => {
    expect(pnlColorClass('150.00')).toBe('text-green-600')
  })

  it('returns red for negative amounts', () => {
    expect(pnlColorClass('-50.00')).toBe('text-red-600')
  })

  it('returns gray for zero', () => {
    expect(pnlColorClass('0.00')).toBe('text-gray-500')
  })
})

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
})
