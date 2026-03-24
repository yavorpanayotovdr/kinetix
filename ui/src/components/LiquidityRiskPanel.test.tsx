import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { LiquidityRiskPanel } from './LiquidityRiskPanel'
import type { LiquidityRiskResultDto } from '../types'

const sampleResult: LiquidityRiskResultDto = {
  bookId: 'BOOK-1',
  portfolioLvar: 316227.76,
  dataCompleteness: 0.85,
  portfolioConcentrationStatus: 'OK',
  calculatedAt: '2026-03-24T10:00:00Z',
  positionRisks: [
    {
      instrumentId: 'AAPL',
      assetClass: 'EQUITY',
      marketValue: 17000,
      tier: 'HIGH_LIQUID',
      horizonDays: 1,
      adv: 10000000,
      advMissing: false,
      advStale: false,
      lvarContribution: 316227.76,
      stressedLiquidationValue: 16500,
      concentrationStatus: 'OK',
    },
  ],
}

describe('LiquidityRiskPanel', () => {
  it('renders portfolio LVaR value', () => {
    render(
      <LiquidityRiskPanel result={sampleResult} loading={false} onRefresh={vi.fn()} />,
    )

    expect(screen.getByTestId('portfolio-lvar')).toBeDefined()
    expect(screen.getByTestId('portfolio-lvar').textContent).toContain('316')
  })

  it('renders data completeness as a percentage', () => {
    render(
      <LiquidityRiskPanel result={sampleResult} loading={false} onRefresh={vi.fn()} />,
    )

    expect(screen.getByTestId('data-completeness').textContent).toContain('85')
  })

  it('renders portfolio concentration status', () => {
    render(
      <LiquidityRiskPanel result={sampleResult} loading={false} onRefresh={vi.fn()} />,
    )

    expect(screen.getByTestId('concentration-status').textContent).toContain(
      'OK',
    )
  })

  it('renders a row for each position risk', () => {
    render(
      <LiquidityRiskPanel result={sampleResult} loading={false} onRefresh={vi.fn()} />,
    )

    expect(screen.getByTestId('position-row-AAPL')).toBeDefined()
  })

  it('shows loading spinner when loading', () => {
    render(
      <LiquidityRiskPanel result={null} loading={true} onRefresh={vi.fn()} />,
    )

    expect(screen.getByTestId('liquidity-loading')).toBeDefined()
  })

  it('shows empty state when no result and not loading', () => {
    render(
      <LiquidityRiskPanel result={null} loading={false} onRefresh={vi.fn()} />,
    )

    expect(screen.getByTestId('liquidity-empty')).toBeDefined()
  })

  it('calls onRefresh when refresh button is clicked', () => {
    const onRefresh = vi.fn()
    render(
      <LiquidityRiskPanel result={sampleResult} loading={false} onRefresh={onRefresh} />,
    )

    fireEvent.click(screen.getByTestId('liquidity-refresh'))
    expect(onRefresh).toHaveBeenCalledOnce()
  })

  it('shows BREACHED concentration status in red', () => {
    const breachedResult: LiquidityRiskResultDto = {
      ...sampleResult,
      portfolioConcentrationStatus: 'BREACHED',
    }
    render(
      <LiquidityRiskPanel result={breachedResult} loading={false} onRefresh={vi.fn()} />,
    )

    const status = screen.getByTestId('concentration-status')
    expect(status.getAttribute('data-status')).toBe('BREACHED')
  })

  it('shows ADV missing warning for positions without ADV data', () => {
    const noAdvResult: LiquidityRiskResultDto = {
      ...sampleResult,
      positionRisks: [
        {
          ...sampleResult.positionRisks[0],
          instrumentId: 'UNKNOWN-BOND',
          advMissing: true,
          tier: 'ILLIQUID',
        },
      ],
    }
    render(
      <LiquidityRiskPanel result={noAdvResult} loading={false} onRefresh={vi.fn()} />,
    )

    expect(screen.getByTestId('adv-missing-warning')).toBeDefined()
  })
})
