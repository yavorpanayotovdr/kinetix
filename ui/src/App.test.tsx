import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PositionDto, VaRResultDto } from './types'

vi.mock('./hooks/usePositions')
vi.mock('./hooks/usePriceStream')
vi.mock('./hooks/useVaR')
vi.mock('./hooks/useStressTest')
vi.mock('./hooks/useGreeks')
vi.mock('./hooks/useNotifications')
vi.mock('./hooks/useRegulatory')
vi.mock('./hooks/useSystemHealth')
vi.mock('./hooks/useJobHistory')

import App from './App'
import { usePositions } from './hooks/usePositions'
import { usePriceStream } from './hooks/usePriceStream'
import { useVaR } from './hooks/useVaR'
import { useStressTest } from './hooks/useStressTest'
import { useGreeks } from './hooks/useGreeks'
import { useNotifications } from './hooks/useNotifications'
import { useRegulatory } from './hooks/useRegulatory'
import { useSystemHealth } from './hooks/useSystemHealth'
import { useJobHistory } from './hooks/useJobHistory'

const mockUsePositions = vi.mocked(usePositions)
const mockUsePriceStream = vi.mocked(usePriceStream)
const mockUseVaR = vi.mocked(useVaR)
const mockUseStressTest = vi.mocked(useStressTest)
const mockUseGreeks = vi.mocked(useGreeks)
const mockUseNotifications = vi.mocked(useNotifications)
const mockUseRegulatory = vi.mocked(useRegulatory)
const mockUseSystemHealth = vi.mocked(useSystemHealth)
const mockUseJobHistory = vi.mocked(useJobHistory)

const position: PositionDto = {
  portfolioId: 'port-1',
  instrumentId: 'AAPL',
  assetClass: 'EQUITY',
  quantity: '100',
  averageCost: { amount: '150.00', currency: 'USD' },
  marketPrice: { amount: '155.00', currency: 'USD' },
  marketValue: { amount: '15500.00', currency: 'USD' },
  unrealizedPnl: { amount: '500.00', currency: 'USD' },
}

const varResult: VaRResultDto = {
  portfolioId: 'port-1',
  calculationType: 'HISTORICAL',
  confidenceLevel: 'CL_95',
  varValue: '1234567.89',
  expectedShortfall: '1567890.12',
  componentBreakdown: [
    { assetClass: 'EQUITY', varContribution: '800000.00', percentageOfTotal: '64.85' },
  ],
  calculatedAt: '2025-01-15T10:30:00Z',
}

const selectPortfolio = vi.fn()

function setupDefaults() {
  mockUsePositions.mockReturnValue({
    positions: [position],
    portfolioId: 'port-1',
    portfolios: ['port-1', 'port-2', 'port-3'],
    selectPortfolio,
    loading: false,
    error: null,
  })
  mockUsePriceStream.mockReturnValue({ positions: [position], connected: true })
  mockUseVaR.mockReturnValue({
    varResult: null,
    history: [],
    loading: false,
    error: null,
    refresh: vi.fn(),
  })
  mockUseStressTest.mockReturnValue({
    scenarios: ['MARKET_CRASH', 'RATE_SHOCK'],
    selectedScenario: 'MARKET_CRASH',
    setSelectedScenario: vi.fn(),
    result: null,
    loading: false,
    error: null,
    run: vi.fn(),
  })
  mockUseGreeks.mockReturnValue({
    greeksResult: null,
    loading: false,
    error: null,
    volBump: 0,
    setVolBump: vi.fn(),
  })
  mockUseNotifications.mockReturnValue({
    rules: [],
    alerts: [],
    loading: false,
    error: null,
    createRule: vi.fn(),
    deleteRule: vi.fn(),
  })
  mockUseRegulatory.mockReturnValue({
    result: null,
    loading: false,
    error: null,
    calculate: vi.fn(),
    downloadCsv: vi.fn(),
    downloadXbrl: vi.fn(),
  })
  mockUseSystemHealth.mockReturnValue({
    health: {
      status: 'UP',
      services: {
        gateway: { status: 'UP' },
        'position-service': { status: 'UP' },
        'price-service': { status: 'UP' },
        'risk-orchestrator': { status: 'UP' },
        'notification-service': { status: 'UP' },
      },
    },
    loading: false,
    error: null,
    refresh: vi.fn(),
  })
  mockUseJobHistory.mockReturnValue({
    runs: [],
    expandedJobs: {},
    loadingJobIds: new Set(),
    loading: false,
    error: null,
    toggleJob: vi.fn(),
    closeJob: vi.fn(),
    clearSelection: vi.fn(),
    refresh: vi.fn(),
  })
}

