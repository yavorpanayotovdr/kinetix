import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { VaRResultDto, GreeksResultDto, TimeRange } from '../types'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import { VaRDashboard } from './VaRDashboard'

const CONTAINER_WIDTH = 800

class FakeResizeObserver {
  callback: ResizeObserverCallback
  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
  }
  observe() {
    this.callback(
      [{ contentRect: { width: CONTAINER_WIDTH } } as unknown as ResizeObserverEntry],
      this as unknown as ResizeObserver,
    )
  }
  unobserve() {}
  disconnect() {}
}

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', FakeResizeObserver)
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(CONTAINER_WIDTH)
})

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

const defaultTimeRange: TimeRange = {
  from: '2025-01-14T10:30:00Z',
  to: '2025-01-15T10:30:00Z',
  label: 'Last 24h',
}

const greeksResult: GreeksResultDto = {
  portfolioId: 'port-1',
  assetClassGreeks: [
    { assetClass: 'EQUITY', delta: '1234.560000', gamma: '78.900000', vega: '5678.120000' },
    { assetClass: 'COMMODITY', delta: '567.890000', gamma: '12.340000', vega: '2345.670000' },
  ],
  theta: '-123.450000',
  rho: '456.780000',
  calculatedAt: '2025-01-15T10:00:00Z',
}

const defaultZoomProps = {
  timeRange: defaultTimeRange,
  setTimeRange: vi.fn(),
  filteredHistory: history,
  zoomIn: vi.fn(),
  resetZoom: vi.fn(),
  zoomDepth: 0,
  refreshing: false,
  greeksResult: null as GreeksResultDto | null,
}

describe('VaRDashboard', () => {
  it('shows loading state', () => {
    render(
      <VaRDashboard
        varResult={null}

        loading={true}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
        filteredHistory={[]}
      />,
    )

    expect(screen.getByTestId('var-loading')).toBeInTheDocument()
  })

  it('shows error state', () => {
    render(
      <VaRDashboard
        varResult={null}

        loading={false}
        error="Failed to fetch VaR"
        onRefresh={() => {}}
        {...defaultZoomProps}
        filteredHistory={[]}
      />,
    )

    expect(screen.getByTestId('var-error')).toBeInTheDocument()
    expect(screen.getByTestId('var-error')).toHaveTextContent('Failed to fetch VaR')
  })

  it('shows empty state when no result', () => {
    render(
      <VaRDashboard
        varResult={null}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
        filteredHistory={[]}
      />,
    )

    expect(screen.getByTestId('var-empty')).toBeInTheDocument()
  })

  it('renders the VaR gauge with data', () => {
    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
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

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
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

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
      />,
    )

    const trend = screen.getByTestId('var-trend-chart')
    expect(trend).toBeInTheDocument()
    expect(trend.querySelector('svg')).toBeInTheDocument()
  })

  it('renders a single-point dot with exactly 1 data point', () => {
    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
        filteredHistory={[history[0]]}
      />,
    )

    const trend = screen.getByTestId('var-trend-chart')
    expect(trend).not.toHaveTextContent('Collecting data...')
    expect(trend.querySelector('[data-testid="single-point-dot"]')).toBeInTheDocument()
  })

  it('uses a 4-column grid with full-width trend chart below', () => {
    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
      />,
    )

    const dashboard = screen.getByTestId('var-dashboard')
    const grid = dashboard.querySelector('.md\\:grid-cols-4')
    expect(grid).toBeInTheDocument()

    const trendChart = screen.getByTestId('var-trend-chart')
    expect(trendChart).toBeInTheDocument()
    expect(grid!.contains(trendChart)).toBe(false)
  })

  it('calls onRefresh when Recalculate button is clicked', () => {
    const onRefresh = vi.fn()

    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={onRefresh}
        {...defaultZoomProps}
      />,
    )

    fireEvent.click(screen.getByTestId('var-recalculate'))
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })

  it('displays calculation type and timestamp', () => {
    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
      />,
    )

    expect(screen.getByTestId('var-dashboard')).toHaveTextContent('HISTORICAL')
  })

  it('shows tooltip describing the calculation method', () => {
    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
      />,
    )

    const label = screen.getByTitle(/historical simulation/i)
    expect(label).toHaveTextContent('HISTORICAL')
  })

  it('shows parametric tooltip for PARAMETRIC calculation type', () => {
    render(
      <VaRDashboard
        varResult={{ ...varResult, calculationType: 'PARAMETRIC' }}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
      />,
    )

    const label = screen.getByTitle(/variance-covariance/i)
    expect(label).toHaveTextContent('PARAMETRIC')
  })

  it('shows Monte Carlo tooltip for MONTE_CARLO calculation type', () => {
    render(
      <VaRDashboard
        varResult={{ ...varResult, calculationType: 'MONTE_CARLO' }}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
      />,
    )

    const label = screen.getByTitle(/monte carlo/i)
    expect(label).toHaveTextContent('MONTE_CARLO')
  })

  it('shows spinner on Recalculate button when refreshing, not full loading state', () => {
    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
        refreshing={true}
      />,
    )

    // Dashboard should still be visible (not replaced by loading spinner)
    expect(screen.getByTestId('var-dashboard')).toBeInTheDocument()
    expect(screen.queryByTestId('var-loading')).not.toBeInTheDocument()

    // Recalculate button should show a spinner
    const button = screen.getByTestId('var-recalculate')
    expect(button.querySelector('.animate-spin')).toBeInTheDocument()
  })

  it('renders TimeRangeSelector with the time range', () => {
    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
      />,
    )

    expect(screen.getByTestId('time-range-selector')).toBeInTheDocument()
  })

  it('renders risk sensitivities when greeksResult is provided', () => {
    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
        greeksResult={greeksResult}
      />,
    )

    expect(screen.getByTestId('risk-sensitivities')).toBeInTheDocument()
    expect(screen.getByTestId('greeks-heatmap')).toBeInTheDocument()
  })

  it('passes pvValue to RiskSensitivities when present', () => {
    const varResultWithPv = { ...varResult, pvValue: '1800000.00' }
    render(
      <VaRDashboard
        varResult={varResultWithPv}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
        greeksResult={greeksResult}
      />,
    )

    expect(screen.getByTestId('pv-display')).toBeInTheDocument()
    expect(screen.getByTestId('pv-display')).toHaveTextContent('$1.8M')
  })

  it('renders placeholder when greeksResult is null', () => {
    render(
      <VaRDashboard
        varResult={varResult}

        loading={false}
        error={null}
        onRefresh={() => {}}
        {...defaultZoomProps}
        greeksResult={null}
      />,
    )

    expect(screen.getByTestId('sensitivities-placeholder')).toBeInTheDocument()
    expect(screen.queryByTestId('risk-sensitivities')).not.toBeInTheDocument()
  })

})
