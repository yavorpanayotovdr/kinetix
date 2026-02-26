import { useEffect, useState } from 'react'
import { ChevronDown, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, History, Search } from 'lucide-react'
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
  const { runs, expandedJobs, loadingJobIds, loading, error, timeRange, setTimeRange, toggleJob, closeJob, zoomIn, resetZoom, zoomDepth, page, totalPages, hasNextPage, nextPage, prevPage, firstPage, lastPage, goToPage } = useJobHistory(
    expanded ? portfolioId : null,
  )
  const [pageInput, setPageInput] = useState(String(page + 1))

  useEffect(() => {
    setPageInput(String(page + 1))
  }, [page])

  const submitPageInput = () => {
    const parsed = parseInt(pageInput, 10)
    if (isNaN(parsed)) {
      setPageInput(String(page + 1))
      return
    }
    const clamped = Math.max(1, Math.min(parsed, totalPages))
    setPageInput(String(clamped))
    goToPage(clamped - 1)
  }

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
          <Badge variant="neutral">{totalPages > 1 ? `Page ${page + 1} of ${totalPages}` : filteredRuns.length}</Badge>
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
              {filteredRuns.length > 0 && (
                <div data-testid="pagination-bar" className="flex items-center justify-center gap-2 mt-2 py-2">
                  <button
                    data-testid="pagination-first"
                    onClick={firstPage}
                    disabled={page === 0}
                    className="inline-flex items-center px-1.5 py-1 text-xs font-medium rounded border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <ChevronsLeft className="h-3 w-3" />
                  </button>
                  <button
                    data-testid="pagination-prev"
                    onClick={prevPage}
                    disabled={page === 0}
                    className="inline-flex items-center px-1.5 py-1 text-xs font-medium rounded border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <ChevronLeft className="h-3 w-3" />
                  </button>
                  <span data-testid="pagination-info" className="text-xs text-slate-500 mx-1 inline-flex items-center gap-1">
                    {totalPages > 1 ? (
                      <>
                        <input
                          data-testid="pagination-page-input"
                          type="text"
                          value={pageInput}
                          onChange={(e) => setPageInput(e.target.value)}
                          onKeyDown={(e) => { if (e.key === 'Enter') submitPageInput() }}
                          onBlur={submitPageInput}
                          className="w-8 px-1 py-0.5 text-xs text-center rounded border border-slate-200 bg-white focus:outline-none focus:border-primary-300"
                        />
                        <span>of {totalPages}</span>
                      </>
                    ) : (
                      `Page ${page + 1}`
                    )}
                  </span>
                  <button
                    data-testid="pagination-next"
                    onClick={nextPage}
                    disabled={!hasNextPage}
                    className="inline-flex items-center px-1.5 py-1 text-xs font-medium rounded border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <ChevronRight className="h-3 w-3" />
                  </button>
                  <button
                    data-testid="pagination-last"
                    onClick={lastPage}
                    disabled={!hasNextPage}
                    className="inline-flex items-center px-1.5 py-1 text-xs font-medium rounded border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    <ChevronsRight className="h-3 w-3" />
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </Card>
  )
}
