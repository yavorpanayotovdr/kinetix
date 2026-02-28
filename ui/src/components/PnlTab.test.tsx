import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../hooks/usePnlAttribution')

import { PnlTab } from './PnlTab'
import { usePnlAttribution } from '../hooks/usePnlAttribution'

const mockUsePnlAttribution = vi.mocked(usePnlAttribution)

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

describe('PnlTab', () => {
  beforeEach(() => {
    vi.resetAllMocks()
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

  it('shows empty state when no data', () => {
    mockUsePnlAttribution.mockReturnValue({
      data: null,
      loading: false,
      error: null,
    })

    render(<PnlTab portfolioId="port-1" />)

    expect(screen.getByTestId('pnl-empty')).toBeInTheDocument()
  })
})
