import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { PortfolioAggregationDto } from '../types'
import { PortfolioSummaryCard } from './PortfolioSummaryCard'

const summary: PortfolioAggregationDto = {
  portfolioId: 'port-1',
  baseCurrency: 'USD',
  totalNav: { amount: '38050.00', currency: 'USD' },
  totalUnrealizedPnl: { amount: '1500.00', currency: 'USD' },
  currencyBreakdown: [
    {
      currency: 'USD',
      localValue: { amount: '21500.00', currency: 'USD' },
      baseValue: { amount: '21500.00', currency: 'USD' },
      fxRate: '1',
    },
    {
      currency: 'EUR',
      localValue: { amount: '18000.00', currency: 'EUR' },
      baseValue: { amount: '16550.00', currency: 'USD' },
      fxRate: '1.10',
    },
  ],
}

describe('PortfolioSummaryCard', () => {
  it('renders total NAV', () => {
    render(
      <PortfolioSummaryCard
        summary={summary}
        baseCurrency="USD"
        onBaseCurrencyChange={vi.fn()}
      />,
    )

    expect(screen.getByTestId('total-nav')).toBeInTheDocument()
    expect(screen.getByTestId('total-nav')).toHaveTextContent('38,050.00')
  })

  it('renders total unrealized P&L', () => {
    render(
      <PortfolioSummaryCard
        summary={summary}
        baseCurrency="USD"
        onBaseCurrencyChange={vi.fn()}
      />,
    )

    expect(screen.getByTestId('total-unrealized-pnl')).toBeInTheDocument()
    expect(screen.getByTestId('total-unrealized-pnl')).toHaveTextContent('1,500.00')
  })

  it('renders currency breakdown rows', () => {
    render(
      <PortfolioSummaryCard
        summary={summary}
        baseCurrency="USD"
        onBaseCurrencyChange={vi.fn()}
      />,
    )

    expect(screen.getByTestId('currency-row-USD')).toBeInTheDocument()
    expect(screen.getByTestId('currency-row-EUR')).toBeInTheDocument()
  })

  it('shows base currency selector', () => {
    render(
      <PortfolioSummaryCard
        summary={summary}
        baseCurrency="USD"
        onBaseCurrencyChange={vi.fn()}
      />,
    )

    const selector = screen.getByTestId('base-currency-selector')
    expect(selector).toBeInTheDocument()
    expect(selector).toHaveValue('USD')
  })

  it('calls onBaseCurrencyChange when currency is changed', async () => {
    const user = userEvent.setup()
    const onBaseCurrencyChange = vi.fn()

    render(
      <PortfolioSummaryCard
        summary={summary}
        baseCurrency="USD"
        onBaseCurrencyChange={onBaseCurrencyChange}
      />,
    )

    await user.selectOptions(screen.getByTestId('base-currency-selector'), 'EUR')
    expect(onBaseCurrencyChange).toHaveBeenCalledWith('EUR')
  })

  it('applies green colour to positive P&L', () => {
    render(
      <PortfolioSummaryCard
        summary={summary}
        baseCurrency="USD"
        onBaseCurrencyChange={vi.fn()}
      />,
    )

    expect(screen.getByTestId('total-unrealized-pnl').className).toContain('text-green-600')
  })

  it('applies red colour to negative P&L', () => {
    const negativeSummary: PortfolioAggregationDto = {
      ...summary,
      totalUnrealizedPnl: { amount: '-500.00', currency: 'USD' },
    }

    render(
      <PortfolioSummaryCard
        summary={negativeSummary}
        baseCurrency="USD"
        onBaseCurrencyChange={vi.fn()}
      />,
    )

    expect(screen.getByTestId('total-unrealized-pnl').className).toContain('text-red-600')
  })

  it('renders loading state when summary is null', () => {
    render(
      <PortfolioSummaryCard
        summary={null}
        baseCurrency="USD"
        onBaseCurrencyChange={vi.fn()}
        loading={true}
      />,
    )

    expect(screen.getByTestId('portfolio-summary-loading')).toBeInTheDocument()
  })
})
