import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FactorAttributionHistoryChart } from './FactorAttributionHistoryChart'
import type { FactorRiskDto } from '../types'

function makeSnapshot(
  calculatedAt: string,
  overrides: Partial<FactorRiskDto> = {},
): FactorRiskDto {
  return {
    bookId: 'BOOK-1',
    calculatedAt,
    totalVar: 50_000.0,
    systematicVar: 38_000.0,
    idiosyncraticVar: 12_000.0,
    rSquared: 0.576,
    concentrationWarning: false,
    factors: [
      {
        factorType: 'EQUITY_BETA',
        varContribution: 20_000.0,
        pctOfTotal: 40.0,
        loading: 1.12,
        loadingMethod: 'OLS',
      },
      {
        factorType: 'RATES_DURATION',
        varContribution: 10_000.0,
        pctOfTotal: 20.0,
        loading: -0.35,
        loadingMethod: 'OLS',
      },
    ],
    ...overrides,
  }
}

const sampleHistory: FactorRiskDto[] = [
  makeSnapshot('2026-03-20T10:00:00Z'),
  makeSnapshot('2026-03-21T10:00:00Z'),
  makeSnapshot('2026-03-22T10:00:00Z'),
]

describe('FactorAttributionHistoryChart', () => {
  it('shows loading spinner when loading', () => {
    render(
      <FactorAttributionHistoryChart history={[]} loading={true} error={null} />,
    )

    expect(screen.getByTestId('factor-history-loading')).toBeDefined()
  })

  it('shows empty state when history is empty and not loading', () => {
    render(
      <FactorAttributionHistoryChart history={[]} loading={false} error={null} />,
    )

    expect(screen.getByTestId('factor-history-empty')).toBeDefined()
  })

  it('shows error message when error is provided', () => {
    render(
      <FactorAttributionHistoryChart
        history={[]}
        loading={false}
        error="Request failed"
      />,
    )

    expect(screen.getByTestId('factor-history-error').textContent).toContain(
      'Request failed',
    )
  })

  it('renders the chart SVG when history is available', () => {
    render(
      <FactorAttributionHistoryChart
        history={sampleHistory}
        loading={false}
        error={null}
      />,
    )

    expect(screen.getByTestId('factor-history-chart')).toBeDefined()
  })

  it('renders a polyline for each factor type', () => {
    render(
      <FactorAttributionHistoryChart
        history={sampleHistory}
        loading={false}
        error={null}
      />,
    )

    expect(screen.getByTestId('factor-history-line-EQUITY_BETA')).toBeDefined()
    expect(screen.getByTestId('factor-history-line-RATES_DURATION')).toBeDefined()
  })

  it('renders a single point when history has one entry', () => {
    render(
      <FactorAttributionHistoryChart
        history={[makeSnapshot('2026-03-24T10:00:00Z')]}
        loading={false}
        error={null}
      />,
    )

    expect(screen.getByTestId('factor-history-chart')).toBeDefined()
    expect(screen.getByTestId('factor-history-line-EQUITY_BETA')).toBeDefined()
  })
})
