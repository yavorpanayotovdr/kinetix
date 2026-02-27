import { useEffect, useRef, useState } from 'react'
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
  refreshSignal?: number
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

export function JobHistory({ portfolioId, refreshSignal = 0 }: JobHistoryProps) {
  const [search, setSearch] = useState('')
  const { runs, expandedJobs, loadingJobIds, loading, error, timeRange, setTimeRange, toggleJob, closeJob, refresh, zoomIn, resetZoom, zoomDepth, page, pageSize, setPageSize, totalPages, hasNextPage, nextPage, prevPage, firstPage, lastPage, goToPage } = useJobHistory(
    portfolioId,
  )
  const [pageInput, setPageInput] = useState(String(page + 1))
  const [pageSizeInput, setPageSizeInput] = useState(String(pageSize))
  const [pageSizeOpen, setPageSizeOpen] = useState(false)
  const pageSizeRef = useRef<HTMLDivElement>(null)

  const prevSignalRef = useRef(refreshSignal)
  useEffect(() => {
    if (refreshSignal !== prevSignalRef.current) {
      prevSignalRef.current = refreshSignal
      refresh()
    }
  }, [refreshSignal, refresh])

  useEffect(() => {
    setPageInput(String(page + 1))
  }, [page])

  useEffect(() => {
    setPageSizeInput(String(pageSize))
  }, [pageSize])

  useEffect(() => {
    if (!pageSizeOpen) return
    const handleClickOutside = (e: MouseEvent) => {
      if (pageSizeRef.current && !pageSizeRef.current.contains(e.target as Node)) {
        setPageSizeOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [pageSizeOpen])

  const submitPageSizeInput = () => {
    const parsed = parseInt(pageSizeInput, 10)
    if (isNaN(parsed) || parsed < 1) {
      setPageSizeInput(String(pageSize))
      return
    }
    setPageSize(parsed)
  }

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
      <div
        data-testid="job-history-header"
        className="flex items-center gap-2 w-full text-left"
      >
        <History className="h-4 w-4 text-slate-500" />
        <span className="text-sm font-semibold text-slate-700">Valuation Jobs</span>
        {filteredRuns.length > 0 && (
          <Badge variant="neutral">{totalPages > 1 ? `Page ${page + 1} of ${totalPages}` : filteredRuns.length}</Badge>
        )}
      </div>

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
                  <span className="mx-1 border-l border-slate-200 h-4" />
                  <div data-testid="page-size-selector" className="inline-flex items-center gap-1 text-xs text-slate-500">
                    <span>Per page:</span>
                    <div ref={pageSizeRef} className="relative inline-flex">
                      <input
                        data-testid="page-size-input"
                        type="text"
                        value={pageSizeInput}
                        onChange={(e) => setPageSizeInput(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            submitPageSizeInput()
                            setPageSizeOpen(false)
                          }
                        }}
                        onBlur={submitPageSizeInput}
                        className="w-12 pl-1.5 pr-5 py-0.5 text-xs rounded border border-slate-200 bg-white text-slate-600 focus:outline-none focus:border-primary-300"
                      />
                      <button
                        data-testid="page-size-toggle"
                        type="button"
                        onClick={() => setPageSizeOpen((prev) => !prev)}
                        className="absolute right-0 top-0 bottom-0 flex items-center px-1 text-slate-400 hover:text-slate-600"
                      >
                        <ChevronDown className="h-3 w-3" />
                      </button>
                      {pageSizeOpen && (
                        <ul className="absolute left-full top-0 ml-0.5 flex items-center bg-white border border-slate-200 rounded shadow-sm z-10">
                          {[10, 20, 50].map((size) => (
                            <li key={size}>
                              <button
                                data-testid={`page-size-option-${size}`}
                                type="button"
                                onClick={() => {
                                  setPageSize(size)
                                  setPageSizeOpen(false)
                                }}
                                className={`px-2 py-0.5 text-xs whitespace-nowrap hover:bg-slate-50 ${pageSize === size ? 'font-semibold text-primary-600' : 'text-slate-600'}`}
                              >
                                {size}
                              </button>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
    </Card>
  )
}
