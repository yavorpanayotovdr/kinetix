import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import type { TimeRange } from '../types'
import { GreeksTrendChart } from './GreeksTrendChart'

const CONTAINER_WIDTH = 800

let observeCalls = 0

class FakeResizeObserver {
  callback: ResizeObserverCallback
  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
  }
  observe() {
    observeCalls++
    this.callback(
      [{ contentRect: { width: CONTAINER_WIDTH } } as unknown as ResizeObserverEntry],
      this as unknown as ResizeObserver,
    )
  }
  unobserve() {}
  disconnect() {}
}

beforeEach(() => {
  observeCalls = 0
  vi.stubGlobal('ResizeObserver', FakeResizeObserver)
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(CONTAINER_WIDTH)
})

const history: VaRHistoryEntry[] = [
  { varValue: 1_200_000, expectedShortfall: 1_500_000, calculatedAt: '2025-01-15T10:00:00Z', delta: 1500.8, gamma: 61.0, vega: 5001.0 },
  { varValue: 1_300_000, expectedShortfall: 1_600_000, calculatedAt: '2025-01-15T10:30:00Z', delta: 1600.2, gamma: 65.5, vega: 5200.3 },
  { varValue: 1_250_000, expectedShortfall: 1_550_000, calculatedAt: '2025-01-15T11:00:00Z', delta: 1550.0, gamma: 63.0, vega: 5100.0 },
  { varValue: 1_400_000, expectedShortfall: 1_700_000, calculatedAt: '2025-01-15T11:30:00Z', delta: 1700.5, gamma: 70.2, vega: 5400.8 },
  { varValue: 1_350_000, expectedShortfall: 1_650_000, calculatedAt: '2025-01-15T12:00:00Z', delta: 1650.0, gamma: 68.0, vega: 5300.0 },
]

