import { useState } from 'react'
import { PositionGrid } from './components/PositionGrid'
import { VaRDashboard } from './components/VaRDashboard'
import { StressTestPanel } from './components/StressTestPanel'
import { GreeksPanel } from './components/GreeksPanel'
import { NotificationCenter } from './components/NotificationCenter'
import { RegulatoryDashboard } from './components/RegulatoryDashboard'
import { usePositions } from './hooks/usePositions'
import { usePriceStream } from './hooks/usePriceStream'
import { useVaR } from './hooks/useVaR'
import { useStressTest } from './hooks/useStressTest'
import { useGreeks } from './hooks/useGreeks'
import { useNotifications } from './hooks/useNotifications'
import { useRegulatory } from './hooks/useRegulatory'

type Tab = 'positions' | 'risk' | 'regulatory' | 'alerts'

const TABS: { key: Tab; label: string }[] = [
  { key: 'positions', label: 'Positions' },
  { key: 'risk', label: 'Risk' },
  { key: 'regulatory', label: 'Regulatory' },
  { key: 'alerts', label: 'Alerts' },
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

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <header className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Kinetix</h1>
        {portfolios.length > 0 && (
          <select
            data-testid="portfolio-selector"
            value={portfolioId ?? ''}
            onChange={(e) => selectPortfolio(e.target.value)}
            className="border rounded px-3 py-1.5 text-sm"
          >
            {portfolios.map((id) => (
              <option key={id} value={id}>{id}</option>
            ))}
          </select>
        )}
      </header>

      {/* Tab bar */}
      <nav className="flex gap-1 mb-6 border-b" data-testid="tab-bar">
        {TABS.map(({ key, label }) => (
          <button
            key={key}
            data-testid={`tab-${key}`}
            onClick={() => setActiveTab(key)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
              activeTab === key
                ? 'border-indigo-600 text-indigo-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {label}
          </button>
        ))}
      </nav>

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
              <div className="grid grid-cols-2 gap-4">
                <StressTestPanel
                  scenarios={stress.scenarios}
                  result={stress.result}
                  loading={stress.loading}
                  error={stress.error}
                  selectedScenario={stress.selectedScenario}
                  onScenarioChange={stress.setSelectedScenario}
                  onRun={stress.run}
                />
                <GreeksPanel
                  greeksResult={greeks.greeksResult}
                  loading={greeks.loading}
                  error={greeks.error}
                  volBump={greeks.volBump}
                  onVolBumpChange={greeks.setVolBump}
                />
              </div>
            </div>
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
    </div>
  )
}

export default App
