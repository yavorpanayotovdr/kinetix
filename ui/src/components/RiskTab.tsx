import { useCallback, useState } from 'react'
import { useVaR } from '../hooks/useVaR'
import { VaRDashboard } from './VaRDashboard'
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

  const [jobRefreshSignal, setJobRefreshSignal] = useState(0)

  const handleRefresh = useCallback(async () => {
    await refresh()
    setJobRefreshSignal((prev) => prev + 1)
  }, [refresh])

  return (
    <div>
      <VaRDashboard
        varResult={varResult}
        filteredHistory={filteredHistory}
        loading={varLoading}
        refreshing={varRefreshing}
        error={varError}
        onRefresh={handleRefresh}
        timeRange={varTimeRange}
        setTimeRange={setVarTimeRange}
        zoomIn={varZoomIn}
        resetZoom={varResetZoom}
        zoomDepth={varZoomDepth}
        greeksResult={greeksResult}
        volBump={volBump}
        onVolBumpChange={setVolBump}
      />
      <div className="mt-4">
        <JobHistory portfolioId={portfolioId} refreshSignal={jobRefreshSignal} />
      </div>
    </div>
  )
}
