import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PositionDto } from './types'

vi.mock('./hooks/usePositions')
vi.mock('./hooks/usePriceStream')
vi.mock('./hooks/useNotifications')
vi.mock('./hooks/useSystemHealth')
vi.mock('./hooks/useStressTest')
vi.mock('./hooks/usePortfolioSelector')
vi.mock('./hooks/useDataQuality')
vi.mock('./hooks/useWorkspace')
vi.mock('./components/TradeBlotter', () => ({
  TradeBlotter: () => <div data-testid="trade-blotter-wrapper" />,
}))
vi.mock('./components/RiskTab', () => ({
  RiskTab: () => <div data-testid="risk-tab-wrapper" />,
}))
vi.mock('./components/ScenariosTab', () => ({
  ScenariosTab: () => <div data-testid="scenarios-tab-wrapper" />,
}))
vi.mock('./components/RegulatoryTab', () => ({
  RegulatoryTab: () => <div data-testid="regulatory-tab-wrapper" />,
}))

import App from './App'
import { usePositions } from './hooks/usePositions'
import { usePriceStream } from './hooks/usePriceStream'
import { useNotifications } from './hooks/useNotifications'
import { useSystemHealth } from './hooks/useSystemHealth'
import { useStressTest } from './hooks/useStressTest'
import { usePortfolioSelector } from './hooks/usePortfolioSelector'
import { useDataQuality } from './hooks/useDataQuality'
import { useWorkspace, DEFAULT_PREFERENCES } from './hooks/useWorkspace'

