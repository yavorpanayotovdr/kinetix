import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { VaRHistoryEntry } from '../hooks/useVaR'
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
  it('renders empty state when fewer than 2 data points', () => {
    render(<VaRTrendChart history={[history[0]]} />)

    const panel = screen.getByTestId('var-trend-chart')
    expect(panel).toBeInTheDocument()
    expect(panel).toHaveTextContent('Collecting data...')
  })

  it('renders empty state for zero data points', () => {
    render(<VaRTrendChart history={[]} />)

    expect(screen.getByTestId('var-trend-chart')).toHaveTextContent('Collecting data...')
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

    const polyline = svg!.querySelector('polyline')
    expect(polyline).toBeInTheDocument()
    expect(polyline).toHaveAttribute('stroke', '#6366f1')
  })

  it('renders an area fill polygon', () => {
    render(<VaRTrendChart history={history} />)

    const svg = screen.getByTestId('var-trend-chart').querySelector('svg')
    const polygon = svg!.querySelector('polygon')
    expect(polygon).toBeInTheDocument()
    expect(polygon).toHaveAttribute('fill', 'rgba(99, 102, 241, 0.15)')
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
})
