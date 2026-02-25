import { useState } from 'react'
import { ChevronDown, ChevronRight, History, Search } from 'lucide-react'
import { useJobHistory } from '../hooks/useJobHistory'
import { useTimeBuckets } from '../hooks/useTimeBuckets'
import { JobHistoryTable } from './JobHistoryTable'
import { JobTimechart } from './JobTimechart'
import { TimeRangeSelector } from './TimeRangeSelector'
import { Card, Badge, Spinner } from './ui'
import type { ValuationJobSummaryDto, ValuationJobDetailDto } from '../types'

interface JobHistoryProps {
  portfolioId: string | null
}

function buildSearchableText(
  run: ValuationJobSummaryDto,
  detail: ValuationJobDetailDto | undefined,
): string {
  const parts = [
    run.jobId,
    run.triggerType,
    run.status,
    run.calculationType,
    run.varValue?.toString(),
    run.expectedShortfall?.toString(),
    run.durationMs?.toString(),
  ]

  if (detail) {
    for (const step of detail.steps) {
      parts.push(...Object.values(step.details))
      if (step.error) parts.push(step.error)
    }
    if (detail.error) parts.push(detail.error)
  }

  return parts.filter(Boolean).join(' ').toLowerCase()
}

function jobMatchesSearch(
  run: ValuationJobSummaryDto,
  term: string,
  detail: ValuationJobDetailDto | undefined,
): boolean {
  const tokens = term.toLowerCase().split(/\s+/).filter(Boolean)
  const text = buildSearchableText(run, detail)
  return tokens.every((t) => text.includes(t))
}

export function JobHistory({ portfolioId }: JobHistoryProps) {
  const [expanded, setExpanded] = useState(true)
  const [search, setSearch] = useState('')
  const { runs, expandedJobs, loadingJobIds, loading, error, timeRange, setTimeRange, toggleJob, closeJob, zoomIn, resetZoom, zoomDepth } = useJobHistory(
    expanded ? portfolioId : null,
  )
  const buckets = useTimeBuckets(runs, timeRange)

  const filteredRuns = search.trim()
    ? runs.filter((r) => jobMatchesSearch(r, search, expandedJobs[r.jobId]))
    : runs

  return (
    <Card data-testid="job-history">
      <button
        data-testid="job-history-toggle"
        onClick={() => setExpanded((prev) => !prev)}
        className="flex items-center gap-2 w-full text-left"
      >
        {expanded ? <ChevronDown className="h-4 w-4 text-slate-500" /> : <ChevronRight className="h-4 w-4 text-slate-500" />}
        <History className="h-4 w-4 text-slate-500" />
        <span className="text-sm font-semibold text-slate-700">Valuation Jobs</span>
        {expanded && filteredRuns.length > 0 && (
          <Badge variant="neutral">{filteredRuns.length}</Badge>
        )}
      </button>

      {expanded && (
        <div className="mt-3">
          {loading && !runs.length && (
            <div data-testid="job-history-loading" className="flex items-center gap-2 text-sm text-slate-500 py-2">
              <Spinner size="sm" />
              Loading jobs...
            </div>
          )}

          {error && (
            <p data-testid="job-history-error" className="text-sm text-red-600 py-2">{error}</p>
          )}

          {!error && !(loading && runs.length === 0) && (
            <>
              <TimeRangeSelector value={timeRange} onChange={setTimeRange} />
              {runs.length > 0 && (
                <JobTimechart
                  buckets={buckets}
                  timeRange={timeRange}
                  onZoom={zoomIn}
                  zoomDepth={zoomDepth}
                  onResetZoom={resetZoom}
                />
              )}
              {runs.length > 0 && (
                <div className="mb-2">
                  <div className="relative inline-block">
                    <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3 w-3 text-slate-400" />
                    <input
                      data-testid="job-history-search"
                      type="text"
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder="Search…"
                      className="pl-7 pr-2 py-1 text-xs rounded border border-slate-200 bg-white focus:outline-none focus:border-primary-300 w-48"
                      onClick={(e) => e.stopPropagation()}
                    />
                  </div>
                </div>
              )}
              <JobHistoryTable
                runs={filteredRuns}
                expandedJobs={expandedJobs}
                loadingJobIds={loadingJobIds}
                onSelectJob={toggleJob}
                onCloseJob={closeJob}
              />
            </>
          )}
        </div>
      )}
    </Card>
  )
}
