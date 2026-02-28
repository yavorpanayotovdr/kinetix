import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../hooks/usePnlAttribution')
vi.mock('../hooks/useSodBaseline')

import { PnlTab } from './PnlTab'
import { usePnlAttribution } from '../hooks/usePnlAttribution'
import { useSodBaseline } from '../hooks/useSodBaseline'

const mockUsePnlAttribution = vi.mocked(usePnlAttribution)
const mockUseSodBaseline = vi.mocked(useSodBaseline)

const pnlAttributionData = {
  portfolioId: 'port-1',
  date: '2025-01-15',
  totalPnl: '15000.00',
  deltaPnl: '8000.00',
  gammaPnl: '2500.00',
  vegaPnl: '3000.00',
  thetaPnl: '-1500.00',
  rhoPnl: '500.00',
  unexplainedPnl: '2500.00',
  positionAttributions: [
    {
      instrumentId: 'AAPL',
      assetClass: 'EQUITY',
      totalPnl: '8000.00',
      deltaPnl: '5000.00',
      gammaPnl: '1200.00',
      vegaPnl: '1500.00',
      thetaPnl: '-800.00',
      rhoPnl: '300.00',
      unexplainedPnl: '800.00',
    },
  ],
  calculatedAt: '2025-01-15T10:30:00Z',
}

const defaultSodBaseline = {
  status: null,
  loading: false,
  error: null,
  creating: false,
  resetting: false,
  computing: false,
  createSnapshot: vi.fn(),
  resetBaseline: vi.fn(),
  computeAttribution: vi.fn().mockResolvedValue(null),
  refresh: vi.fn(),
}

describe('PnlTab', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockUseSodBaseline.mockReturnValue(defaultSodBaseline)
  })

  it('calls usePnlAttribution with the given portfolioId', () => {
    mockUsePnlAttribution.mockReturnValue({
      data: null,
      loading: false,
      error: null,
    })

    render(<PnlTab portfolioId="port-1" />)

    expect(mockUsePnlAttribution).toHaveBeenCalledWith('port-1')
  })

  it('renders waterfall chart and attribution table when data is available', () => {
    mockUsePnlAttribution.mockReturnValue({
      data: pnlAttributionData,
      loading: false,
      error: null,
    })
    mockUseSodBaseline.mockReturnValue({
      ...defaultSodBaseline,
      status: {
        exists: true,
        baselineDate: '2025-01-15',
        snapshotType: 'MANUAL',
        createdAt: '2025-01-15T08:00:00Z',
      },
    })

    render(<PnlTab portfolioId="port-1" />)

    expect(screen.getByTestId('waterfall-chart')).toBeInTheDocument()
    expect(screen.getByTestId('attribution-table')).toBeInTheDocument()
  })

  it('shows loading state', () => {
    mockUsePnlAttribution.mockReturnValue({
      data: null,
      loading: true,
      error: null,
    })
    mockUseSodBaseline.mockReturnValue({
      ...defaultSodBaseline,
      loading: true,
    })

    render(<PnlTab portfolioId="port-1" />)

    expect(screen.getByTestId('pnl-loading')).toBeInTheDocument()
  })

  it('shows error state', () => {
    mockUsePnlAttribution.mockReturnValue({
      data: null,
      loading: false,
      error: 'Failed to load',
    })

    render(<PnlTab portfolioId="port-1" />)

    expect(screen.getByTestId('pnl-error')).toBeInTheDocument()
    expect(screen.getByText('Failed to load')).toBeInTheDocument()
  })

  it('shows SOD baseline warning when no baseline exists', () => {
    mockUsePnlAttribution.mockReturnValue({
      data: null,
      loading: false,
      error: null,
    })
    mockUseSodBaseline.mockReturnValue({
      ...defaultSodBaseline,
      status: {
        exists: false,
        baselineDate: null,
        snapshotType: null,
        createdAt: null,
      },
    })

    render(<PnlTab portfolioId="port-1" />)

    expect(screen.getByTestId('sod-baseline-warning')).toBeInTheDocument()
    expect(screen.getByTestId('pnl-empty')).toBeInTheDocument()
  })

  it('shows compute prompt when baseline exists but no P&L data', () => {
    mockUsePnlAttribution.mockReturnValue({
      data: null,
      loading: false,
      error: null,
    })
    mockUseSodBaseline.mockReturnValue({
      ...defaultSodBaseline,
      status: {
        exists: true,
        baselineDate: '2025-01-15',
        snapshotType: 'MANUAL',
        createdAt: '2025-01-15T08:00:00Z',
      },
    })

    render(<PnlTab portfolioId="port-1" />)

    expect(screen.getByTestId('sod-baseline-active')).toBeInTheDocument()
    expect(screen.getByTestId('pnl-compute-button')).toBeInTheDocument()
  })

  it('shows confirmation dialog when reset button is clicked', () => {
    mockUsePnlAttribution.mockReturnValue({
      data: pnlAttributionData,
      loading: false,
      error: null,
    })
    mockUseSodBaseline.mockReturnValue({
      ...defaultSodBaseline,
      status: {
        exists: true,
        baselineDate: '2025-01-15',
        snapshotType: 'MANUAL',
        createdAt: '2025-01-15T08:00:00Z',
      },
    })

    render(<PnlTab portfolioId="port-1" />)

    fireEvent.click(screen.getByTestId('sod-reset-button'))

    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument()
    expect(screen.getByTestId('confirm-dialog-confirm')).toBeInTheDocument()
  })

  it('shows SOD error when sod hook returns an error', () => {
    mockUsePnlAttribution.mockReturnValue({
      data: null,
      loading: false,
      error: null,
    })
    mockUseSodBaseline.mockReturnValue({
      ...defaultSodBaseline,
      status: {
        exists: false,
        baselineDate: null,
        snapshotType: null,
        createdAt: null,
      },
      error: 'SOD service unavailable',
    })

    render(<PnlTab portfolioId="port-1" />)

    expect(screen.getByTestId('sod-error')).toBeInTheDocument()
    expect(screen.getByText('SOD service unavailable')).toBeInTheDocument()
  })
})
