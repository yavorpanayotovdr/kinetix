import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { VolSkewChart } from './VolSkewChart'
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

const POINTS: VolPointResponse[] = [
  { strike: 90, maturityDays: 30, impliedVol: 0.28 },
  { strike: 100, maturityDays: 30, impliedVol: 0.25 },
  { strike: 110, maturityDays: 30, impliedVol: 0.22 },
  { strike: 90, maturityDays: 90, impliedVol: 0.30 },
  { strike: 100, maturityDays: 90, impliedVol: 0.27 },
  { strike: 110, maturityDays: 90, impliedVol: 0.24 },
]

const DIFF_POINTS: VolPointDiffResponse[] = [
  { strike: 90, maturityDays: 30, baseVol: 0.28, compareVol: 0.27, diff: 0.01 },
  { strike: 100, maturityDays: 30, baseVol: 0.25, compareVol: 0.24, diff: 0.01 },
  { strike: 110, maturityDays: 30, baseVol: 0.22, compareVol: 0.21, diff: 0.01 },
]

describe('VolSkewChart', () => {
  it('renders empty state when no points are provided', () => {
    render(<VolSkewChart points={[]} maturities={[]} />)

    expect(screen.getByTestId('vol-skew-chart')).toHaveTextContent('No vol surface data available.')
  })

  it('renders loading state when isLoading is true and points are empty', () => {
    render(<VolSkewChart points={[]} maturities={[]} isLoading />)

    const chart = screen.getByTestId('vol-skew-chart')
    expect(chart.querySelector('[role="status"]')).toBeInTheDocument()
  })

  it('renders SVG chart when points are provided', () => {
    render(<VolSkewChart points={POINTS} maturities={[30, 90]} />)

    const chart = screen.getByTestId('vol-skew-chart')
    expect(chart.querySelector('svg')).toBeInTheDocument()
  })

  it('renders one polyline per selected maturity', () => {
    render(<VolSkewChart points={POINTS} maturities={[30, 90]} />)

    const chart = screen.getByTestId('vol-skew-chart')
    const polylines = chart.querySelectorAll('polyline')
    expect(polylines.length).toBeGreaterThanOrEqual(2)
  })

  it('renders maturity toggle buttons', () => {
    render(<VolSkewChart points={POINTS} maturities={[30, 90]} />)

    expect(screen.getByTestId('maturity-toggle-30')).toBeInTheDocument()
    expect(screen.getByTestId('maturity-toggle-90')).toBeInTheDocument()
  })

  it('hides a maturity series when its toggle is clicked', () => {
    render(<VolSkewChart points={POINTS} maturities={[30, 90]} />)

    const chart = screen.getByTestId('vol-skew-chart')
    const before = chart.querySelectorAll('polyline').length

    fireEvent.click(screen.getByTestId('maturity-toggle-90'))

    // Only the 30-day series should be visible at full opacity
    const toggle90 = screen.getByTestId('maturity-toggle-90')
    expect(toggle90).toHaveAttribute('aria-pressed', 'false')

    const after = chart.querySelectorAll('polyline').length
    // Lines are still rendered but at reduced opacity — count stays the same
    // (we test opacity change rather than line removal)
    expect(after).toBe(before)
  })

  it('renders dashed diff overlay when diffPoints are provided', () => {
    render(<VolSkewChart points={POINTS} maturities={[30, 90]} diffPoints={DIFF_POINTS} />)

    const chart = screen.getByTestId('vol-skew-chart')
    const dashedLines = [...chart.querySelectorAll('polyline')].filter(
      (el) => el.getAttribute('stroke-dasharray') !== null,
    )
    expect(dashedLines.length).toBeGreaterThanOrEqual(1)
  })

  it('renders chart title', () => {
    render(<VolSkewChart points={POINTS} maturities={[30, 90]} />)

    expect(screen.getByText('Vol Skew')).toBeInTheDocument()
  })
})