describe('App', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    setupDefaults()
  })

  it('renders Kinetix heading and portfolio selector', () => {
    render(<App />)

    expect(screen.getByText('Kinetix')).toBeInTheDocument()
    expect(screen.getByTestId('portfolio-selector')).toBeInTheDocument()
  })

  it('shows loading message while fetching', () => {
    mockUsePositions.mockReturnValue({
      positions: [],
      portfolioId: null,
      portfolios: [],
      selectPortfolio,
      loading: true,
      error: null,
    })

    render(<App />)

    expect(screen.getByText('Loading positions...')).toBeInTheDocument()
  })

  it('shows error message on failure', () => {
    mockUsePositions.mockReturnValue({
      positions: [],
      portfolioId: null,
      portfolios: [],
      selectPortfolio,
      loading: false,
      error: 'Network error',
    })

    render(<App />)

    expect(screen.getByText('Network error')).toBeInTheDocument()
  })

  it('default tab shows positions', () => {
    render(<App />)

    expect(screen.getByTestId('position-row-AAPL')).toBeInTheDocument()
    expect(screen.queryByTestId('var-dashboard')).not.toBeInTheDocument()
  })

  it('clicking Risk tab shows VaR, stress test, and greeks', () => {
    mockUseVaR.mockReturnValue({
      varResult,
      history: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    render(<App />)

    fireEvent.click(screen.getByTestId('tab-risk'))

    expect(screen.getByTestId('var-dashboard')).toBeInTheDocument()
    expect(screen.getByTestId('stress-test-panel')).toBeInTheDocument()
  })

  it('clicking Regulatory tab shows regulatory dashboard', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-regulatory'))

    expect(screen.getByTestId('regulatory-dashboard')).toBeInTheDocument()
  })

  it('clicking Alerts tab shows notification center', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-alerts'))

    expect(screen.getByTestId('notification-center')).toBeInTheDocument()
  })

  it('portfolio selector calls selectPortfolio', () => {
    render(<App />)

    fireEvent.change(screen.getByTestId('portfolio-selector'), {
      target: { value: 'port-2' },
    })

    expect(selectPortfolio).toHaveBeenCalledWith('port-2')
  })

  it('shows alert count badge when alerts exist', () => {
    mockUseNotifications.mockReturnValue({
      rules: [],
      alerts: [
        {
          id: 'evt-1',
          ruleId: 'rule-1',
          ruleName: 'VaR Limit',
          type: 'VAR_BREACH',
          severity: 'CRITICAL',
          message: 'VaR exceeded threshold',
          currentValue: 150000,
          threshold: 100000,
          portfolioId: 'port-1',
          triggeredAt: '2025-01-15T10:00:00Z',
        },
      ],
      loading: false,
      error: null,
      createRule: vi.fn(),
      deleteRule: vi.fn(),
    })

    render(<App />)

    const badge = screen.getByTestId('alert-count-badge')
    expect(badge).toBeInTheDocument()
    expect(badge).toHaveTextContent('1')
  })

  it('does not show alert badge when no alerts', () => {
    render(<App />)

    expect(screen.queryByTestId('alert-count-badge')).not.toBeInTheDocument()
  })

  it('clicking System tab shows system dashboard', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-system'))

    expect(screen.getByTestId('system-dashboard')).toBeInTheDocument()
  })

  it('shows degraded dot on System tab when a service is DOWN', () => {
    mockUseSystemHealth.mockReturnValue({
      health: {
        status: 'DEGRADED',
        services: {
          gateway: { status: 'UP' },
          'position-service': { status: 'DOWN' },
          'price-service': { status: 'UP' },
          'risk-orchestrator': { status: 'UP' },
          'notification-service': { status: 'UP' },
        },
      },
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    render(<App />)

    expect(screen.getByTestId('system-degraded-dot')).toBeInTheDocument()
  })

  it('does not show degraded dot when all systems are UP', () => {
    render(<App />)

    expect(screen.queryByTestId('system-degraded-dot')).not.toBeInTheDocument()
  })

  it('System tab renders even when positions are loading', () => {
    mockUsePositions.mockReturnValue({
      positions: [],
      portfolioId: null,
      portfolios: [],
      selectPortfolio,
      loading: true,
      error: null,
    })

    render(<App />)

    fireEvent.click(screen.getByTestId('tab-system'))

    expect(screen.getByTestId('system-dashboard')).toBeInTheDocument()
    expect(screen.queryByText('Loading positions...')).not.toBeInTheDocument()
  })

  it('shows JobHistory in the risk tab', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-risk'))

    expect(screen.getByTestId('job-history')).toBeInTheDocument()
  })

  it('System tab renders even when positions have an error', () => {
    mockUsePositions.mockReturnValue({
      positions: [],
      portfolioId: null,
      portfolios: [],
      selectPortfolio,
      loading: false,
      error: 'Network error',
    })

    render(<App />)

    fireEvent.click(screen.getByTestId('tab-system'))

    expect(screen.getByTestId('system-dashboard')).toBeInTheDocument()
    expect(screen.queryByText('Network error')).not.toBeInTheDocument()
  })
})
