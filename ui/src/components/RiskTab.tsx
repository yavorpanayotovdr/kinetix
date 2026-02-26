import { useVaR } from '../hooks/useVaR'
import { VaRDashboard } from './VaRDashboard'
import { GreeksPanel } from './GreeksPanel'
import { JobHistory } from './JobHistory'

interface RiskTabProps {
  portfolioId: string | null
}

export function RiskTab({ portfolioId }: RiskTabProps) {
  const {
    varResult,
    greeksResult,
    loading: varLoading,
    refreshing: varRefreshing,
    error: varError,
    refresh,
    filteredHistory,
    timeRange: varTimeRange,
    setTimeRange: setVarTimeRange,
    zoomIn: varZoomIn,
    resetZoom: varResetZoom,
    zoomDepth: varZoomDepth,
    volBump,
    setVolBump,
  } = useVaR(portfolioId)

  return (
    <div>
      <VaRDashboard
        varResult={varResult}
        filteredHistory={filteredHistory}
        loading={varLoading}
        refreshing={varRefreshing}
        error={varError}
        onRefresh={refresh}
        timeRange={varTimeRange}
        setTimeRange={setVarTimeRange}
        zoomIn={varZoomIn}
        resetZoom={varResetZoom}
        zoomDepth={varZoomDepth}
      />
      <GreeksPanel
        greeksResult={greeksResult}
        loading={varLoading}
        error={varError}
        volBump={volBump}
        onVolBumpChange={setVolBump}
      />
      <div className="mt-4">
        <JobHistory portfolioId={portfolioId} />
      </div>
    </div>
  )
}
