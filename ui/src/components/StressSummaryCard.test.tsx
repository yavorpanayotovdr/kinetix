import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { StressTestResultDto } from '../types'
import { StressSummaryCard } from './StressSummaryCard'

const stressResult: StressTestResultDto = {
  scenarioName: 'MARKET_CRASH',
  baseVar: '1000000',
  stressedVar: '2500000',
  pnlImpact: '-1500000',
  assetClassImpacts: [
    {
      assetClass: 'EQUITY',
      baseExposure: '5000000',
      stressedExposure: '3500000',
      pnlImpact: '-1500000',
    },
  ],
  calculatedAt: '2025-01-15T10:30:00Z',
}

const defaultProps = {
  result: null as StressTestResultDto | null,
  loading: false,
  onRun: vi.fn(),
  onViewDetails: vi.fn(),
}

describe('StressSummaryCard', () => {
  it('renders empty state when no result and not loading', () => {
    render(<StressSummaryCard {...defaultProps} />)

    expect(screen.getByTestId('stress-summary-card')).toBeInTheDocument()
    expect(screen.getByText(/no stress test results/i)).toBeInTheDocument()
  })

  it('renders Run Stress Tests button', () => {
    render(<StressSummaryCard {...defaultProps} />)

    expect(screen.getByTestId('stress-summary-run-btn')).toBeInTheDocument()
  })

  it('calls onRun when Run Stress Tests button is clicked', () => {
    const onRun = vi.fn()
    render(<StressSummaryCard {...defaultProps} onRun={onRun} />)

    fireEvent.click(screen.getByTestId('stress-summary-run-btn'))

    expect(onRun).toHaveBeenCalledOnce()
  })

  it('shows loading state on the run button', () => {
    render(<StressSummaryCard {...defaultProps} loading={true} />)

    const btn = screen.getByTestId('stress-summary-run-btn')
    expect(btn).toHaveTextContent('Running...')
  })

  it('renders the results table when result is present', () => {
    render(<StressSummaryCard {...defaultProps} result={stressResult} />)

    expect(screen.getByTestId('stress-summary-table')).toBeInTheDocument()
  })

  it('displays scenario name in the table', () => {
    render(<StressSummaryCard {...defaultProps} result={stressResult} />)

    expect(screen.getByText('MARKET CRASH')).toBeInTheDocument()
  })

  it('displays Base VaR formatted as currency', () => {
    render(<StressSummaryCard {...defaultProps} result={stressResult} />)

    expect(screen.getByText('$1,000,000.00')).toBeInTheDocument()
  })

  it('displays Stressed VaR formatted as currency', () => {
    render(<StressSummaryCard {...defaultProps} result={stressResult} />)

    expect(screen.getByText('$2,500,000.00')).toBeInTheDocument()
  })

  it('displays P&L Impact formatted as currency with red text for losses', () => {
    render(<StressSummaryCard {...defaultProps} result={stressResult} />)

    const pnlCell = screen.getByTestId('stress-summary-pnl-impact')
    expect(pnlCell).toHaveTextContent('-$1,500,000.00')
    expect(pnlCell.className).toContain('text-red-600')
  })

  it('does not apply red text to positive P&L impact', () => {
    const positiveResult: StressTestResultDto = {
      ...stressResult,
      pnlImpact: '500000',
    }

    render(<StressSummaryCard {...defaultProps} result={positiveResult} />)

    const pnlCell = screen.getByTestId('stress-summary-pnl-impact')
    expect(pnlCell).toHaveTextContent('$500,000.00')
    expect(pnlCell.className).not.toContain('text-red-600')
  })

  it('renders View Details link when result is present', () => {
    render(<StressSummaryCard {...defaultProps} result={stressResult} />)

    expect(screen.getByTestId('stress-summary-view-details')).toBeInTheDocument()
  })

  it('calls onViewDetails when View Details link is clicked', () => {
    const onViewDetails = vi.fn()
    render(
      <StressSummaryCard
        {...defaultProps}
        result={stressResult}
        onViewDetails={onViewDetails}
      />,
    )

    fireEvent.click(screen.getByTestId('stress-summary-view-details'))

    expect(onViewDetails).toHaveBeenCalledOnce()
  })

  it('does not render View Details link when no result', () => {
    render(<StressSummaryCard {...defaultProps} />)

    expect(screen.queryByTestId('stress-summary-view-details')).not.toBeInTheDocument()
  })
})
