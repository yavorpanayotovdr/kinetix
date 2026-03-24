import { useEffect, useRef, useState } from 'react'
import { Activity, BarChart3, ScrollText, TrendingUp, Shield, FlaskConical, Scale, Bell, Server, FlaskRound, Sun, Moon, Save, CalendarDays } from 'lucide-react'
import { PositionGrid } from './components/PositionGrid'
import { TradeBlotter } from './components/TradeBlotter'
import { NotificationCenter } from './components/NotificationCenter'
import { SystemDashboard } from './components/SystemDashboard'
import { RiskTab } from './components/RiskTab'
import { ScenariosTab } from './components/ScenariosTab'
import { RegulatoryTab } from './components/RegulatoryTab'
import { PnlTab } from './components/PnlTab'
import { WhatIfPanel } from './components/WhatIfPanel'
import { EodTimelineTab } from './components/EodTimelineTab'
import { BookSummaryCard } from './components/BookSummaryCard'
import { usePositions } from './hooks/usePositions'
import { useBookSelector, ALL_BOOKS } from './hooks/useBookSelector'
import { useHierarchySelector } from './hooks/useHierarchySelector'
import { HierarchySelector } from './components/HierarchySelector'
import { usePriceStream } from './hooks/usePriceStream'
import { useNotifications } from './hooks/useNotifications'
import { usePositionRisk } from './hooks/usePositionRisk'
import { useSystemHealth } from './hooks/useSystemHealth'
import { useWhatIf } from './hooks/useWhatIf'
import { useStressTest } from './hooks/useStressTest'
import { useRunAllScenarios } from './hooks/useRunAllScenarios'
import { useHierarchySummary } from './hooks/useHierarchySummary'
import { useTheme } from './hooks/useTheme'
import { useDataQuality } from './hooks/useDataQuality'
import { DataQualityIndicator } from './components/DataQualityIndicator'
import { useMarketRegime } from './hooks/useMarketRegime'
import { RegimeIndicator } from './components/RegimeIndicator'
import { useWorkspace } from './hooks/useWorkspace'

type Tab = 'positions' | 'trades' | 'pnl' | 'risk' | 'eod' | 'scenarios' | 'regulatory' | 'alerts' | 'system'

const TABS: { key: Tab; label: string; icon: typeof Activity }[] = [
  { key: 'positions', label: 'Positions', icon: BarChart3 },
  { key: 'trades', label: 'Trades', icon: ScrollText },
  { key: 'pnl', label: 'P&L', icon: TrendingUp },
  { key: 'risk', label: 'Risk', icon: Shield },
  { key: 'eod', label: 'EOD History', icon: CalendarDays },
  { key: 'scenarios', label: 'Scenarios', icon: FlaskConical },
  { key: 'regulatory', label: 'Regulatory', icon: Scale },
  { key: 'alerts', label: 'Alerts', icon: Bell },
  { key: 'system', label: 'System', icon: Server },
]

