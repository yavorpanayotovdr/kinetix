import { Fragment, useState } from 'react'
import { Search } from 'lucide-react'
import type { ValuationJobSummaryDto, ValuationJobDetailDto } from '../types'
import { Badge, Spinner } from './ui'
import { JobTimeline } from './JobTimeline'
import { formatRelativeTime } from '../utils/format'

interface JobHistoryTableProps {
  runs: ValuationJobSummaryDto[]
  expandedJobs: Record<string, ValuationJobDetailDto>
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
  const [searchTerms, setSearchTerms] = useState<Record<string, string>>({})

  if (runs.length === 0) {
    return (
      <div data-testid="job-history-empty" className="text-sm text-slate-400 py-4 text-center">
        No valuation jobs yet.
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
            <th className="py-2 pr-3" title="VaR (Value at Risk) estimates the maximum potential loss over a given time period at a specific confidence level. For example, a 95% VaR of $5,000 means there is a 95% chance that losses will not exceed $5,000.">VaR</th>
            <th className="py-2" title="ES (Expected Shortfall) estimates the average loss in the worst-case scenarios beyond the VaR (Value at Risk) threshold. For example, if VaR at 95% confidence is $5,000, the ES tells you the average loss you'd expect in that worst 5% of cases.">ES</th>
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
                          <h4 className="text-sm font-semibold text-slate-700">Job Details</h4>
                          <div className="flex items-center gap-3">
                            {detail && !isLoading && (
                              <div className="relative">
                                <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3 w-3 text-slate-400" />
                                <input
                                  data-testid={`detail-search-${run.jobId}`}
                                  type="text"
                                  value={searchTerms[run.jobId] ?? ''}
                                  onChange={(e) => setSearchTerms((prev) => ({ ...prev, [run.jobId]: e.target.value }))}
                                  placeholder="Search…"
                                  className="pl-7 pr-2 py-1 text-xs rounded border border-slate-200 bg-white focus:outline-none focus:border-primary-300 w-48"
                                  onClick={(e) => e.stopPropagation()}
                                />
                              </div>
                            )}
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
                        </div>
                        {isLoading && (
                          <div data-testid="detail-loading" className="flex items-center gap-2 text-sm text-slate-500 py-2">
                            <Spinner size="sm" />
                            Loading job details...
                          </div>
                        )}
                        {detail && !isLoading && (
                          <>
                            <JobTimeline steps={detail.steps} search={searchTerms[run.jobId] ?? ''} />
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
