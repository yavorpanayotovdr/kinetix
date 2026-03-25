import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { VolSurfacePanel } from './VolSurfacePanel'

vi.mock('../hooks/useVolSurface', () => ({
  useVolSurface: vi.fn(),
}))

import { useVolSurface } from '../hooks/useVolSurface'

const mockUseVolSurface = vi.mocked(useVolSurface)

const SURFACE_DATA = {
  instrumentId: 'AAPL',
  asOfDate: '2026-03-25T10:00:00Z',
  source: 'BLOOMBERG',
  points: [
    { strike: 90,  maturityDays: 30, impliedVol: 0.28 },
    { strike: 100, maturityDays: 30, impliedVol: 0.25 },
    { strike: 110, maturityDays: 30, impliedVol: 0.22 },
    { strike: 100, maturityDays: 90, impliedVol: 0.27 },
  ],
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('ResizeObserver', class {
    callback: ResizeObserverCallback
    constructor(cb: ResizeObserverCallback) { this.callback = cb }
    observe() {
      this.callback(
        [{ contentRect: { width: 800 } } as unknown as ResizeObserverEntry],
        this as unknown as ResizeObserver,
      )
    }
    unobserve() {}
    disconnect() {}
  })
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(800)
})

describe('VolSurfacePanel', () => {
  it('renders empty state when no instrument is selected', () => {
    mockUseVolSurface.mockReturnValue({
      surface: null, diff: null, maturities: [], loading: false, diffLoading: false, error: null,
    })

    render(<VolSurfacePanel instruments={[]} />)

    expect(screen.getByTestId('vol-surface-panel')).toHaveTextContent('Select an instrument to view the vol surface.')
  })

  it('renders instrument dropdown with provided instruments', () => {
    mockUseVolSurface.mockReturnValue({
      surface: null, diff: null, maturities: [], loading: false, diffLoading: false, error: null,
    })

    render(<VolSurfacePanel instruments={['AAPL', 'SPX']} />)

    const select = screen.getByTestId('instrument-selector')
    expect(select).toBeInTheDocument()
  })

  it('renders both charts when surface data is available', () => {
    mockUseVolSurface.mockReturnValue({
      surface: SURFACE_DATA,
      diff: null,
      maturities: [30, 90],
      loading: false,
      diffLoading: false,
      error: null,
    })

    render(<VolSurfacePanel instruments={['AAPL']} defaultInstrumentId="AAPL" />)

    expect(screen.getByTestId('vol-skew-chart')).toBeInTheDocument()
    expect(screen.getByTestId('vol-term-structure-chart')).toBeInTheDocument()
  })

  it('renders no-data message when instrument is selected but surface is null', () => {
    mockUseVolSurface.mockReturnValue({
      surface: null, diff: null, maturities: [], loading: false, diffLoading: false, error: null,
    })

    render(<VolSurfacePanel instruments={['AAPL']} defaultInstrumentId="AAPL" />)

    expect(screen.getByTestId('vol-surface-panel')).toHaveTextContent('No vol surface available for this instrument.')
  })

  it('renders loading state while surface is being fetched', () => {
    mockUseVolSurface.mockReturnValue({
      surface: null, diff: null, maturities: [], loading: true, diffLoading: false, error: null,
    })

    render(<VolSurfacePanel instruments={['AAPL']} defaultInstrumentId="AAPL" />)

    const skewChart = screen.getByTestId('vol-skew-chart')
    expect(skewChart.querySelector('[role="status"]')).toBeInTheDocument()
  })

  it('renders error message when surface fetch fails', () => {
    mockUseVolSurface.mockReturnValue({
      surface: null, diff: null, maturities: [], loading: false, diffLoading: false, error: 'Network error',
    })

    render(<VolSurfacePanel instruments={['AAPL']} defaultInstrumentId="AAPL" />)

    expect(screen.getByTestId('vol-surface-panel')).toHaveTextContent('Network error')
  })

  it('renders compare date picker', () => {
    mockUseVolSurface.mockReturnValue({
      surface: SURFACE_DATA,
      diff: null,
      maturities: [30, 90],
      loading: false,
      diffLoading: false,
      error: null,
    })

    render(<VolSurfacePanel instruments={['AAPL']} defaultInstrumentId="AAPL" />)

    expect(screen.getByTestId('compare-date-picker')).toBeInTheDocument()
  })

  it('updates instrument selection when dropdown changes', () => {
    mockUseVolSurface.mockReturnValue({
      surface: null, diff: null, maturities: [], loading: false, diffLoading: false, error: null,
    })

    render(<VolSurfacePanel instruments={['AAPL', 'SPX']} />)

    const select = screen.getByTestId('instrument-selector')
    fireEvent.change(select, { target: { value: 'AAPL' } })

    expect(mockUseVolSurface).toHaveBeenCalledWith('AAPL', undefined)
  })
})
