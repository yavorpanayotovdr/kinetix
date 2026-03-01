import { useState } from 'react'
import { Activity, BarChart3, ScrollText, TrendingUp, Shield, FlaskConical, Scale, Bell, Server, FlaskRound } from 'lucide-react'
import { PositionGrid } from './components/PositionGrid'
import { TradeBlotter } from './components/TradeBlotter'
import { NotificationCenter } from './components/NotificationCenter'
import { SystemDashboard } from './components/SystemDashboard'
import { RiskTab } from './components/RiskTab'
import { ScenariosTab } from './components/ScenariosTab'
import { RegulatoryTab } from './components/RegulatoryTab'
import { PnlTab } from './components/PnlTab'
import { WhatIfPanel } from './components/WhatIfPanel'
import { usePositions } from './hooks/usePositions'
import { usePriceStream } from './hooks/usePriceStream'
import { useNotifications } from './hooks/useNotifications'
import { usePositionRisk } from './hooks/usePositionRisk'
import { useSystemHealth } from './hooks/useSystemHealth'
import { useWhatIf } from './hooks/useWhatIf'
import { useStressTest } from './hooks/useStressTest'

type Tab = 'positions' | 'trades' | 'pnl' | 'risk' | 'scenarios' | 'regulatory' | 'alerts' | 'system'

const TABS: { key: Tab; label: string; icon: typeof Activity }[] = [
  { key: 'positions', label: 'Positions', icon: BarChart3 },
  { key: 'trades', label: 'Trades', icon: ScrollText },
  { key: 'pnl', label: 'P&L', icon: TrendingUp },
  { key: 'risk', label: 'Risk', icon: Shield },
  { key: 'scenarios', label: 'Scenarios', icon: FlaskConical },
  { key: 'regulatory', label: 'Regulatory', icon: Scale },
  { key: 'alerts', label: 'Alerts', icon: Bell },
  { key: 'system', label: 'System', icon: Server },
]

function App() {
  const [activeTab, setActiveTab] = useState<Tab>('positions')
  const [whatIfOpen, setWhatIfOpen] = useState(false)

  const { positions: initialPositions, portfolioId, portfolios, selectPortfolio, loading, error } = usePositions()
  const { positions, connected } = usePriceStream(initialPositions)
  const { positionRisk } = usePositionRisk(portfolioId)
  const notifications = useNotifications()
  const systemHealth = useSystemHealth()
  const whatIf = useWhatIf(portfolioId)
  const stress = useStressTest(portfolioId)

  return (
    <div className="min-h-screen bg-surface-50 flex flex-col">
      <header className="bg-surface-900 text-white px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Activity className="h-5 w-5 text-primary-500" />
          <h1 className="text-lg font-bold tracking-tight">Kinetix</h1>
        </div>
        {portfolios.length > 0 && (
          <select
            data-testid="portfolio-selector"
            value={portfolioId ?? ''}
            onChange={(e) => selectPortfolio(e.target.value)}
            className="bg-surface-800 border border-surface-700 text-white rounded-md px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
          >
            {portfolios.map((id) => (
              <option key={id} value={id}>{id}</option>
            ))}
          </select>
        )}
      </header>

      <nav className="bg-surface-800 px-6 flex gap-1 border-b border-surface-700" data-testid="tab-bar">
        {TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            data-testid={`tab-${key}`}
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

      <main className="flex-1 p-6">
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
            {error && <p className="text-red-600">{error}</p>}

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
                    <PositionGrid positions={positions} connected={connected} positionRisk={positionRisk} />
                  </div>
                )}

                {activeTab === 'trades' && (
                  <TradeBlotter portfolioId={portfolioId} />
                )}

                {activeTab === 'pnl' && (
                  <PnlTab portfolioId={portfolioId} />
                )}

                {activeTab === 'risk' && (
                  <RiskTab
                    portfolioId={portfolioId}
                    stressResults={stress.results}
                    stressLoading={stress.loading}
                    onRunStress={stress.run}
                    onViewStressDetails={() => setActiveTab('scenarios')}
                    onWhatIf={() => setWhatIfOpen(true)}
                    onViewPnlTab={() => setActiveTab('pnl')}
                  />
                )}

                {activeTab === 'scenarios' && (
                  <ScenariosTab
                    scenarios={stress.scenarios}
                    result={stress.result}
                    loading={stress.loading}
                    error={stress.error}
                    selectedScenario={stress.selectedScenario}
                    onScenarioChange={stress.setSelectedScenario}
                    onRun={stress.run}
                  />
                )}

                {activeTab === 'regulatory' && (
                  <RegulatoryTab portfolioId={portfolioId} />
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
      />
    </div>
  )
}

export default App