const mockUsePositions = vi.mocked(usePositions)
const mockUsePriceStream = vi.mocked(usePriceStream)
const mockUseNotifications = vi.mocked(useNotifications)
const mockUseSystemHealth = vi.mocked(useSystemHealth)
const mockUseStressTest = vi.mocked(useStressTest)
const mockUsePortfolioSelector = vi.mocked(usePortfolioSelector)
const mockUseDataQuality = vi.mocked(useDataQuality)
const mockUseWorkspace = vi.mocked(useWorkspace)

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
  mockUsePriceStream.mockReturnValue({ positions: [position], connected: true, reconnecting: false })
  mockUseNotifications.mockReturnValue({
    rules: [],
    alerts: [],
    loading: false,
    error: null,
    createRule: vi.fn(),
    deleteRule: vi.fn(),
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
  mockUseStressTest.mockReturnValue({
    scenarios: ['MARKET_CRASH', 'RATE_SHOCK'],
    selectedScenario: 'MARKET_CRASH',
    setSelectedScenario: vi.fn(),
    result: null,
    results: [],
    loading: false,
    error: null,
    run: vi.fn(),
  })
  mockUsePortfolioSelector.mockReturnValue({
    portfolioOptions: [
      { value: '__ALL__', label: 'All Portfolios' },
      { value: 'port-1', label: 'port-1' },
      { value: 'port-2', label: 'port-2' },
    ],
    selectedPortfolioId: 'port-1',
    isAllSelected: false,
    allPortfolioIds: ['port-1', 'port-2'],
    positions: [position],
    aggregatedPositions: [],
    selectPortfolio: vi.fn(),
    loading: false,
    error: null,
  })
  mockUseDataQuality.mockReturnValue({
    status: { overall: 'OK', checks: [] },
    loading: false,
    error: null,
  })
  mockUseWorkspace.mockReturnValue({
    preferences: DEFAULT_PREFERENCES,
    updatePreference: vi.fn(),
    resetPreferences: vi.fn(),
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
    expect(screen.queryByTestId('risk-tab-wrapper')).not.toBeInTheDocument()
  })

  it('clicking Trades tab renders the TradeBlotter wrapper', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-trades'))

    expect(screen.getByTestId('trade-blotter-wrapper')).toBeInTheDocument()
    expect(screen.queryByTestId('position-row-AAPL')).not.toBeInTheDocument()
  })

  it('clicking Risk tab renders the RiskTab wrapper', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-risk'))

    expect(screen.getByTestId('risk-tab-wrapper')).toBeInTheDocument()
  })

  it('Risk tab does not show stress test panel', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-risk'))

    expect(screen.queryByTestId('scenarios-tab-wrapper')).not.toBeInTheDocument()
  })

  it('clicking Scenarios tab renders the ScenariosTab wrapper', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-scenarios'))

    expect(screen.getByTestId('scenarios-tab-wrapper')).toBeInTheDocument()
  })

  it('clicking Regulatory tab renders the RegulatoryTab wrapper', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-regulatory'))

    expect(screen.getByTestId('regulatory-tab-wrapper')).toBeInTheDocument()
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

  it('does not render RiskTab wrapper on initial positions tab', () => {
    render(<App />)

    expect(screen.queryByTestId('risk-tab-wrapper')).not.toBeInTheDocument()
    expect(screen.queryByTestId('scenarios-tab-wrapper')).not.toBeInTheDocument()
    expect(screen.queryByTestId('regulatory-tab-wrapper')).not.toBeInTheDocument()
  })

  it('unmounts RiskTab when switching away from Risk tab', () => {
    render(<App />)

    fireEvent.click(screen.getByTestId('tab-risk'))
    expect(screen.getByTestId('risk-tab-wrapper')).toBeInTheDocument()

    fireEvent.click(screen.getByTestId('tab-positions'))
    expect(screen.queryByTestId('risk-tab-wrapper')).not.toBeInTheDocument()
  })

  describe('WAI-ARIA accessibility', () => {
    it('tab bar has role tablist', () => {
      render(<App />)

      const tabBar = screen.getByTestId('tab-bar')
      expect(tabBar).toHaveAttribute('role', 'tablist')
    })

    it('each tab has role tab and aria-selected', () => {
      render(<App />)

      const positionsTab = screen.getByTestId('tab-positions')
      expect(positionsTab).toHaveAttribute('role', 'tab')
      expect(positionsTab).toHaveAttribute('aria-selected', 'true')

      const tradesTab = screen.getByTestId('tab-trades')
      expect(tradesTab).toHaveAttribute('role', 'tab')
      expect(tradesTab).toHaveAttribute('aria-selected', 'false')
    })

    it('active tab panel has role tabpanel', () => {
      render(<App />)

      const tabPanel = screen.getByRole('tabpanel')
      expect(tabPanel).toBeInTheDocument()
      expect(tabPanel).toHaveAttribute('aria-labelledby', 'tab-positions')
    })

    it('tab panel aria-labelledby updates when switching tabs', () => {
      render(<App />)

      fireEvent.click(screen.getByTestId('tab-trades'))

      const tabPanel = screen.getByRole('tabpanel')
      expect(tabPanel).toHaveAttribute('aria-labelledby', 'tab-trades')
    })

    it('keyboard arrow right navigates to next tab', () => {
      render(<App />)

      const positionsTab = screen.getByTestId('tab-positions')
      positionsTab.focus()
      fireEvent.keyDown(screen.getByTestId('tab-bar'), { key: 'ArrowRight' })

      expect(screen.getByTestId('tab-trades')).toHaveFocus()
    })

    it('keyboard arrow left navigates to previous tab', () => {
      render(<App />)

      const tradesTab = screen.getByTestId('tab-trades')
      tradesTab.focus()
      fireEvent.keyDown(screen.getByTestId('tab-bar'), { key: 'ArrowLeft' })

      expect(screen.getByTestId('tab-positions')).toHaveFocus()
    })

    it('keyboard Home moves focus to first tab', () => {
      render(<App />)

      const tradesTab = screen.getByTestId('tab-trades')
      tradesTab.focus()
      fireEvent.keyDown(screen.getByTestId('tab-bar'), { key: 'Home' })

      expect(screen.getByTestId('tab-positions')).toHaveFocus()
    })

    it('keyboard End moves focus to last tab', () => {
      render(<App />)

      const positionsTab = screen.getByTestId('tab-positions')
      positionsTab.focus()
      fireEvent.keyDown(screen.getByTestId('tab-bar'), { key: 'End' })

      expect(screen.getByTestId('tab-system')).toHaveFocus()
    })

    it('arrow right wraps around from last tab to first', () => {
      render(<App />)

      const systemTab = screen.getByTestId('tab-system')
      systemTab.focus()
      fireEvent.keyDown(screen.getByTestId('tab-bar'), { key: 'ArrowRight' })

      expect(screen.getByTestId('tab-positions')).toHaveFocus()
    })

    it('arrow left wraps around from first tab to last', () => {
      render(<App />)

      const positionsTab = screen.getByTestId('tab-positions')
      positionsTab.focus()
      fireEvent.keyDown(screen.getByTestId('tab-bar'), { key: 'ArrowLeft' })

      expect(screen.getByTestId('tab-system')).toHaveFocus()
    })

    it('inactive tabs have tabIndex -1 and active tab has tabIndex 0', () => {
      render(<App />)

      const positionsTab = screen.getByTestId('tab-positions')
      expect(positionsTab).toHaveAttribute('tabindex', '0')

      const tradesTab = screen.getByTestId('tab-trades')
      expect(tradesTab).toHaveAttribute('tabindex', '-1')
    })

    it('connection status has aria-live polite for real-time updates', () => {
      render(<App />)

      const connectionStatus = screen.getByTestId('connection-status')
      expect(connectionStatus).toHaveAttribute('aria-live', 'polite')
    })
  })

  describe('reconnecting indicator', () => {
    it('shows reconnecting banner when WebSocket is reconnecting', () => {
      mockUsePriceStream.mockReturnValue({
        positions: [position],
        connected: false,
        reconnecting: true,
      })

      render(<App />)

      expect(screen.getByTestId('reconnecting-banner')).toBeInTheDocument()
      expect(screen.getByText('Reconnecting...')).toBeInTheDocument()
    })

    it('does not show reconnecting banner when connected', () => {
      render(<App />)

      expect(screen.queryByTestId('reconnecting-banner')).not.toBeInTheDocument()
    })
  })
})
