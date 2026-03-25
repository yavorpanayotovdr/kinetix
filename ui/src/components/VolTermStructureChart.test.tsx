import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { VolTermStructureChart } from './VolTermStructureChart'
import type { VolPointResponse, VolPointDiffResponse } from '../api/volSurface'

const CONTAINER_WIDTH = 800

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class {
    callback: ResizeObserverCallback
    constructor(cb: ResizeObserverCallback) { this.callback = cb }
    observe() {
      this.callback(
        [{ contentRect: { width: CONTAINER_WIDTH } } as unknown as ResizeObserverEntry],
        this as unknown as ResizeObserver,
      )
    }
    unobserve() {}
    disconnect() {}
  })
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(CONTAINER_WIDTH)
})

// ATM is strike 100; points at strikes 90, 100, 110 across maturities 30, 90, 180
const POINTS: VolPointResponse[] = [
  { strike: 90,  maturityDays: 30,  impliedVol: 0.28 },
  { strike: 100, maturityDays: 30,  impliedVol: 0.25 },
  { strike: 110, maturityDays: 30,  impliedVol: 0.22 },
  { strike: 90,  maturityDays: 90,  impliedVol: 0.30 },
  { strike: 100, maturityDays: 90,  impliedVol: 0.27 },
  { strike: 110, maturityDays: 90,  impliedVol: 0.24 },
  { strike: 90,  maturityDays: 180, impliedVol: 0.32 },
  { strike: 100, maturityDays: 180, impliedVol: 0.29 },
  { strike: 110, maturityDays: 180, impliedVol: 0.26 },
]

const DIFF_POINTS: VolPointDiffResponse[] = [
  { strike: 100, maturityDays: 30,  baseVol: 0.25, compareVol: 0.24, diff: 0.01 },
  { strike: 100, maturityDays: 90,  baseVol: 0.27, compareVol: 0.26, diff: 0.01 },
  { strike: 100, maturityDays: 180, baseVol: 0.29, compareVol: 0.28, diff: 0.01 },
]

describe('VolTermStructureChart', () => {
  it('renders empty state when no points are provided', () => {
    render(<VolTermStructureChart points={[]} />)

    expect(screen.getByTestId('vol-term-structure-chart')).toHaveTextContent('No vol surface data available.')
  })

  it('renders loading state when isLoading is true and points are empty', () => {
    render(<VolTermStructureChart points={[]} isLoading />)

    const chart = screen.getByTestId('vol-term-structure-chart')
    expect(chart.querySelector('[role="status"]')).toBeInTheDocument()
  })

  it('renders SVG chart when points are provided', () => {
    render(<VolTermStructureChart points={POINTS} />)

    const chart = screen.getByTestId('vol-term-structure-chart')
    expect(chart.querySelector('svg')).toBeInTheDocument()
  })

  it('renders ATM term structure as a polyline', () => {
    render(<VolTermStructureChart points={POINTS} />)

    const chart = screen.getByTestId('vol-term-structure-chart')
    const polylines = chart.querySelectorAll('polyline')
    expect(polylines.length).toBeGreaterThanOrEqual(1)
  })

  it('renders dashed diff overlay when diffPoints are provided', () => {
    render(<VolTermStructureChart points={POINTS} diffPoints={DIFF_POINTS} />)

    const chart = screen.getByTestId('vol-term-structure-chart')
    const dashedLines = [...chart.querySelectorAll('polyline')].filter(
      (el) => el.getAttribute('stroke-dasharray') !== null,
    )
    expect(dashedLines.length).toBeGreaterThanOrEqual(1)
  })

  it('renders chart title', () => {
    render(<VolTermStructureChart points={POINTS} />)

    expect(screen.getByText('ATM Term Structure')).toBeInTheDocument()
  })

  it('renders x-axis labels for maturity ticks', () => {
    render(<VolTermStructureChart points={POINTS} />)

    const chart = screen.getByTestId('vol-term-structure-chart')
    const textNodes = chart.querySelectorAll('text')
    expect(textNodes.length).toBeGreaterThan(0)
  })
})
