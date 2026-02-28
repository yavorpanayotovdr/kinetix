import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StressTestResultDto } from '../types'

vi.mock('../hooks/useVaR')
vi.mock('../hooks/useJobHistory')
vi.mock('../hooks/usePositionRisk')
vi.mock('../hooks/useVarLimit')
vi.mock('../hooks/useAlerts')

import { RiskTab } from './RiskTab'
import { useVaR } from '../hooks/useVaR'
import { useJobHistory } from '../hooks/useJobHistory'
import { usePositionRisk } from '../hooks/usePositionRisk'
import { useVarLimit } from '../hooks/useVarLimit'
import { useAlerts } from '../hooks/useAlerts'

const mockUseVaR = vi.mocked(useVaR)
const mockUseJobHistory = vi.mocked(useJobHistory)
const mockUsePositionRisk = vi.mocked(usePositionRisk)
const mockUseVarLimit = vi.mocked(useVarLimit)
const mockUseAlerts = vi.mocked(useAlerts)

const stressResult: StressTestResultDto = {
  scenarioName: 'MARKET_CRASH',
  baseVar: '1000000',
  stressedVar: '2500000',
  pnlImpact: '-1500000',
  assetClassImpacts: [],
  calculatedAt: '2025-01-15T10:30:00Z',
}

const defaultStressProps = {
  stressResult: null as StressTestResultDto | null,
  stressLoading: false,
  onRunStress: vi.fn(),
  onViewStressDetails: vi.fn(),
}

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
    mockUseVarLimit.mockReturnValue({
      varLimit: null,
      loading: false,
    })
    mockUseAlerts.mockReturnValue({
      alerts: [],
      dismissAlert: vi.fn(),
    })
  })

  it('calls useVaR with the given portfolioId', () => {
    render(<RiskTab portfolioId="port-1" {...defaultStressProps} />)

    expect(mockUseVaR).toHaveBeenCalledWith('port-1')
  })

  it('renders VaR dashboard and job history', () => {
    render(<RiskTab portfolioId="port-1" {...defaultStressProps} />)

    expect(screen.getByTestId('var-empty')).toBeInTheDocument()
    expect(screen.getByTestId('job-history')).toBeInTheDocument()
  })

  it('calls usePositionRisk with the given portfolioId', () => {
    render(<RiskTab portfolioId="port-1" {...defaultStressProps} />)

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

    render(<RiskTab portfolioId="port-1" {...defaultStressProps} />)

    expect(screen.getByTestId('position-risk-section')).toBeInTheDocument()
  })

  it('passes varLimit from useVarLimit to VaRDashboard', () => {
    mockUseVarLimit.mockReturnValue({
      varLimit: 2000000,
      loading: false,
    })
    mockUseVaR.mockReturnValue({
      varResult: {
        portfolioId: 'port-1',
        calculationType: 'HISTORICAL',
        confidenceLevel: 'CL_95',
        varValue: '1000000',
        expectedShortfall: '1500000',
        componentBreakdown: [],
        calculatedAt: '2025-01-15T10:00:00Z',
      },
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

    render(<RiskTab portfolioId="port-1" {...defaultStressProps} />)

    const limitLabel = screen.getByTestId('var-limit')
    expect(limitLabel).toHaveTextContent('Limit: $2,000,000.00')
    // VaR is 1,000,000 / 2,000,000 = 50%
    expect(limitLabel).toHaveTextContent('50%')
  })

  it('renders risk alert banner when alerts are present', () => {
    mockUseAlerts.mockReturnValue({
      alerts: [
        {
          id: 'alert-1',
          ruleId: 'rule-1',
          ruleName: 'VaR Limit',
          type: 'VAR_BREACH',
          severity: 'CRITICAL',
          message: 'VaR exceeds limit',
          currentValue: 2300000,
          threshold: 2000000,
          portfolioId: 'port-1',
          triggeredAt: '2026-02-28T10:00:00Z',
        },
      ],
      dismissAlert: vi.fn(),
    })

    render(<RiskTab portfolioId="port-1" {...defaultStressProps} />)

    expect(screen.getByTestId('risk-alert-banner')).toBeInTheDocument()
    expect(screen.getByText('VaR exceeds limit')).toBeInTheDocument()
  })

  it('does not render risk alert banner when no alerts', () => {
    mockUseAlerts.mockReturnValue({
      alerts: [],
      dismissAlert: vi.fn(),
    })

    render(<RiskTab portfolioId="port-1" {...defaultStressProps} />)

    expect(screen.queryByTestId('risk-alert-banner')).not.toBeInTheDocument()
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

    render(<RiskTab portfolioId="port-1" {...defaultStressProps} />)

    const refreshButton = screen.getByTestId('var-recalculate')
    await user.click(refreshButton)

    expect(mockRefreshVaR).toHaveBeenCalled()
    expect(mockRefreshPositionRisk).toHaveBeenCalled()
  })

  it('renders StressSummaryCard', () => {
    render(<RiskTab portfolioId="port-1" {...defaultStressProps} />)

    expect(screen.getByTestId('stress-summary-card')).toBeInTheDocument()
  })

  it('renders StressSummaryCard between PositionRiskTable and JobHistory', () => {
    render(<RiskTab portfolioId="port-1" {...defaultStressProps} stressResult={stressResult} />)

    const card = screen.getByTestId('stress-summary-card')
    const jobHistory = screen.getByTestId('job-history')
    expect(card.compareDocumentPosition(jobHistory) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('passes stress result to StressSummaryCard', () => {
    render(
      <RiskTab
        portfolioId="port-1"
        {...defaultStressProps}
        stressResult={stressResult}
      />,
    )

    expect(screen.getByTestId('stress-summary-table')).toBeInTheDocument()
    expect(screen.getByText('MARKET CRASH')).toBeInTheDocument()
  })

  it('calls onRunStress when Run Stress Tests button is clicked', async () => {
    const user = userEvent.setup()
    const onRunStress = vi.fn()

    render(
      <RiskTab
        portfolioId="port-1"
        {...defaultStressProps}
        onRunStress={onRunStress}
      />,
    )

    await user.click(screen.getByTestId('stress-summary-run-btn'))
    expect(onRunStress).toHaveBeenCalledOnce()
  })

  it('calls onViewStressDetails when View Details is clicked', async () => {
    const user = userEvent.setup()
    const onViewStressDetails = vi.fn()

    render(
      <RiskTab
        portfolioId="port-1"
        {...defaultStressProps}
        stressResult={stressResult}
        onViewStressDetails={onViewStressDetails}
      />,
    )

    await user.click(screen.getByTestId('stress-summary-view-details'))
    expect(onViewStressDetails).toHaveBeenCalledOnce()
  })
})