function App() {
  const workspace = useWorkspace()
  const [activeTab, setActiveTab] = useState<Tab>(
    (workspace.preferences.defaultTab as Tab) || 'positions',
  )
  const [whatIfOpen, setWhatIfOpen] = useState(false)
  const tabRefs = useRef<Map<Tab, HTMLButtonElement>>(new Map())

  const handleTabKeyDown = (e: React.KeyboardEvent) => {
    const tabKeys = TABS.map(t => t.key)
    const focusedElement = document.activeElement
    const currentIndex = tabKeys.findIndex(
      key => tabRefs.current.get(key) === focusedElement,
    )
    if (currentIndex === -1) return

    let nextIndex: number | null = null
    switch (e.key) {
      case 'ArrowRight':
        nextIndex = (currentIndex + 1) % tabKeys.length
        break
      case 'ArrowLeft':
        nextIndex = (currentIndex - 1 + tabKeys.length) % tabKeys.length
        break
      case 'Home':
        nextIndex = 0
        break
      case 'End':
        nextIndex = tabKeys.length - 1
        break
      default:
        return
    }
    e.preventDefault()
    tabRefs.current.get(tabKeys[nextIndex])?.focus()
  }

  const { positions: initialPositions, bookId: rawBookId, selectBook: rawSelectBook, refreshPositions, retryInitialLoad, loading: rawLoading, error: rawError } = usePositions()
  const bookSelector = useBookSelector()
  const hierarchy = useHierarchySelector()
  const isAllSelected = bookSelector.isAllSelected
  const effectiveBookId = hierarchy.effectiveBookId ?? (isAllSelected ? null : rawBookId)
  const { positions, connected, reconnecting, exhausted, lastConnectedAt, disconnectedSince, manualReconnect } = usePriceStream(
    isAllSelected ? bookSelector.aggregatedPositions : initialPositions,
    undefined,
    refreshPositions,
  )
  const { positionRisk } = usePositionRisk(effectiveBookId)

  const loading = rawLoading || bookSelector.loading
  const error = rawError || bookSelector.error

  // Keep selectBook wired for when hierarchy navigates to a specific book
  const handleBookChange = (id: string) => {
    if (id === ALL_BOOKS) {
      bookSelector.selectBook(ALL_BOOKS)
    } else {
      rawSelectBook(id)
      bookSelector.selectBook(id)
    }
  }
  void handleBookChange // used indirectly via hierarchy selection changes

  const bookId = hierarchy.effectiveBookId ?? (isAllSelected ? ALL_BOOKS : rawBookId)
  const notifications = useNotifications()
  const systemHealth = useSystemHealth()
  const whatIf = useWhatIf(effectiveBookId)
  const stress = useStressTest(bookId)
  const scenariosAll = useRunAllScenarios(bookId)
  const hierarchySummary = useHierarchySummary(hierarchy.selection)
  const { isDark, toggle: toggleTheme } = useTheme()
  const dataQuality = useDataQuality()
  const marketRegime = useMarketRegime()

  const [disconnectElapsed, setDisconnectElapsed] = useState(0)
  useEffect(() => {
    if (!disconnectedSince) return
    const update = () => setDisconnectElapsed(Math.floor((Date.now() - disconnectedSince.getTime()) / 1000))
    update()
    const timer = setInterval(update, 1000)
    return () => {
      clearInterval(timer)
      setDisconnectElapsed(0)
    }
  }, [disconnectedSince])

  return (
    <div className="min-h-screen bg-surface-50 dark:bg-surface-900 dark:text-slate-100 flex flex-col">
      <header className="bg-surface-900 text-white px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Activity className="h-5 w-5 text-primary-500" />
          <h1 className="text-lg font-bold tracking-tight">Kinetix</h1>
        </div>
        <div className="flex items-center gap-3">
          <HierarchySelector hierarchy={hierarchy} />
          <RegimeIndicator regime={marketRegime.regime} loading={marketRegime.loading} />
          <DataQualityIndicator
            status={reconnecting && dataQuality.status
              ? {
                  ...dataQuality.status,
                  overall: 'WARNING',
                  checks: [
                    { name: 'Price Feed', status: 'WARNING', message: 'WebSocket reconnecting', lastChecked: new Date().toISOString() },
                    ...dataQuality.status.checks,
                  ],
                }
              : dataQuality.status}
            loading={dataQuality.loading}
          />
          <button
            data-testid="save-workspace-button"
            onClick={() => {
              workspace.updatePreference('defaultTab', activeTab)
              workspace.updatePreference('defaultBook', bookId)
            }}
            className="p-1.5 rounded-md hover:bg-surface-800 transition-colors text-slate-300 hover:text-white"
            aria-label="Save workspace"
            title="Save current tab and book as defaults"
          >
            <Save className="h-4 w-4" />
          </button>
          <button
            data-testid="dark-mode-toggle"
            onClick={toggleTheme}
            className="p-1.5 rounded-md hover:bg-surface-800 transition-colors text-slate-300 hover:text-white"
            aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </button>
        </div>
      </header>

      <nav className="bg-surface-800 px-6 flex gap-1 border-b border-surface-700" data-testid="tab-bar" role="tablist" onKeyDown={handleTabKeyDown}>
        {TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            id={`tab-${key}`}
            data-testid={`tab-${key}`}
            ref={(el) => { if (el) tabRefs.current.set(key, el) }}
            role="tab"
            aria-selected={activeTab === key}
            tabIndex={activeTab === key ? 0 : -1}
            onClick={() => setActiveTab(key)}
            className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
              activeTab === key
                ? 'border-primary-500 text-white'
                : 'border-transparent text-slate-400 hover:text-white'
            }`}
          >
            <Icon className="h-4 w-4" />
            {label}
            {key === 'alerts' && notifications.alerts.length > 0 && (
              <span
                data-testid="alert-count-badge"
                className="ml-1 px-1.5 py-0.5 bg-primary-500 text-white text-xs rounded-full"
              >
                {notifications.alerts.length}
              </span>
            )}
            {key === 'system' && systemHealth.health?.status === 'DEGRADED' && (
              <span
                data-testid="system-degraded-dot"
                className="ml-1 inline-block h-2 w-2 rounded-full bg-red-500"
              />
            )}
          </button>
        ))}
      </nav>

      {exhausted && (
        <div
          data-testid="connection-lost-banner"
          className="bg-red-50 border-b border-red-200 text-red-700 px-6 py-2 text-sm font-medium flex items-center justify-between"
          role="alert"
        >
          <span>Connection lost. Live prices are unavailable.</span>
          <button
            data-testid="reconnect-button"
            onClick={manualReconnect}
            className="ml-4 px-3 py-1 text-sm font-medium bg-red-100 hover:bg-red-200 text-red-800 rounded-md transition-colors"
          >
            Reconnect
          </button>
        </div>
      )}

      {!exhausted && reconnecting && (() => {
        const healthUp = systemHealth.health?.status === 'UP'
        const healthDegraded = systemHealth.health?.status === 'DEGRADED'
        const healthUnknown = !systemHealth.health
        let bannerText: string
        let bannerClass: string
        if (healthDegraded) {
          bannerText = 'System update in progress. Prices paused.'
          bannerClass = 'bg-blue-50 border-b border-blue-200 text-blue-700'
        } else if (healthUnknown) {
          bannerText = 'Unable to reach server. Reconnecting...'
          bannerClass = 'bg-amber-100 border-b border-amber-300 text-amber-800'
        } else if (healthUp) {
          bannerText = 'Price feed interrupted. Reconnecting...'
          bannerClass = 'bg-amber-100 border-b border-amber-300 text-amber-800'
        } else {
          bannerText = 'Reconnecting...'
          bannerClass = 'bg-amber-100 border-b border-amber-300 text-amber-800'
        }
        const elapsed = disconnectElapsed > 0 ? ` (${disconnectElapsed}s)` : ''
        return (
          <div data-testid="reconnecting-banner" className={`${bannerClass} px-6 py-2 text-sm font-medium`} role="alert">
            {bannerText}{elapsed}
          </div>
        )
      })()}

      {systemHealth.health?.status === 'DEGRADED' && !reconnecting && (
        <div
          data-testid="maintenance-banner"
          className="bg-blue-50 border-b border-blue-200 text-blue-700 px-6 py-2 text-sm font-medium"
          role="status"
        >
          Scheduled maintenance in progress. Some features may be temporarily limited.
        </div>
      )}

      <main className="flex-1 p-6 dark:bg-surface-900" role="tabpanel" aria-labelledby={`tab-${activeTab}`}>
        {activeTab === 'system' ? (
          <SystemDashboard
            health={systemHealth.health}
            loading={systemHealth.loading}
            error={systemHealth.error}
            onRefresh={systemHealth.refresh}
          />
        ) : (
          <>
            {loading && <p className="text-gray-500">Loading positions...</p>}
            {error && (
              <div
                data-testid="load-error-card"
                className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start justify-between gap-4"
                role="alert"
              >
                <div>
                  <p className="text-red-700 font-medium text-sm">Failed to load positions</p>
                  <p className="text-red-600 text-sm mt-1">{error}</p>
                </div>
                <button
                  data-testid="retry-load-button"
                  onClick={retryInitialLoad}
                  className="flex-shrink-0 px-3 py-1.5 text-sm font-medium bg-red-100 hover:bg-red-200 text-red-800 rounded-md transition-colors"
                >
                  Retry
                </button>
              </div>
            )}

            {!loading && !error && (
              <>
                {activeTab === 'positions' && (
                  <div>
                    <div className="flex items-center justify-between mb-4">
                      <div />
                      <button
                        data-testid="whatif-open-button"
                        onClick={() => setWhatIfOpen(true)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-indigo-600 border border-indigo-300 rounded-md hover:bg-indigo-50 transition-colors"
                      >
                        <FlaskRound className="h-4 w-4" />
                        What-If
                      </button>
                    </div>
                    <div className="mb-4">
                      <BookSummaryCard
                        summary={hierarchySummary.summary}
                        baseCurrency={hierarchySummary.baseCurrency}
                        onBaseCurrencyChange={hierarchySummary.setBaseCurrency}
                        loading={hierarchySummary.loading}
                        title={hierarchySummary.summaryLabel}
                      />
                    </div>
                    <PositionGrid
                      positions={positions}
                      connected={connected}
                      reconnecting={reconnecting}
                      lastConnectedAt={lastConnectedAt}
                      positionRisk={positionRisk}
                      showBookColumn={hierarchy.selection.level !== 'book'}
                    />
                  </div>
                )}

                {activeTab === 'trades' && (
                  <TradeBlotter bookId={bookId} />
                )}

                {activeTab === 'pnl' && (
                  <PnlTab bookId={bookId} />
                )}

                {activeTab === 'risk' && (
                  <RiskTab
                    bookId={bookId}
                    stressResults={stress.results}
                    stressLoading={stress.loading}
                    onRunStress={stress.run}
                    onViewStressDetails={() => setActiveTab('scenarios')}
                    onWhatIf={() => setWhatIfOpen(true)}
                    onViewPnlTab={() => setActiveTab('pnl')}
                    aggregatedView={hierarchy.selection.level !== 'book'}
                    effectiveBookIds={hierarchy.effectiveBookIds}
                    bookGroupId={hierarchy.selection.deskId ?? hierarchy.selection.divisionId ?? (hierarchy.selection.level === 'firm' ? 'firm' : null)}
                    hierarchyLevel={hierarchy.selection.level === 'firm' ? 'FIRM' : hierarchy.selection.level === 'division' ? 'DIVISION' : hierarchy.selection.level === 'desk' ? 'DESK' : null}
                    onNavigateToBook={(bid) => hierarchy.setSelection({ level: 'book', divisionId: hierarchy.selection.divisionId, deskId: hierarchy.selection.deskId, bookId: bid })}
                  />
                )}

                {activeTab === 'eod' && (
                  <EodTimelineTab bookId={effectiveBookId} />
                )}

                {activeTab === 'scenarios' && (
                  <ScenariosTab
                    bookId={bookId}
                    results={scenariosAll.results}
                    loading={scenariosAll.loading}
                    error={scenariosAll.error}
                    selectedScenario={scenariosAll.selectedScenario}
                    onSelectScenario={scenariosAll.setSelectedScenario}
                    confidenceLevel={scenariosAll.confidenceLevel}
                    onConfidenceLevelChange={scenariosAll.setConfidenceLevel}
                    timeHorizonDays={scenariosAll.timeHorizonDays}
                    onTimeHorizonDaysChange={scenariosAll.setTimeHorizonDays}
                    onRunAll={scenariosAll.runAll}
                    onAppendResult={scenariosAll.appendResult}
                  />
                )}

                {activeTab === 'regulatory' && (
                  <RegulatoryTab bookId={bookId} />
                )}

                {activeTab === 'alerts' && (
                  <NotificationCenter
                    rules={notifications.rules}
                    alerts={notifications.alerts}
                    loading={notifications.loading}
                    error={notifications.error}
                    onCreateRule={notifications.createRule}
                    onDeleteRule={notifications.deleteRule}
                  />
                )}
              </>
            )}
          </>
        )}
      </main>

      <WhatIfPanel
        open={whatIfOpen}
        onClose={() => setWhatIfOpen(false)}
        trades={whatIf.trades}
        onAddTrade={whatIf.addTrade}
        onRemoveTrade={whatIf.removeTrade}
        onUpdateTrade={whatIf.updateTrade}
        onSubmit={whatIf.submit}
        onReset={whatIf.reset}
        result={whatIf.result}
        impact={whatIf.impact}
        loading={whatIf.loading}
        error={whatIf.error}
        errorTransient={whatIf.errorTransient}
        validationErrors={whatIf.validationErrors}
        onRetry={whatIf.retry}
        onCompareInDetail={() => {
          setWhatIfOpen(false)
          setActiveTab('risk')
        }}
      />
    </div>
  )
}

export default App
