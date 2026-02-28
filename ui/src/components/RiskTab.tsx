import { useCallback, useState } from 'react'
import { useVaR } from '../hooks/useVaR'
import { usePositionRisk } from '../hooks/usePositionRisk'
import { VaRDashboard } from './VaRDashboard'
import { PositionRiskTable } from './PositionRiskTable'
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
  } = useVaR(portfolioId)

  const {
    positionRisk,
    loading: positionRiskLoading,
    error: positionRiskError,
    refresh: refreshPositionRisk,
  } = usePositionRisk(portfolioId)

  const [jobRefreshSignal, setJobRefreshSignal] = useState(0)

  const handleRefresh = useCallback(async () => {
    await Promise.all([refresh(), refreshPositionRisk()])
    setJobRefreshSignal((prev) => prev + 1)
  }, [refresh, refreshPositionRisk])

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
      />
      <div className="mt-4">
        <PositionRiskTable data={positionRisk} loading={positionRiskLoading} error={positionRiskError} />
      </div>
      <div className="mt-4">
        <JobHistory portfolioId={portfolioId} refreshSignal={jobRefreshSignal} />
      </div>
    </div>
  )
}
