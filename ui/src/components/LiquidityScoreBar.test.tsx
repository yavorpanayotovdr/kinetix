import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { LiquidityScoreBar } from './LiquidityScoreBar'
import type { LiquidityTier } from '../types'

describe('LiquidityScoreBar', () => {
  it('shows HIGH_LIQUID label with green color indicator', () => {
    render(<LiquidityScoreBar tier="HIGH_LIQUID" horizonDays={1} />)

    expect(screen.getByTestId('liquidity-score-bar')).toBeDefined()
    expect(screen.getByTestId('tier-label').textContent).toBe('HIGH LIQUID')
    expect(screen.getByTestId('horizon-days').textContent).toContain('1')
  })

  it('shows LIQUID tier label', () => {
    render(<LiquidityScoreBar tier="LIQUID" horizonDays={3} />)

    expect(screen.getByTestId('tier-label').textContent).toBe('LIQUID')
    expect(screen.getByTestId('horizon-days').textContent).toContain('3')
  })

  it('shows SEMI_LIQUID tier label', () => {
    render(<LiquidityScoreBar tier="SEMI_LIQUID" horizonDays={5} />)

    expect(screen.getByTestId('tier-label').textContent).toBe('SEMI LIQUID')
  })

  it('shows ILLIQUID tier label with warning styling', () => {
    render(<LiquidityScoreBar tier="ILLIQUID" horizonDays={10} />)

    const bar = screen.getByTestId('liquidity-score-bar')
    expect(screen.getByTestId('tier-label').textContent).toBe('ILLIQUID')
    expect(bar.getAttribute('data-tier')).toBe('ILLIQUID')
  })

  it('shows ADV missing warning when advMissing is true', () => {
    render(
      <LiquidityScoreBar tier="ILLIQUID" horizonDays={10} advMissing={true} />,
    )

    expect(screen.getByTestId('adv-missing-warning')).toBeDefined()
  })

  it('shows ADV stale warning when advStale is true', () => {
    render(
      <LiquidityScoreBar tier="SEMI_LIQUID" horizonDays={5} advStale={true} />,
    )

    expect(screen.getByTestId('adv-stale-warning')).toBeDefined()
  })

  it('does not show warnings when ADV data is fresh', () => {
    render(
      <LiquidityScoreBar
        tier="HIGH_LIQUID"
        horizonDays={1}
        advMissing={false}
        advStale={false}
      />,
    )

    expect(screen.queryByTestId('adv-missing-warning')).toBeNull()
    expect(screen.queryByTestId('adv-stale-warning')).toBeNull()
  })

  it.each<[LiquidityTier, string]>([
    ['HIGH_LIQUID', 'HIGH LIQUID'],
    ['LIQUID', 'LIQUID'],
    ['SEMI_LIQUID', 'SEMI LIQUID'],
    ['ILLIQUID', 'ILLIQUID'],
  ])('renders label "%s" for tier %s', (tier, expectedLabel) => {
    render(<LiquidityScoreBar tier={tier} horizonDays={1} />)
    expect(screen.getByTestId('tier-label').textContent).toBe(expectedLabel)
  })
})
