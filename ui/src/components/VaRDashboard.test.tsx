import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { VaRResultDto } from '../types'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import { VaRDashboard } from './VaRDashboard'

const varResult: VaRResultDto = {
  portfolioId: 'port-1',
  calculationType: 'HISTORICAL',
  confidenceLevel: 'CL_95',
  varValue: '1234567.89',
  expectedShortfall: '1567890.12',
  componentBreakdown: [
    { assetClass: 'EQUITY', varContribution: '800000.00', percentageOfTotal: '64.85' },
    { assetClass: 'FIXED_INCOME', varContribution: '300000.00', percentageOfTotal: '24.30' },
    { assetClass: 'COMMODITY', varContribution: '134567.89', percentageOfTotal: '10.85' },
  ],
  calculatedAt: '2025-01-15T10:30:00Z',
}

const history: VaRHistoryEntry[] = [
  { varValue: 1200000, expectedShortfall: 1500000, calculatedAt: '2025-01-15T10:00:00Z' },
  { varValue: 1234567.89, expectedShortfall: 1567890.12, calculatedAt: '2025-01-15T10:30:00Z' },
  { varValue: 1300000, expectedShortfall: 1600000, calculatedAt: '2025-01-15T11:00:00Z' },
]

describe('VaRDashboard', () => {
  it('shows loading state', () => {
    render(
      <VaRDashboard
        varResult={null}
        history={[]}
        loading={true}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('var-loading')).toBeInTheDocument()
  })

  it('shows error state', () => {
    render(
      <VaRDashboard
        varResult={null}
        history={[]}
        loading={false}
        error="Failed to fetch VaR"
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('var-error')).toBeInTheDocument()
    expect(screen.getByTestId('var-error')).toHaveTextContent('Failed to fetch VaR')
  })

  it('shows empty state when no result', () => {
    render(
      <VaRDashboard
        varResult={null}
        history={[]}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('var-empty')).toBeInTheDocument()
  })

  it('renders the VaR gauge with data', () => {
    render(
      <VaRDashboard
        varResult={varResult}
        history={history}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('var-dashboard')).toBeInTheDocument()
    expect(screen.getByTestId('var-gauge')).toBeInTheDocument()
    expect(screen.getByTestId('var-value')).toHaveTextContent('$1,234,567.89')
  })

  it('renders component breakdown segments', () => {
    render(
      <VaRDashboard
        varResult={varResult}
        history={history}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('var-breakdown')).toBeInTheDocument()
    expect(screen.getByTestId('breakdown-EQUITY')).toBeInTheDocument()
    expect(screen.getByTestId('breakdown-FIXED_INCOME')).toBeInTheDocument()
    expect(screen.getByTestId('breakdown-COMMODITY')).toBeInTheDocument()
  })

  it('renders trend chart with sufficient data points', () => {
    render(
      <VaRDashboard
        varResult={varResult}
        history={history}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const trend = screen.getByTestId('var-trend')
    expect(trend).toBeInTheDocument()
    expect(trend.querySelector('svg')).toBeInTheDocument()
  })

  it('shows collecting data message with fewer than 2 points', () => {
    render(
      <VaRDashboard
        varResult={varResult}
        history={[history[0]]}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const trend = screen.getByTestId('var-trend')
    expect(trend).toHaveTextContent('Collecting data...')
  })

  it('calls onRefresh when Recalculate button is clicked', () => {
    const onRefresh = vi.fn()

    render(
      <VaRDashboard
        varResult={varResult}
        history={history}
        loading={false}
        error={null}
        onRefresh={onRefresh}
      />,
    )

    fireEvent.click(screen.getByTestId('var-recalculate'))
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })

  it('displays calculation type and timestamp', () => {
    render(
      <VaRDashboard
        varResult={varResult}
        history={history}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('var-dashboard')).toHaveTextContent('HISTORICAL')
  })
})
