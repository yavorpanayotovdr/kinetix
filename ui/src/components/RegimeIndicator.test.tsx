import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { RegimeIndicator } from './RegimeIndicator'
import type { MarketRegimeDto } from '../types'

const baseRegime: MarketRegimeDto = {
  regime: 'NORMAL',
  isConfirmed: true,
  confidence: 0.92,
  consecutiveObservations: 0,
  detectedAt: '2026-03-24T10:00:00Z',
  degradedInputs: false,
  signals: {
    realisedVol20d: 0.10,
    crossAssetCorrelation: 0.40,
    creditSpreadBps: null,
    pnlVolatility: null,
  },
  varParameters: {
    calculationType: 'PARAMETRIC',
    confidenceLevel: 'CL_95',
    timeHorizonDays: 1,
    correlationMethod: 'standard',
    numSimulations: null,
  },
}

function crisisRegime(): MarketRegimeDto {
  return {
    ...baseRegime,
    regime: 'CRISIS',
    confidence: 0.87,
    signals: {
      ...baseRegime.signals,
      realisedVol20d: 0.28,
      crossAssetCorrelation: 0.80,
    },
    varParameters: {
      calculationType: 'MONTE_CARLO',
      confidenceLevel: 'CL_99',
      timeHorizonDays: 5,
      correlationMethod: 'stressed',
      numSimulations: 50000,
    },
  }
}

describe('RegimeIndicator', () => {
  it('renders NORMAL regime badge in green', () => {
    render(<RegimeIndicator regime={baseRegime} loading={false} />)

    const badge = screen.getByTestId('regime-indicator')
    expect(badge).toBeDefined()
    expect(badge.textContent).toContain('NORMAL')
  })

  it('renders CRISIS regime badge in red', () => {
    render(<RegimeIndicator regime={crisisRegime()} loading={false} />)

    const badge = screen.getByTestId('regime-indicator')
    expect(badge.textContent).toContain('CRISIS')
  })

  it('renders ELEVATED_VOL badge in amber', () => {
    const elevatedRegime: MarketRegimeDto = { ...baseRegime, regime: 'ELEVATED_VOL' }
    render(<RegimeIndicator regime={elevatedRegime} loading={false} />)

    const badge = screen.getByTestId('regime-indicator')
    expect(badge.textContent).toContain('ELEVATED_VOL')
  })

  it('shows loading state when loading is true', () => {
    render(<RegimeIndicator regime={null} loading={true} />)

    expect(screen.getByTestId('regime-loading')).toBeDefined()
  })

  it('returns null when regime is null and not loading', () => {
    const { container } = render(<RegimeIndicator regime={null} loading={false} />)

    expect(container.firstChild).toBeNull()
  })

  it('opens detail panel on click showing VaR method', () => {
    render(<RegimeIndicator regime={crisisRegime()} loading={false} />)

    fireEvent.click(screen.getByTestId('regime-indicator'))

    expect(screen.getByTestId('regime-detail-panel')).toBeDefined()
    expect(screen.getByText('MONTE_CARLO')).toBeDefined()
  })

  it('shows regime confidence in detail panel', () => {
    render(<RegimeIndicator regime={crisisRegime()} loading={false} />)

    fireEvent.click(screen.getByTestId('regime-indicator'))

    const panel = screen.getByTestId('regime-detail-panel')
    expect(panel.textContent).toContain('87')
  })

  it('closes detail panel on second click', () => {
    render(<RegimeIndicator regime={crisisRegime()} loading={false} />)

    fireEvent.click(screen.getByTestId('regime-indicator'))
    expect(screen.getByTestId('regime-detail-panel')).toBeDefined()

    fireEvent.click(screen.getByTestId('regime-indicator'))
    expect(screen.queryByTestId('regime-detail-panel')).toBeNull()
  })

  it('shows degraded inputs warning when signals are incomplete', () => {
    const degradedRegime: MarketRegimeDto = { ...baseRegime, degradedInputs: true }
    render(<RegimeIndicator regime={degradedRegime} loading={false} />)

    fireEvent.click(screen.getByTestId('regime-indicator'))

    const panel = screen.getByTestId('regime-detail-panel')
    expect(panel.textContent).toContain('degraded')
  })
})
