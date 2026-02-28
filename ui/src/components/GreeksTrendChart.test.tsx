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
  { varValue: 1_200_000, expectedShortfall: 1_500_000, calculatedAt: '2025-01-15T10:00:00Z', confidenceLevel: 'CL_95', delta: 1500.8, gamma: 61.0, vega: 5001.0, theta: -120.5 },
  { varValue: 1_300_000, expectedShortfall: 1_600_000, calculatedAt: '2025-01-15T10:30:00Z', confidenceLevel: 'CL_95', delta: 1600.2, gamma: 65.5, vega: 5200.3, theta: -135.2 },
  { varValue: 1_250_000, expectedShortfall: 1_550_000, calculatedAt: '2025-01-15T11:00:00Z', confidenceLevel: 'CL_95', delta: 1550.0, gamma: 63.0, vega: 5100.0, theta: -128.0 },
  { varValue: 1_400_000, expectedShortfall: 1_700_000, calculatedAt: '2025-01-15T11:30:00Z', confidenceLevel: 'CL_95', delta: 1700.5, gamma: 70.2, vega: 5400.8, theta: -142.8 },
  { varValue: 1_350_000, expectedShortfall: 1_650_000, calculatedAt: '2025-01-15T12:00:00Z', confidenceLevel: 'CL_95', delta: 1650.0, gamma: 68.0, vega: 5300.0, theta: -138.0 },
]

describe('GreeksTrendChart', () => {
  it('renders empty state for zero data points', () => {
    render(<GreeksTrendChart history={[]} />)

    expect(screen.getByTestId('greeks-trend-chart')).toHaveTextContent('Collecting data...')
  })

  it('shows message instead of chart for single data point', () => {
    render(<GreeksTrendChart history={[history[0]]} />)

    const panel = screen.getByTestId('greeks-trend-chart')
    expect(panel).toHaveTextContent('Trend data requires at least 2 calculations')
    expect(panel.querySelector('svg')).not.toBeInTheDocument()
  })

  it('renders the chart panel with header', () => {
    render(<GreeksTrendChart history={history} />)

    const panel = screen.getByTestId('greeks-trend-chart')
    expect(panel).toBeInTheDocument()
    expect(panel).toHaveTextContent('Greeks Trend')
  })

  it('renders four polylines for delta, gamma, vega, and theta', () => {
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

    // Theta = amber (#f59e0b)
    const thetaLine = svg.querySelector('polyline[stroke="#f59e0b"]')
    expect(thetaLine).toBeInTheDocument()
  })

  it('renders a legend with delta, gamma, vega, and theta labels', () => {
    render(<GreeksTrendChart history={history} />)

    const chart = screen.getByTestId('greeks-trend-chart')
    expect(chart).toHaveTextContent('Delta')
    expect(chart).toHaveTextContent('Gamma')
    expect(chart).toHaveTextContent('Vega')
    expect(chart).toHaveTextContent('Theta')
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

  it('tooltip shows delta, gamma, vega, and theta values', () => {
    render(<GreeksTrendChart history={history} />)

    const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!
    fireEvent.mouseMove(svg, { clientX: 200, clientY: 100 })

    const tooltip = screen.getByTestId('greeks-trend-tooltip')
    expect(tooltip).toHaveTextContent(/Delta/)
    expect(tooltip).toHaveTextContent(/Gamma/)
    expect(tooltip).toHaveTextContent(/Vega/)
    expect(tooltip).toHaveTextContent(/Theta/)
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

  it('renders hover dot for theta on mouse move', () => {
    render(<GreeksTrendChart history={history} />)

    const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!
    fireEvent.mouseMove(svg, { clientX: 200, clientY: 100 })

    const thetaDot = svg.querySelector('circle[data-testid="hover-dot-theta"]')
    expect(thetaDot).toBeInTheDocument()
  })

  it('renders chart when entries have theta but some are missing theta', () => {
    const mixedHistory: VaRHistoryEntry[] = [
      { varValue: 1_200_000, expectedShortfall: 1_500_000, calculatedAt: '2025-01-15T10:00:00Z', confidenceLevel: 'CL_95', delta: 1500.8, gamma: 61.0, vega: 5001.0, theta: -120.5 },
      { varValue: 1_300_000, expectedShortfall: 1_600_000, calculatedAt: '2025-01-15T10:30:00Z', confidenceLevel: 'CL_95', delta: 1600.2, gamma: 65.5, vega: 5200.3 },
      { varValue: 1_250_000, expectedShortfall: 1_550_000, calculatedAt: '2025-01-15T11:00:00Z', confidenceLevel: 'CL_95', delta: 1550.0, gamma: 63.0, vega: 5100.0, theta: -128.0 },
    ]

    render(<GreeksTrendChart history={mixedHistory} />)

    const svg = screen.getByTestId('greeks-trend-chart').querySelector('svg')!
    // Should still render delta/gamma/vega lines
    expect(svg.querySelector('polyline[stroke="#3b82f6"]')).toBeInTheDocument()
    expect(svg.querySelector('polyline[stroke="#22c55e"]')).toBeInTheDocument()
    expect(svg.querySelector('polyline[stroke="#a855f7"]')).toBeInTheDocument()
  })

  it('handles entries without Greeks data gracefully', () => {
    const historyWithoutGreeks: VaRHistoryEntry[] = [
      { varValue: 1_200_000, expectedShortfall: 1_500_000, calculatedAt: '2025-01-15T10:00:00Z', confidenceLevel: 'CL_95' },
      { varValue: 1_300_000, expectedShortfall: 1_600_000, calculatedAt: '2025-01-15T10:30:00Z', confidenceLevel: 'CL_95' },
    ]

    render(<GreeksTrendChart history={historyWithoutGreeks} />)

    // Should show "Collecting data..." when no greeks values exist
    expect(screen.getByTestId('greeks-trend-chart')).toHaveTextContent('Collecting data...')
  })
})
