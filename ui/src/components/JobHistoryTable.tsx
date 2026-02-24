import { Fragment } from 'react'
import type { CalculationJobSummaryDto, CalculationJobDetailDto } from '../types'
import { Badge, Spinner } from './ui'
import { JobTimeline } from './JobTimeline'
import { formatRelativeTime } from '../utils/format'

interface JobHistoryTableProps {
  runs: CalculationJobSummaryDto[]
  expandedJobs: Record<string, CalculationJobDetailDto>
  loadingJobIds: Set<string>
  onSelectJob: (jobId: string) => void
  onCloseJob: (jobId: string) => void
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

export function JobHistoryTable({ runs, expandedJobs, loadingJobIds, onSelectJob, onCloseJob }: JobHistoryTableProps) {
  if (runs.length === 0) {
    return (
      <div data-testid="job-history-empty" className="text-sm text-slate-400 py-4 text-center">
        No calculation jobs yet.
      </div>
    )
  }

  return (
    <div data-testid="job-history-table">
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
          {runs.map((run) => {
            const isExpanded = run.jobId in expandedJobs
            const isLoading = loadingJobIds.has(run.jobId)
            const detail = expandedJobs[run.jobId]

            return (
              <Fragment key={run.jobId}>
                <tr
                  data-testid={`job-row-${run.jobId}`}
                  onClick={() => onSelectJob(run.jobId)}
                  className={`cursor-pointer hover:bg-slate-50 border-b border-slate-100 ${
                    isExpanded || isLoading ? 'bg-primary-50' : ''
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
                {(isExpanded || isLoading) && (
                  <tr data-testid={`job-detail-row-${run.jobId}`}>
                    <td colSpan={6} className="p-0">
                      <div data-testid="job-detail-panel" className="px-4 py-3 bg-slate-50 border-b border-slate-200">
                        <div className="flex items-center justify-between mb-2">
                          <h4 className="text-sm font-semibold text-slate-700">Job Detail</h4>
                          <button
                            data-testid={`close-detail-${run.jobId}`}
                            onClick={(e) => {
                              e.stopPropagation()
                              onCloseJob(run.jobId)
                            }}
                            className="text-xs text-slate-400 hover:text-slate-600"
                          >
                            Close
                          </button>
                        </div>
                        {isLoading && (
                          <div data-testid="detail-loading" className="flex items-center gap-2 text-sm text-slate-500 py-2">
                            <Spinner size="sm" />
                            Loading job details...
                          </div>
                        )}
                        {detail && !isLoading && (
                          <>
                            <JobTimeline steps={detail.steps} />
                            {detail.error && (
                              <p className="mt-2 text-xs text-red-600">Error: {detail.error}</p>
                            )}
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
