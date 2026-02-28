import { useCallback, useState } from 'react'
import { useVaR } from '../hooks/useVaR'
import { usePositionRisk } from '../hooks/usePositionRisk'
import { useVarLimit } from '../hooks/useVarLimit'
import { useAlerts } from '../hooks/useAlerts'
import { useSodBaseline } from '../hooks/useSodBaseline'
import { usePnlAttribution } from '../hooks/usePnlAttribution'
import type { StressTestResultDto } from '../types'
import { VaRDashboard } from './VaRDashboard'
import { PositionRiskTable } from './PositionRiskTable'
import { JobHistory } from './JobHistory'
import { RiskAlertBanner } from './RiskAlertBanner'
import { StressSummaryCard } from './StressSummaryCard'
import { PnlSummaryCard } from './PnlSummaryCard'

interface RiskTabProps {
  portfolioId: string | null
  stressResult: StressTestResultDto | null
  stressLoading: boolean
  onRunStress: () => void
  onViewStressDetails: () => void
  onWhatIf?: () => void
}

export function RiskTab({
  portfolioId,
  stressResult,
  stressLoading,
  onRunStress,
  onViewStressDetails,
  onWhatIf,
}: RiskTabProps) {
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

  const { varLimit } = useVarLimit()
  const { alerts, dismissAlert } = useAlerts()

  const sod = useSodBaseline(portfolioId)
  const { data: pnlData } = usePnlAttribution(portfolioId)

  const [jobRefreshSignal, setJobRefreshSignal] = useState(0)

  const handleRefresh = useCallback(async () => {
    await Promise.all([refresh(), refreshPositionRisk()])
    setJobRefreshSignal((prev) => prev + 1)
  }, [refresh, refreshPositionRisk])

  return (
    <div>
      {alerts.length > 0 && (
        <div className="mb-4">
          <RiskAlertBanner alerts={alerts} onDismiss={dismissAlert} />
        </div>
      )}
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
        varLimit={varLimit}
        onWhatIf={onWhatIf}
      />
      <div className="mt-4">
        <PositionRiskTable data={positionRisk} loading={positionRiskLoading} error={positionRiskError} />
      </div>
      <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4">
        <PnlSummaryCard
          sodStatus={sod.status}
          pnlData={pnlData}
          computing={sod.computing}
          onComputePnl={sod.computeAttribution}
        />
        <StressSummaryCard
          result={stressResult}
          loading={stressLoading}
          onRun={onRunStress}
          onViewDetails={onViewStressDetails}
        />
      </div>
      <div className="mt-4">
        <JobHistory portfolioId={portfolioId} refreshSignal={jobRefreshSignal} />
      </div>
    </div>
  )
}
