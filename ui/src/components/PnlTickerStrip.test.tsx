import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { IntradayPnlSnapshotDto } from '../types'
import { PnlTickerStrip } from './PnlTickerStrip'

const makeSnapshot = (overrides: Partial<IntradayPnlSnapshotDto> = {}): IntradayPnlSnapshotDto => ({
  snapshotAt: '2026-03-24T09:30:00Z',
  baseCurrency: 'USD',
  trigger: 'position_change',
  totalPnl: '1500.00',
  realisedPnl: '500.00',
  unrealisedPnl: '1000.00',
  deltaPnl: '1200.00',
  gammaPnl: '80.00',
  vegaPnl: '40.00',
  thetaPnl: '-15.00',
  rhoPnl: '7.00',
  unexplainedPnl: '188.00',
  highWaterMark: '1800.00',
  correlationId: 'corr-1',
  ...overrides,
})

describe('PnlTickerStrip', () => {
  it('renders nothing when latest is null', () => {
    const { container } = render(
      <PnlTickerStrip bookId="book-1" latest={null} connected={false} />,
    )
    expect(container.firstChild).toBeNull()
  })

  it('displays total P&L when latest snapshot is provided', () => {
    render(
      <PnlTickerStrip bookId="book-1" latest={makeSnapshot()} connected={true} />,
    )

    expect(screen.getByTestId('ticker-total-pnl')).toHaveTextContent('1,500.00')
  })

  it('displays realised P&L', () => {
    render(
      <PnlTickerStrip bookId="book-1" latest={makeSnapshot()} connected={true} />,
    )

    expect(screen.getByTestId('ticker-realised-pnl')).toHaveTextContent('500.00')
  })

  it('displays unrealised P&L', () => {
    render(
      <PnlTickerStrip bookId="book-1" latest={makeSnapshot()} connected={true} />,
    )

    expect(screen.getByTestId('ticker-unrealised-pnl')).toHaveTextContent('1,000.00')
  })

  it('displays high-water mark', () => {
    render(
      <PnlTickerStrip bookId="book-1" latest={makeSnapshot()} connected={true} />,
    )

    expect(screen.getByTestId('ticker-hwm')).toHaveTextContent('1,800.00')
  })

  it('applies green colour to positive total P&L', () => {
    render(
      <PnlTickerStrip bookId="book-1" latest={makeSnapshot({ totalPnl: '1500.00' })} connected={true} />,
    )

    expect(screen.getByTestId('ticker-total-pnl').className).toContain('text-green-600')
  })

  it('applies red colour to negative total P&L', () => {
    render(
      <PnlTickerStrip bookId="book-1" latest={makeSnapshot({ totalPnl: '-250.00' })} connected={true} />,
    )

    expect(screen.getByTestId('ticker-total-pnl').className).toContain('text-red-600')
  })

  it('shows a connected indicator when connected is true', () => {
    render(
      <PnlTickerStrip bookId="book-1" latest={makeSnapshot()} connected={true} />,
    )

    expect(screen.getByTestId('ticker-connection-status')).toBeInTheDocument()
    expect(screen.getByTestId('ticker-connection-status').className).toContain('bg-green-500')
  })

  it('shows a disconnected indicator when connected is false', () => {
    render(
      <PnlTickerStrip bookId="book-1" latest={makeSnapshot()} connected={false} />,
    )

    expect(screen.getByTestId('ticker-connection-status').className).toContain('bg-red-500')
  })

  it('displays the trigger label', () => {
    render(
      <PnlTickerStrip
        bookId="book-1"
        latest={makeSnapshot({ trigger: 'position_change' })}
        connected={true}
      />,
    )

    expect(screen.getByTestId('ticker-trigger')).toHaveTextContent('position_change')
  })

  it('displays the snapshot time', () => {
    render(
      <PnlTickerStrip
        bookId="book-1"
        latest={makeSnapshot({ snapshotAt: '2026-03-24T09:30:00Z' })}
        connected={true}
      />,
    )

    // Should display formatted time (HH:MM:SS)
    expect(screen.getByTestId('ticker-snapshot-time')).toBeInTheDocument()
  })

  it('shows missing FX rate warning when missingFxRates is non-empty', () => {
    render(
      <PnlTickerStrip
        bookId="book-1"
        latest={makeSnapshot({ missingFxRates: ['USD/JPY', 'EUR/GBP'] })}
        connected={true}
      />,
    )

    const warning = screen.getByTestId('ticker-missing-fx-rates')
    expect(warning).toBeInTheDocument()
    expect(warning).toHaveTextContent('USD/JPY')
    expect(warning).toHaveTextContent('EUR/GBP')
  })

  it('does not show missing FX rate warning when missingFxRates is empty', () => {
    render(
      <PnlTickerStrip
        bookId="book-1"
        latest={makeSnapshot({ missingFxRates: [] })}
        connected={true}
      />,
    )

    expect(screen.queryByTestId('ticker-missing-fx-rates')).not.toBeInTheDocument()
  })

  it('does not show missing FX rate warning when missingFxRates is absent', () => {
    render(
      <PnlTickerStrip
        bookId="book-1"
        latest={makeSnapshot()}
        connected={true}
      />,
    )

    expect(screen.queryByTestId('ticker-missing-fx-rates')).not.toBeInTheDocument()
  })
})
