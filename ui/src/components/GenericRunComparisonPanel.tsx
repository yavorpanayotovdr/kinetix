import { Download } from 'lucide-react'
import { Button } from './ui'
import { RunSnapshotCard } from './RunSnapshotCard'
import { RunDiffSummary } from './RunDiffSummary'
import { ComponentDiffChart } from './ComponentDiffChart'
import { PositionDiffTable } from './PositionDiffTable'
import { VaRAttributionPanel } from './VaRAttributionPanel'
import { exportToCsv } from '../utils/exportCsv'
import type { RunComparisonResponseDto, VaRAttributionDto } from '../types'

interface GenericRunComparisonPanelProps {
  comparison: RunComparisonResponseDto
  attribution: VaRAttributionDto | null
  attributionLoading: boolean
  onRequestAttribution: () => void
  threshold: number
  onThresholdChange: (threshold: number) => void
  showAttribution?: boolean
}

export function GenericRunComparisonPanel({
  comparison,
  attribution,
  attributionLoading,
  onRequestAttribution,
  threshold,
  onThresholdChange,
  showAttribution = true,
}: GenericRunComparisonPanelProps) {
  const handleExportCsv = () => {
    const headers = ['Metric', 'Base', 'Target', 'Change']
    const diff = comparison.portfolioDiff
    const rows: string[][] = [
      ['VaR', String(comparison.baseRun.varValue ?? ''), String(comparison.targetRun.varValue ?? ''), String(diff.varChange)],
      ['ES', String(comparison.baseRun.es ?? ''), String(comparison.targetRun.es ?? ''), String(diff.esChange)],
      ['PV', String(comparison.baseRun.pv ?? ''), String(comparison.targetRun.pv ?? ''), String(diff.pvChange)],
      ['Delta', String(comparison.baseRun.delta ?? ''), String(comparison.targetRun.delta ?? ''), String(diff.deltaChange)],
      ['Gamma', String(comparison.baseRun.gamma ?? ''), String(comparison.targetRun.gamma ?? ''), String(diff.gammaChange)],
      ['Vega', String(comparison.baseRun.vega ?? ''), String(comparison.targetRun.vega ?? ''), String(diff.vegaChange)],
      ['Theta', String(comparison.baseRun.theta ?? ''), String(comparison.targetRun.theta ?? ''), String(diff.thetaChange)],
      ['Rho', String(comparison.baseRun.rho ?? ''), String(comparison.targetRun.rho ?? ''), String(diff.rhoChange)],
    ]
    exportToCsv(`comparison-${comparison.comparisonId}.csv`, headers, rows)
  }

  return (
    <div data-testid="run-comparison-panel" className="space-y-4">
      <div className="flex justify-end">
        <Button
          data-testid="export-comparison-csv"
          variant="secondary"
          onClick={handleExportCsv}
          aria-label="Export comparison to CSV"
        >
          <Download className="h-4 w-4 mr-1" aria-hidden="true" />
          Export CSV
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <RunSnapshotCard snapshot={comparison.baseRun} title="Base" />
        <RunSnapshotCard snapshot={comparison.targetRun} title="Target" />
      </div>

      <RunDiffSummary diff={comparison.portfolioDiff} />

      {comparison.componentDiffs.length > 0 && (
        <ComponentDiffChart diffs={comparison.componentDiffs} />
      )}

      <PositionDiffTable
        diffs={comparison.positionDiffs}
        threshold={threshold}
        onThresholdChange={onThresholdChange}
      />

      {comparison.parameterDiffs.length > 0 && (
        <div data-testid="parameter-diffs" className="text-sm">
          <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">
            Parameter Differences
          </h3>
          <div className="space-y-1">
            {comparison.parameterDiffs.map((p) => (
              <div
                key={p.paramName}
                className="flex gap-2 text-slate-600 dark:text-slate-300"
              >
                <span className="font-medium">{p.paramName}:</span>
                <span className="text-slate-500 dark:text-slate-400">{p.baseValue ?? '—'}</span>
                <span aria-hidden="true">→</span>
                <span>{p.targetValue ?? '—'}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {showAttribution && (
        <VaRAttributionPanel
          attribution={attribution}
          loading={attributionLoading}
          onRequest={onRequestAttribution}
        />
      )}
    </div>
  )
}
