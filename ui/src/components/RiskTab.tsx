import { useCallback, useMemo, useState } from 'react'
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
import { LastUpdatedIndicator } from './LastUpdatedIndicator'
import { ValuationDatePicker } from './ValuationDatePicker'

interface RiskTabProps {
  portfolioId: string | null
  stressResults: StressTestResultDto[]
  stressLoading: boolean
  onRunStress: () => void
  onViewStressDetails: () => void
  onWhatIf?: () => void
  onViewPnlTab?: () => void
}

export function RiskTab({
  portfolioId,
  stressResults,
  stressLoading,
  onRunStress,
  onViewStressDetails,
  onWhatIf,
  onViewPnlTab,
}: RiskTabProps) {
  const [valuationDate, setValuationDate] = useState<string | null>(null)

  const {
    varResult,
    greeksResult,
    loading: varLoading,
    historyLoading: varHistoryLoading,
    refreshing: varRefreshing,
    error: varError,
    refresh,
    filteredHistory,
    timeRange: varTimeRange,
    setTimeRange: setVarTimeRange,
    zoomIn: varZoomIn,
    resetZoom: varResetZoom,
    zoomDepth: varZoomDepth,
    selectedConfidenceLevel,
    setSelectedConfidenceLevel,
    isLive,
  } = useVaR(portfolioId, valuationDate)

  const {
    positionRisk,
    loading: positionRiskLoading,
    error: positionRiskError,
    refresh: refreshPositionRisk,
  } = usePositionRisk(portfolioId, valuationDate)

  const { varLimit } = useVarLimit()
  const { alerts, dismissAlert } = useAlerts()

  const sod = useSodBaseline(portfolioId)
  const { data: pnlData } = usePnlAttribution(portfolioId)

  const [jobRefreshSignal, setJobRefreshSignal] = useState(0)

  const handleRefresh = useCallback(async () => {
    await refresh()
    await refreshPositionRisk()
    setJobRefreshSignal((prev) => prev + 1)
  }, [refresh, refreshPositionRisk])

  const lastUpdated = varResult?.calculatedAt ?? null

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-4">
          {alerts.length > 0 && (
            <div className="flex-1 mr-4">
              <RiskAlertBanner alerts={alerts} onDismiss={dismissAlert} />
            </div>
          )}
          <ValuationDatePicker value={valuationDate} onChange={setValuationDate} />
        </div>
        <LastUpdatedIndicator timestamp={lastUpdated} />
      </div>
      <VaRDashboard
        varResult={varResult}
        filteredHistory={filteredHistory}
        loading={varLoading}
        historyLoading={varHistoryLoading}
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
        selectedConfidenceLevel={selectedConfidenceLevel}
        onConfidenceLevelChange={setSelectedConfidenceLevel}
        isLive={isLive}
        valuationDate={valuationDate}
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
          onViewFullAttribution={onViewPnlTab}
        />
        <StressSummaryCard
          results={stressResults}
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
