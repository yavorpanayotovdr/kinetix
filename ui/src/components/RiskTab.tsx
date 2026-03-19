import { useCallback, useState } from 'react'
import { useVaR } from '../hooks/useVaR'
import { useCrossBookVaR } from '../hooks/useCrossBookVaR'
import { usePositionRisk } from '../hooks/usePositionRisk'
import { useVarLimit } from '../hooks/useVarLimit'
import { useAlerts } from '../hooks/useAlerts'
import { useSodBaseline } from '../hooks/useSodBaseline'
import { usePnlAttribution } from '../hooks/usePnlAttribution'
import type { StressTestResultDto } from '../types'
import { VaRDashboard } from './VaRDashboard'
import { PositionRiskTable } from './PositionRiskTable'
import { BookContributionTable } from './BookContributionTable'
import { JobHistory } from './JobHistory'
import { RiskAlertBanner } from './RiskAlertBanner'
import { StressSummaryCard } from './StressSummaryCard'
import { PnlSummaryCard } from './PnlSummaryCard'
import { LastUpdatedIndicator } from './LastUpdatedIndicator'
import { ValuationDatePicker } from './ValuationDatePicker'
import { RunComparisonContainer } from './RunComparisonContainer'
import { CorrelationHeatmap } from './CorrelationHeatmap'

type RiskSubTab = 'dashboard' | 'run-compare'

interface RiskTabProps {
  bookId: string | null
  stressResults: StressTestResultDto[]
  stressLoading: boolean
  onRunStress: () => void
  onViewStressDetails: () => void
  onWhatIf?: () => void
  onViewPnlTab?: () => void
  aggregatedView?: boolean
  effectiveBookIds?: string[]
  bookGroupId?: string | null
  onNavigateToBook?: (bookId: string) => void
}

export function RiskTab({
  bookId,
  stressResults,
  stressLoading,
  onRunStress,
  onViewStressDetails,
  onWhatIf,
  onViewPnlTab,
  aggregatedView = false,
  effectiveBookIds = [],
  bookGroupId = null,
  onNavigateToBook,
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
  } = useVaR(bookId, valuationDate)

  const {
    positionRisk,
    loading: positionRiskLoading,
    error: positionRiskError,
    refresh: refreshPositionRisk,
  } = usePositionRisk(bookId, valuationDate)

  const {
    result: crossBookResult,
    loading: crossBookLoading,
    refreshing: crossBookRefreshing,
    error: crossBookError,
    refresh: crossBookRefresh,
  } = useCrossBookVaR(
    aggregatedView ? effectiveBookIds : [],
    aggregatedView ? bookGroupId : null,
  )

  const { varLimit } = useVarLimit()
  const { alerts, dismissAlert } = useAlerts()

  const sod = useSodBaseline(bookId)
  const { data: pnlData } = usePnlAttribution(bookId)

  const [jobRefreshSignal, setJobRefreshSignal] = useState(0)

  const handleRefresh = useCallback(async () => {
    if (aggregatedView) {
      await crossBookRefresh()
    }
    await refresh()
    await refreshPositionRisk()
    setJobRefreshSignal((prev) => prev + 1)
  }, [refresh, refreshPositionRisk, crossBookRefresh, aggregatedView])

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
          {aggregatedView && !crossBookResult && !crossBookLoading && (
            <div
              data-testid="aggregated-var-note"
              className="mb-3 px-3 py-2 text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-md"
            >
              Showing sum of book VaRs — click Recalculate All to compute diversified portfolio VaR.
            </div>
          )}
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
            refreshing={varRefreshing || crossBookRefreshing}
            error={crossBookError || varError}
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
            totalStandaloneVar={crossBookResult ? Number(crossBookResult.totalStandaloneVar) : undefined}
            diversificationBenefit={crossBookResult ? Number(crossBookResult.diversificationBenefit) : undefined}
          />
          {aggregatedView && crossBookResult && (
            <>
              <div className="mt-4">
                <BookContributionTable
                  contributions={crossBookResult.bookContributions}
                  onBookClick={onNavigateToBook}
                />
              </div>
              <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4">
                <CorrelationHeatmap
                  assetClasses={[...new Set(crossBookResult.componentBreakdown.map((c) => c.assetClass))]}
                />
              </div>
            </>
          )}
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
            <JobHistory bookId={bookId} refreshSignal={jobRefreshSignal} onCompareJobs={handleCompareJobs} />
          </div>
        </>
      )}

      {subTab === 'run-compare' && (
        <RunComparisonContainer bookId={bookId} initialJobIds={pendingJobCompare} />
      )}
    </div>
  )
}
