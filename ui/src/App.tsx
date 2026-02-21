import { PositionGrid } from './components/PositionGrid'
import { VaRDashboard } from './components/VaRDashboard'
import { usePositions } from './hooks/usePositions'
import { usePriceStream } from './hooks/usePriceStream'
import { useVaR } from './hooks/useVaR'

function App() {
  const { positions: initialPositions, portfolioId, loading, error } = usePositions()
  const { positions, connected } = usePriceStream(initialPositions)
  const { varResult, history, loading: varLoading, error: varError, refresh } = useVaR(portfolioId)

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <header className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Kinetix</h1>
        {portfolioId && (
          <span className="text-sm text-gray-500">{portfolioId}</span>
        )}
      </header>

      {loading && <p className="text-gray-500">Loading positions...</p>}
      {error && <p className="text-red-600">{error}</p>}
      {!loading && !error && (
        <>
          <VaRDashboard
            varResult={varResult}
            history={history}
            loading={varLoading}
            error={varError}
            onRefresh={refresh}
          />
          <PositionGrid positions={positions} connected={connected} />
        </>
      )}
    </div>
  )
}

export default App
