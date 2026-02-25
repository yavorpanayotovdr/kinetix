import { useState } from 'react'
import { Activity, BarChart3, Shield, FlaskConical, Scale, Bell, Server } from 'lucide-react'
import { PositionGrid } from './components/PositionGrid'
import { VaRDashboard } from './components/VaRDashboard'
import { StressTestPanel } from './components/StressTestPanel'
import { GreeksPanel } from './components/GreeksPanel'
import { JobHistory } from './components/JobHistory'
import { NotificationCenter } from './components/NotificationCenter'
import { RegulatoryDashboard } from './components/RegulatoryDashboard'
import { SystemDashboard } from './components/SystemDashboard'
import { usePositions } from './hooks/usePositions'
import { usePriceStream } from './hooks/usePriceStream'
import { useVaR } from './hooks/useVaR'
import { useStressTest } from './hooks/useStressTest'
import { useGreeks } from './hooks/useGreeks'
import { useNotifications } from './hooks/useNotifications'
import { useRegulatory } from './hooks/useRegulatory'
import { useSystemHealth } from './hooks/useSystemHealth'

type Tab = 'positions' | 'risk' | 'scenarios' | 'regulatory' | 'alerts' | 'system'

const TABS: { key: Tab; label: string; icon: typeof Activity }[] = [
  { key: 'positions', label: 'Positions', icon: BarChart3 },
  { key: 'risk', label: 'Risk', icon: Shield },
  { key: 'scenarios', label: 'Scenarios', icon: FlaskConical },
  { key: 'regulatory', label: 'Regulatory', icon: Scale },
  { key: 'alerts', label: 'Alerts', icon: Bell },
  { key: 'system', label: 'System', icon: Server },
]

function App() {
  const [activeTab, setActiveTab] = useState<Tab>('positions')

  const { positions: initialPositions, portfolioId, portfolios, selectPortfolio, loading, error } = usePositions()
  const { positions, connected } = usePriceStream(initialPositions)
  const { varResult, history, loading: varLoading, error: varError, refresh } = useVaR(portfolioId)
  const stress = useStressTest(portfolioId)
  const greeks = useGreeks(portfolioId)
  const notifications = useNotifications()
  const regulatory = useRegulatory(portfolioId)
  const systemHealth = useSystemHealth()

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
                  <PositionGrid positions={positions} connected={connected} />
                )}

                {activeTab === 'risk' && (
                  <div>
                    <VaRDashboard
                      varResult={varResult}
                      history={history}
                      loading={varLoading}
                      error={varError}
                      onRefresh={refresh}
                    />
                    <GreeksPanel
                      greeksResult={greeks.greeksResult}
                      loading={greeks.loading}
                      error={greeks.error}
                      volBump={greeks.volBump}
                      onVolBumpChange={greeks.setVolBump}
                    />
                    <div className="mt-4">
                      <JobHistory portfolioId={portfolioId} />
                    </div>
                  </div>
                )}

                {activeTab === 'scenarios' && (
                  <StressTestPanel
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
                  <RegulatoryDashboard
                    result={regulatory.result}
                    loading={regulatory.loading}
                    error={regulatory.error}
                    onCalculate={regulatory.calculate}
                    onDownloadCsv={regulatory.downloadCsv}
                    onDownloadXbrl={regulatory.downloadXbrl}
                  />
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
    </div>
  )
}

export default App
