import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import type { TimeRange } from '../types'
import { VaRTrendChart } from './VaRTrendChart'

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
  { varValue: 1_200_000, expectedShortfall: 1_500_000, calculatedAt: '2025-01-15T10:00:00Z' },
  { varValue: 1_300_000, expectedShortfall: 1_600_000, calculatedAt: '2025-01-15T10:30:00Z' },
  { varValue: 1_250_000, expectedShortfall: 1_550_000, calculatedAt: '2025-01-15T11:00:00Z' },
  { varValue: 1_400_000, expectedShortfall: 1_700_000, calculatedAt: '2025-01-15T11:30:00Z' },
  { varValue: 1_350_000, expectedShortfall: 1_650_000, calculatedAt: '2025-01-15T12:00:00Z' },
]

describe('VaRTrendChart', () => {
  it('renders empty state for zero data points', () => {
    render(<VaRTrendChart history={[]} />)

    expect(screen.getByTestId('var-trend-chart')).toHaveTextContent('Collecting data...')
  })

  it('renders a single data point as a dot instead of showing collecting message', () => {
    render(<VaRTrendChart history={[history[0]]} />)

    const panel = screen.getByTestId('var-trend-chart')
    expect(panel).not.toHaveTextContent('Collecting data...')

    const svg = panel.querySelector('svg')
    expect(svg).toBeInTheDocument()

    const dot = svg!.querySelector('circle[data-testid="single-point-dot"]')
    expect(dot).toBeInTheDocument()
    expect(dot).toHaveAttribute('fill', '#6366f1')
  })

  it('renders the chart panel with header', () => {
    render(<VaRTrendChart history={history} />)

    const panel = screen.getByTestId('var-trend-chart')
    expect(panel).toBeInTheDocument()
    expect(panel).toHaveTextContent('VaR Trend')
  })

  it('displays the latest VaR value in the header', () => {
    render(<VaRTrendChart history={history} />)

    expect(screen.getByTestId('var-trend-chart')).toHaveTextContent('$1,350,000')
  })

  it('renders an SVG with a polyline for the data', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')
    expect(svg).toBeInTheDocument()

    const polyline = svg!.querySelector('polyline[stroke="#6366f1"]')
    expect(polyline).toBeInTheDocument()
  })

  it('renders an area fill polygon', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')
    const polygon = svg!.querySelector('polygon[fill="rgba(99, 102, 241, 0.15)"]')
    expect(polygon).toBeInTheDocument()
  })

  it('renders Y-axis grid lines with labels', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')
    const gridLines = svg!.querySelectorAll('line[stroke-dasharray]')
    expect(gridLines.length).toBeGreaterThanOrEqual(3)

    const yLabels = svg!.querySelectorAll('text[text-anchor="end"]')
    expect(yLabels.length).toBeGreaterThanOrEqual(3)
  })

  it('renders X-axis timestamp labels', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')
    const xLabels = svg!.querySelectorAll('text[text-anchor="middle"]')
    expect(xLabels.length).toBeGreaterThanOrEqual(2)
  })

  it('shows crosshair and tooltip on hover', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')!
    fireEvent.mouseMove(svg, { clientX: 200, clientY: 100 })

    const crosshair = svg.querySelector('line[data-testid="crosshair"]')
    expect(crosshair).toBeInTheDocument()

    const tooltip = screen.getByTestId('var-trend-tooltip')
    expect(tooltip).toBeInTheDocument()
  })

  it('shows hover dot on mousemove', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')!
    fireEvent.mouseMove(svg, { clientX: 200, clientY: 100 })

    const dot = svg.querySelector('circle[data-testid="hover-dot"]')
    expect(dot).toBeInTheDocument()
  })

  it('hides crosshair and tooltip on mouse leave', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')!
    fireEvent.mouseMove(svg, { clientX: 200, clientY: 100 })
    expect(screen.getByTestId('var-trend-tooltip')).toBeInTheDocument()

    fireEvent.mouseLeave(svg)
    expect(screen.queryByTestId('var-trend-tooltip')).not.toBeInTheDocument()
  })

  it('uses ResizeObserver for responsive width', () => {
    render(<VaRTrendChart history={history} />)

    expect(observeCalls).toBeGreaterThan(0)
  })

  it('X-axis labels reflect the selected time range, not just the data extent', () => {
    // All data is within 2 hours, but the Custom timeRange covers 7 days
    const weekRange: TimeRange = {
      from: '2025-01-08T12:00:00Z',
      to: '2025-01-15T12:00:00Z',
      label: 'Custom',
    }

    const { rerender } = render(
      <VaRTrendChart history={history} timeRange={weekRange} />,
    )

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')!
    const weekLabels = Array.from(svg.querySelectorAll('text[text-anchor="middle"]')).map(
      (el) => el.textContent,
    )

    // 7-day range uses "Mon DD" format (from formatChartTime)
    expect(weekLabels.some((l) => l?.includes('Jan'))).toBe(true)

    // Switch to a 1-hour Custom range — labels should change to "HH:MM" format
    const hourRange: TimeRange = {
      from: '2025-01-15T11:00:00Z',
      to: '2025-01-15T12:00:00Z',
      label: 'Custom',
    }

    rerender(<VaRTrendChart history={history} timeRange={hourRange} />)

    const hourLabels = Array.from(svg.querySelectorAll('text[text-anchor="middle"]')).map(
      (el) => el.textContent,
    )

    // 1-hour range uses "HH:MM" format — should NOT contain "Jan"
    expect(hourLabels.some((l) => l?.includes('Jan'))).toBe(false)
    expect(hourLabels.some((l) => /^\d{2}:\d{2}$/.test(l ?? ''))).toBe(true)
  })

  it('renders ES polyline with amber stroke', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')!
    const polylines = svg.querySelectorAll('polyline')
    const esPolyline = Array.from(polylines).find((p) => p.getAttribute('stroke') === '#f59e0b')
    expect(esPolyline).toBeInTheDocument()
  })

  it('renders ES area fill polygon', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')!
    const polygons = svg.querySelectorAll('polygon')
    const esArea = Array.from(polygons).find((p) => p.getAttribute('fill') === 'rgba(245, 158, 11, 0.10)')
    expect(esArea).toBeInTheDocument()
  })

  it('shows ES hover dot on mousemove', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')!
    fireEvent.mouseMove(svg, { clientX: 200, clientY: 100 })

    const dot = svg.querySelector('circle[data-testid="hover-dot-es"]')
    expect(dot).toBeInTheDocument()
    expect(dot).toHaveAttribute('fill', '#f59e0b')
  })

  it('renders a legend with VaR and ES labels', () => {
    render(<VaRTrendChart history={history} />)

    const chart = screen.getByTestId('var-trend-chart')
    expect(chart).toHaveTextContent('VaR')
    expect(chart).toHaveTextContent('ES')

    const legend = chart.querySelector('.bg-indigo-500')
    expect(legend).toBeInTheDocument()
    const esLegend = chart.querySelector('.bg-amber-500')
    expect(esLegend).toBeInTheDocument()
  })

  describe('zoom interaction', () => {
    it('renders reset zoom button when zoomDepth > 0', () => {
      render(
        <VaRTrendChart
          history={history}
          onZoom={vi.fn()}
          zoomDepth={1}
          onResetZoom={vi.fn()}
        />,
      )

      expect(screen.getByTestId('reset-zoom')).toBeInTheDocument()
      expect(screen.getByTestId('reset-zoom')).toHaveTextContent('Reset zoom')
    })

    it('hides reset zoom button when zoomDepth is 0', () => {
      render(
        <VaRTrendChart
          history={history}
          onZoom={vi.fn()}
          zoomDepth={0}
          onResetZoom={vi.fn()}
        />,
      )

      expect(screen.queryByTestId('reset-zoom')).not.toBeInTheDocument()
    })

    it('calls onResetZoom when reset zoom button is clicked', () => {
      const onResetZoom = vi.fn()

      render(
        <VaRTrendChart
          history={history}
          onZoom={vi.fn()}
          zoomDepth={2}
          onResetZoom={onResetZoom}
        />,
      )

      fireEvent.click(screen.getByTestId('reset-zoom'))
      expect(onResetZoom).toHaveBeenCalledTimes(1)
    })

    it('calls onZoom with a Custom TimeRange on brush drag', () => {
      const onZoom = vi.fn()

      render(
        <VaRTrendChart
          history={history}
          onZoom={onZoom}
          zoomDepth={0}
          onResetZoom={vi.fn()}
        />,
      )

      const svg = screen.getByTestId('var-trend-chart').querySelector('svg')!

      fireEvent.mouseDown(svg, { clientX: 100, clientY: 50 })
      fireEvent.mouseMove(svg, { clientX: 300, clientY: 50 })
      fireEvent.mouseUp(svg)

      expect(onZoom).toHaveBeenCalledTimes(1)
      const zoomRange = onZoom.mock.calls[0][0] as TimeRange
      expect(zoomRange.label).toBe('Custom')
      expect(zoomRange.from).toBeDefined()
      expect(zoomRange.to).toBeDefined()
    })

    it('renders brush overlay rect during drag', () => {
      render(
        <VaRTrendChart
          history={history}
          onZoom={vi.fn()}
          zoomDepth={0}
          onResetZoom={vi.fn()}
        />,
      )

      const svg = screen.getByTestId('var-trend-chart').querySelector('svg')!

      fireEvent.mouseDown(svg, { clientX: 100, clientY: 50 })
      fireEvent.mouseMove(svg, { clientX: 300, clientY: 50 })

      const overlay = svg.querySelector('rect[fill="rgba(99, 102, 241, 0.2)"]')
      expect(overlay).toBeInTheDocument()
    })
  })
})