describe('GreeksTrendChart', () => {
  it('renders empty state for zero data points', () => {
    render(<GreeksTrendChart history={[]} />)

    expect(screen.getByTestId('greeks-trend-chart')).toHaveTextContent('Collecting data...')
  })

  it('renders a single data point as a dot', () => {
    render(<GreeksTrendChart history={[history[0]]} />)

    const panel = screen.getByTestId('greeks-trend-chart')
    expect(panel).not.toHaveTextContent('Collecting data...')

    const svg = panel.querySelector('svg')
    expect(svg).toBeInTheDocument()

    const dot = svg!.querySelector('circle[data-testid="single-point-dot"]')
    expect(dot).toBeInTheDocument()
  })

  it('renders the chart panel with header', () => {
    render(<GreeksTrendChart history={history} />)

    const panel = screen.getByTestId('greeks-trend-chart')
    expect(panel).toBeInTheDocument()
    expect(panel).toHaveTextContent('Greeks Trend')
  })

  it('renders three polylines for delta, gamma, and vega', () => {
    render(<GreeksTrendChart history={history} />)

    const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!

    // Delta = blue (#3b82f6)
    const deltaLine = svg.querySelector('polyline[stroke="#3b82f6"]')
    expect(deltaLine).toBeInTheDocument()

    // Gamma = green (#22c55e)
    const gammaLine = svg.querySelector('polyline[stroke="#22c55e"]')
    expect(gammaLine).toBeInTheDocument()

    // Vega = purple (#a855f7")
    const vegaLine = svg.querySelector('polyline[stroke="#a855f7"]')
    expect(vegaLine).toBeInTheDocument()
  })

  it('renders a legend with delta, gamma, and vega labels', () => {
    render(<GreeksTrendChart history={history} />)

    const chart = screen.getByTestId('greeks-trend-chart')
    expect(chart).toHaveTextContent('Delta')
    expect(chart).toHaveTextContent('Gamma')
    expect(chart).toHaveTextContent('Vega')
  })

  it('shows crosshair and tooltip on hover', () => {
    render(<GreeksTrendChart history={history} />)

    const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!
    fireEvent.mouseMove(svg, { clientX: 200, clientY: 100 })

    const crosshair = svg.querySelector('line[data-testid="crosshair"]')
    expect(crosshair).toBeInTheDocument()

    const tooltip = screen.getByTestId('greeks-trend-tooltip')
    expect(tooltip).toBeInTheDocument()
  })

  it('tooltip shows delta, gamma, and vega values', () => {
    render(<GreeksTrendChart history={history} />)

    const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!
    fireEvent.mouseMove(svg, { clientX: 200, clientY: 100 })

    const tooltip = screen.getByTestId('greeks-trend-tooltip')
    expect(tooltip).toHaveTextContent(/Delta/)
    expect(tooltip).toHaveTextContent(/Gamma/)
    expect(tooltip).toHaveTextContent(/Vega/)
  })

  it('hides crosshair and tooltip on mouse leave', () => {
    render(<GreeksTrendChart history={history} />)

    const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!
    fireEvent.mouseMove(svg, { clientX: 200, clientY: 100 })
    expect(screen.getByTestId('greeks-trend-tooltip')).toBeInTheDocument()

    fireEvent.mouseLeave(svg)
    expect(screen.queryByTestId('greeks-trend-tooltip')).not.toBeInTheDocument()
  })

  it('uses ResizeObserver for responsive width', () => {
    render(<GreeksTrendChart history={history} />)

    expect(observeCalls).toBeGreaterThan(0)
  })

  it('renders Y-axis grid lines', () => {
    render(<GreeksTrendChart history={history} />)

    const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!
    const gridLines = svg.querySelectorAll('line[stroke-dasharray]')
    expect(gridLines.length).toBeGreaterThanOrEqual(2)
  })

  it('renders X-axis timestamp labels', () => {
    render(<GreeksTrendChart history={history} />)

    const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!
    const xLabels = svg.querySelectorAll('text[text-anchor="middle"]')
    expect(xLabels.length).toBeGreaterThanOrEqual(2)
  })

  describe('zoom interaction', () => {
    it('renders reset zoom button when zoomDepth > 0', () => {
      render(
        <GreeksTrendChart
          history={history}
          onZoom={vi.fn()}
          zoomDepth={1}
          onResetZoom={vi.fn()}
        />,
      )

      expect(screen.getByTestId('reset-zoom')).toBeInTheDocument()
    })

    it('hides reset zoom button when zoomDepth is 0', () => {
      render(
        <GreeksTrendChart
          history={history}
          onZoom={vi.fn()}
          zoomDepth={0}
          onResetZoom={vi.fn()}
        />,
      )

      expect(screen.queryByTestId('reset-zoom')).not.toBeInTheDocument()
    })

    it('calls onZoom with a Custom TimeRange on brush drag', () => {
      const onZoom = vi.fn()

      render(
        <GreeksTrendChart
          history={history}
          onZoom={onZoom}
          zoomDepth={0}
          onResetZoom={vi.fn()}
        />,
      )

      const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!

      fireEvent.mouseDown(svg, { clientX: 100, clientY: 50 })
      fireEvent.mouseMove(svg, { clientX: 300, clientY: 50 })
      fireEvent.mouseUp(svg)

      expect(onZoom).toHaveBeenCalledTimes(1)
      const zoomRange = onZoom.mock.calls[0][0] as TimeRange
      expect(zoomRange.label).toBe('Custom')
    })
  })

  it('handles entries without Greeks data gracefully', () => {
    const historyWithoutGreeks: VaRHistoryEntry[] = [
      { varValue: 1_200_000, expectedShortfall: 1_500_000, calculatedAt: '2025-01-15T10:00:00Z' },
      { varValue: 1_300_000, expectedShortfall: 1_600_000, calculatedAt: '2025-01-15T10:30:00Z' },
    ]

    render(<GreeksTrendChart history={historyWithoutGreeks} />)

    // Should show "Collecting data..." when no greeks values exist
    expect(screen.getByTestId('greeks-trend-chart')).toHaveTextContent('Collecting data...')
  })
})
