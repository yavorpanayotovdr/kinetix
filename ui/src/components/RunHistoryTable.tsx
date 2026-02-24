import { Fragment } from 'react'
import type { CalculationRunSummaryDto, CalculationRunDetailDto } from '../types'
import { Badge } from './ui'
import { PipelineTimeline } from './PipelineTimeline'
import { formatRelativeTime } from '../utils/format'

interface RunHistoryTableProps {
  runs: CalculationRunSummaryDto[]
  selectedRunId: string | null
  selectedRun: CalculationRunDetailDto | null
  onSelectRun: (runId: string) => void
  onClearSelection: () => void
}

const STATUS_VARIANT: Record<string, 'success' | 'critical' | 'info' | 'neutral'> = {
  COMPLETED: 'success',
  FAILED: 'critical',
  RUNNING: 'info',
}

const TRIGGER_VARIANT: Record<string, 'info' | 'neutral' | 'warning'> = {
  ON_DEMAND: 'info',
  SCHEDULED: 'neutral',
  TRADE_EVENT: 'warning',
  PRICE_EVENT: 'warning',
}

export function RunHistoryTable({ runs, selectedRunId, selectedRun, onSelectRun, onClearSelection }: RunHistoryTableProps) {
  if (runs.length === 0) {
    return (
      <div data-testid="run-history-empty" className="text-sm text-slate-400 py-4 text-center">
        No calculation runs yet.
      </div>
    )
  }

  return (
    <div data-testid="run-history-table">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-xs text-slate-500 border-b border-slate-200">
            <th className="py-2 pr-3">Time</th>
            <th className="py-2 pr-3">Trigger</th>
            <th className="py-2 pr-3">Status</th>
            <th className="py-2 pr-3">Duration</th>
            <th className="py-2 pr-3">VaR</th>
            <th className="py-2">ES</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <Fragment key={run.runId}>
              <tr
                data-testid={`run-row-${run.runId}`}
                onClick={() => onSelectRun(run.runId)}
                className={`cursor-pointer hover:bg-slate-50 border-b border-slate-100 ${
                  selectedRunId === run.runId ? 'bg-primary-50' : ''
                }`}
              >
                <td className="py-2 pr-3 text-slate-600">{formatRelativeTime(run.startedAt)}</td>
                <td className="py-2 pr-3">
                  <Badge variant={TRIGGER_VARIANT[run.triggerType] ?? 'neutral'}>{run.triggerType}</Badge>
                </td>
                <td className="py-2 pr-3">
                  <Badge variant={STATUS_VARIANT[run.status] ?? 'neutral'}>{run.status}</Badge>
                </td>
                <td className="py-2 pr-3 text-slate-600">
                  {run.durationMs != null ? `${run.durationMs}ms` : '-'}
                </td>
                <td className="py-2 pr-3 text-slate-700 font-mono">
                  {run.varValue != null ? run.varValue.toLocaleString(undefined, { maximumFractionDigits: 2 }) : '-'}
                </td>
                <td className="py-2 text-slate-700 font-mono">
                  {run.expectedShortfall != null
                    ? run.expectedShortfall.toLocaleString(undefined, { maximumFractionDigits: 2 })
                    : '-'}
                </td>
              </tr>
              {selectedRun?.runId === run.runId && (
                <tr data-testid="run-detail-row">
                  <td colSpan={6} className="p-0">
                    <div data-testid="run-detail-panel" className="px-4 py-3 bg-slate-50 border-b border-slate-200">
                      <div className="flex items-center justify-between mb-2">
                        <h4 className="text-sm font-semibold text-slate-700">Pipeline Detail</h4>
                        <button
                          data-testid="close-detail"
                          onClick={(e) => {
                            e.stopPropagation()
                            onClearSelection()
                          }}
                          className="text-xs text-slate-400 hover:text-slate-600"
                        >
                          Close
                        </button>
                      </div>
                      <PipelineTimeline steps={selectedRun.steps} />
                      {selectedRun.error && (
                        <p className="mt-2 text-xs text-red-600">Error: {selectedRun.error}</p>
                      )}
                    </div>
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
        </tbody>
      </table>
    </div>
  )
}
