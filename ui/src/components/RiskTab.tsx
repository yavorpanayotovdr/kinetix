import { useCallback, useRef, useState } from 'react'
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
import { RunComparisonContainer } from './RunComparisonContainer'

type RiskSubTab = 'dashboard' | 'run-compare'

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
  const [subTab, setSubTab] = useState<RiskSubTab>('dashboard')
  const [valuationDate, setValuationDate] = useState<string | null>(null)
  const [pendingJobCompare, setPendingJobCompare] = useState<{ baseJobId: string; targetJobId: string } | null>(null)

  const handleCompareJobs = useCallback((baseJobId: string, targetJobId: string) => {
    setPendingJobCompare({ baseJobId, targetJobId })
    setSubTab('run-compare')
  }, [])

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

  const subTabs: { key: RiskSubTab; label: string }[] = [
    { key: 'dashboard', label: 'Dashboard' },
    { key: 'run-compare', label: 'Run Compare' },
  ]

  return (
    <div>
      {/* Sub-tab bar */}
      <div className="flex gap-1 mb-4 border-b border-slate-200 dark:border-surface-700">
        {subTabs.map((t) => (
          <button
            key={t.key}
            data-testid={`risk-subtab-${t.key}`}
            onClick={() => setSubTab(t.key)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              subTab === t.key
                ? 'border-primary-500 text-primary-600 dark:text-primary-400'
                : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {subTab === 'dashboard' && (
        <>
          {alerts.length > 0 && (
            <div className="mb-2">
              <RiskAlertBanner alerts={alerts} onDismiss={dismissAlert} />
            </div>
          )}
          <div className="flex items-center justify-between mb-2">
            <ValuationDatePicker value={valuationDate} onChange={setValuationDate} />
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
            <JobHistory portfolioId={portfolioId} refreshSignal={jobRefreshSignal} onCompareJobs={handleCompareJobs} />
          </div>
        </>
      )}

      {subTab === 'run-compare' && (
        <RunComparisonContainer portfolioId={portfolioId} initialJobIds={pendingJobCompare} />
      )}
    </div>
  )
}
