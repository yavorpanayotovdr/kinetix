import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../hooks/useVaR')
vi.mock('../hooks/useJobHistory')
vi.mock('../hooks/usePositionRisk')

import { RiskTab } from './RiskTab'
import { useVaR } from '../hooks/useVaR'
import { useJobHistory } from '../hooks/useJobHistory'
import { usePositionRisk } from '../hooks/usePositionRisk'

const mockUseVaR = vi.mocked(useVaR)
const mockUseJobHistory = vi.mocked(useJobHistory)
const mockUsePositionRisk = vi.mocked(usePositionRisk)

describe('RiskTab', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockUseVaR.mockReturnValue({
      varResult: null,
      greeksResult: null,
      history: [],
      filteredHistory: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
      refreshing: false,
      timeRange: { from: '2025-01-14T10:30:00Z', to: '2025-01-15T10:30:00Z', label: 'Last 24h' },
      setTimeRange: vi.fn(),
      zoomIn: vi.fn(),
      resetZoom: vi.fn(),
      zoomDepth: 0,
    })
    mockUseJobHistory.mockReturnValue({
      runs: [],
      expandedJobs: {},
      loadingJobIds: new Set(),
      loading: false,
      error: null,
      timeRange: { from: '2025-01-14T10:00:00Z', to: '2025-01-15T10:00:00Z', label: 'Last 24h' },
      setTimeRange: vi.fn(),
      toggleJob: vi.fn(),
      closeJob: vi.fn(),
      clearSelection: vi.fn(),
      refresh: vi.fn(),
      zoomIn: vi.fn(),
      resetZoom: vi.fn(),
      zoomDepth: 0,
      page: 1,
      totalPages: 1,
      hasNextPage: false,
      nextPage: vi.fn(),
      prevPage: vi.fn(),
      firstPage: vi.fn(),
      lastPage: vi.fn(),
      goToPage: vi.fn(),
      pageSize: 10,
      setPageSize: vi.fn(),
      totalCount: 0,
    })
    mockUsePositionRisk.mockReturnValue({
      positionRisk: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })
  })

  it('calls useVaR with the given portfolioId', () => {
    render(<RiskTab portfolioId="port-1" />)

    expect(mockUseVaR).toHaveBeenCalledWith('port-1')
  })

  it('renders VaR dashboard and job history', () => {
    render(<RiskTab portfolioId="port-1" />)

    expect(screen.getByTestId('var-empty')).toBeInTheDocument()
    expect(screen.getByTestId('job-history')).toBeInTheDocument()
  })

  it('calls usePositionRisk with the given portfolioId', () => {
    render(<RiskTab portfolioId="port-1" />)

    expect(mockUsePositionRisk).toHaveBeenCalledWith('port-1')
  })

  it('renders PositionRiskTable between VaR dashboard and job history', () => {
    mockUsePositionRisk.mockReturnValue({
      positionRisk: [
        {
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          marketValue: '15500.00',
          delta: '1234.56',
          gamma: '45.67',
          vega: '89.01',
          varContribution: '800.00',
          esContribution: '1000.00',
          percentageOfTotal: '64.85',
        },
      ],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    render(<RiskTab portfolioId="port-1" />)

    expect(screen.getByTestId('position-risk-section')).toBeInTheDocument()
  })

  it('refreshes position risk data when VaR dashboard is refreshed', async () => {
    const user = userEvent.setup()
    const mockRefreshVaR = vi.fn().mockResolvedValue(undefined)
    const mockRefreshPositionRisk = vi.fn().mockResolvedValue(undefined)

    mockUseVaR.mockReturnValue({
      varResult: {
        portfolioId: 'port-1',
        calculationType: 'HISTORICAL',
        confidenceLevel: '0.95',
        varValue: '5000',
        expectedShortfall: '7000',
        componentBreakdown: [],
        calculatedAt: '2025-01-15T10:00:00Z',
      },
      greeksResult: null,
      history: [],
      filteredHistory: [],
      loading: false,
      error: null,
      refresh: mockRefreshVaR,
      refreshing: false,
      timeRange: { from: '2025-01-14T10:30:00Z', to: '2025-01-15T10:30:00Z', label: 'Last 24h' },
      setTimeRange: vi.fn(),
      zoomIn: vi.fn(),
      resetZoom: vi.fn(),
      zoomDepth: 0,
    })
    mockUsePositionRisk.mockReturnValue({
      positionRisk: [],
      loading: false,
      error: null,
      refresh: mockRefreshPositionRisk,
    })

    render(<RiskTab portfolioId="port-1" />)

    const refreshButton = screen.getByTestId('var-recalculate')
    await user.click(refreshButton)

    expect(mockRefreshVaR).toHaveBeenCalled()
    expect(mockRefreshPositionRisk).toHaveBeenCalled()
  })
})
