import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchValuationJobs, fetchValuationJobDetail, fetchChartData, type ChartDataResponse } from '../api/jobHistory'
import type { ValuationJobSummaryDto, ValuationJobDetailDto, TimeRange } from '../types'

function defaultTimeRange(): TimeRange {
  const now = new Date()
  const from = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  return { from: from.toISOString(), to: now.toISOString(), label: 'Last 24h' }
}

const SLIDING_DURATIONS: Record<string, number> = {
  'Last 1h': 60 * 60 * 1000,
  'Last 24h': 24 * 60 * 60 * 1000,
  'Last 7d': 7 * 24 * 60 * 60 * 1000,
}

function resolveQueryRange(range: TimeRange): { from: string; to: string } {
  const duration = SLIDING_DURATIONS[range.label]
  if (duration) {
    const now = new Date()
    return { from: new Date(now.getTime() - duration).toISOString(), to: now.toISOString() }
  }
  if (range.label === 'Today') {
    const now = new Date()
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    return { from: start.toISOString(), to: now.toISOString() }
  }
  return { from: range.from, to: range.to }
}

export interface UseJobHistoryResult {
  runs: ValuationJobSummaryDto[]
  chartData: ChartDataResponse | null
  expandedJobs: Record<string, ValuationJobDetailDto>
  loadingJobIds: Set<string>
  loading: boolean
  error: string | null
  timeRange: TimeRange
  setTimeRange: (range: TimeRange) => void
  toggleJob: (jobId: string) => void
  closeJob: (jobId: string) => void
  clearSelection: () => void
  refresh: () => void
  zoomIn: (range: TimeRange) => void
  resetZoom: () => void
  zoomDepth: number
  page: number
  pageSize: number
  setPageSize: (size: number) => void
  totalCount: number
  totalPages: number
  hasNextPage: boolean
  nextPage: () => void
  prevPage: () => void
  firstPage: () => void
  lastPage: () => void
  goToPage: (target: number) => void
}

const POLL_INTERVAL = 5_000
const DEFAULT_PAGE_SIZE = 10

export function useJobHistory(bookId: string | null): UseJobHistoryResult {
  const [runs, setRuns] = useState<ValuationJobSummaryDto[]>([])
  const [chartData, setChartData] = useState<ChartDataResponse | null>(null)
  const [expandedJobs, setExpandedJobs] = useState<Record<string, ValuationJobDetailDto>>({})
  const [loadingJobIds, setLoadingJobIds] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [timeRange, setTimeRangeInternal] = useState<TimeRange>(defaultTimeRange)
  const [zoomStack, setZoomStack] = useState<TimeRange[]>([])
  const [fetchVersion, setFetchVersion] = useState(0)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSizeInternal] = useState(DEFAULT_PAGE_SIZE)
  const [totalCount, setTotalCount] = useState(0)

  const timeRangeRef = useRef(timeRange)
  timeRangeRef.current = timeRange

  const pageRef = useRef(page)
  pageRef.current = page

  const pageSizeRef = useRef(pageSize)
  pageSizeRef.current = pageSize

  const initialLoadDone = useRef(false)

  // Pin `from` when a time range is established so that items don't shuffle
  // out of view on each poll tick. Only `to` advances for sliding ranges.
  const pinnedFromRef = useRef('')
  const isSlidingRef = useRef(false)

  const load = useCallback(async () => {
    if (!bookId) return

    if (!initialLoadDone.current) {
      setLoading(true)
    }
    setError(null)

    try {
      const from = pinnedFromRef.current
      const to = isSlidingRef.current ? new Date().toISOString() : resolveQueryRange(timeRangeRef.current).to
      const { items, totalCount: count } = await fetchValuationJobs(bookId, pageSizeRef.current, pageRef.current * pageSizeRef.current, from, to)
      setTotalCount(count)
      setRuns((prev) => {
        if (
          items.length === prev.length &&
          items.every((item, i) => item.jobId === prev[i].jobId && item.status === prev[i].status)
        ) {
          return prev
        }
        return items
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
      initialLoadDone.current = true
    }
  }, [bookId])

  const loadChart = useCallback(async () => {
    if (!bookId) return

    try {
      const { from, to } = resolveQueryRange(timeRangeRef.current)
      const response = await fetchChartData(bookId, from, to)
      setChartData(response)
    } catch {
      // Chart fetch failure is non-critical; table data is still available
    }
  }, [bookId])

  const loadRef = useRef(load)
  loadRef.current = load

  const loadChartRef = useRef(loadChart)
  loadChartRef.current = loadChart

  useEffect(() => {
    if (!bookId) {
      setRuns([])
      setChartData(null)
      setExpandedJobs({})
      setLoadingJobIds(new Set())
      return
    }

    // Resolve and pin the query range so polls use a stable `from`.
    const range = timeRangeRef.current
    const { from } = resolveQueryRange(range)
    pinnedFromRef.current = from
    isSlidingRef.current = !!(SLIDING_DURATIONS[range.label] || range.label === 'Today')

    initialLoadDone.current = false
    loadRef.current()

    const interval = setInterval(() => loadRef.current(), POLL_INTERVAL)
    return () => clearInterval(interval)
  }, [bookId, fetchVersion])

  // Refetch when page or pageSize changes — without resetting initialLoadDone
  const paginationMounted = useRef(false)
  useEffect(() => {
    if (!paginationMounted.current) {
      paginationMounted.current = true
      return
    }
    if (!bookId) return
    loadRef.current()
  }, [page, pageSize, bookId])

  useEffect(() => {
    if (bookId) loadChartRef.current()
  }, [bookId, fetchVersion])

  // Auto-refresh expanded detail panels for RUNNING jobs on each poll tick
  const runsRef = useRef(runs)
  runsRef.current = runs
  const expandedJobsRef = useRef(expandedJobs)
  expandedJobsRef.current = expandedJobs

  useEffect(() => {
    if (!bookId) return
    const refreshRunningDetails = async () => {
      const currentRuns = runsRef.current
      const currentExpanded = expandedJobsRef.current
      const runningExpandedIds = currentRuns
        .filter((r) => r.status === 'RUNNING' && r.jobId in currentExpanded)
        .map((r) => r.jobId)
      for (const jobId of runningExpandedIds) {
        try {
          const detail = await fetchValuationJobDetail(jobId)
          if (detail) {
            setExpandedJobs((prev) => ({ ...prev, [jobId]: detail }))
          }
        } catch {
          // Non-critical — table data still available
        }
      }
    }
    const interval = setInterval(refreshRunningDetails, POLL_INTERVAL)
    return () => clearInterval(interval)
  }, [bookId])

  const toggleJob = useCallback(async (jobId: string) => {
    if (jobId in expandedJobs) {
      setExpandedJobs((prev) => {
        const next = { ...prev }
        delete next[jobId]
        return next
      })
      return
    }

    setLoadingJobIds((prev) => new Set(prev).add(jobId))

    try {
      const detail = await fetchValuationJobDetail(jobId)
      if (detail) {
        setExpandedJobs((prev) => ({ ...prev, [jobId]: detail }))
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoadingJobIds((prev) => {
        const next = new Set(prev)
        next.delete(jobId)
        return next
      })
    }
  }, [expandedJobs])

  const closeJob = useCallback((jobId: string) => {
    setExpandedJobs((prev) => {
      const next = { ...prev }
      delete next[jobId]
      return next
    })
  }, [])

  const clearSelection = useCallback(() => {
    setExpandedJobs({})
    setLoadingJobIds(new Set())
  }, [])

  const setTimeRange = useCallback((range: TimeRange) => {
    setZoomStack([])
    setPage(0)
    setTimeRangeInternal(range)
    setFetchVersion((v) => v + 1)
  }, [])

  const zoomIn = useCallback((range: TimeRange) => {
    setZoomStack((prev) => [...prev, timeRange])
    setPage(0)
    setTimeRangeInternal(range)
    setFetchVersion((v) => v + 1)
  }, [timeRange])

  const resetZoom = useCallback(() => {
    if (zoomStack.length > 0) {
      setTimeRangeInternal(zoomStack[0])
      setZoomStack([])
      setFetchVersion((v) => v + 1)
    }
  }, [zoomStack])

  const totalPages = Math.ceil(totalCount / pageSize)
  const hasNextPage = (page + 1) * pageSize < totalCount

  const nextPage = useCallback(() => {
    if (!hasNextPage) return
    setExpandedJobs({})
    setLoadingJobIds(new Set())
    setPage((p) => p + 1)
  }, [hasNextPage])

  const prevPage = useCallback(() => {
    if (page === 0) return
    setExpandedJobs({})
    setLoadingJobIds(new Set())
    setPage((p) => p - 1)
  }, [page])

  const firstPage = useCallback(() => {
    if (page === 0) return
    setExpandedJobs({})
    setLoadingJobIds(new Set())
    setPage(0)
  }, [page])

  const lastPage = useCallback(() => {
    if (!hasNextPage) return
    setExpandedJobs({})
    setLoadingJobIds(new Set())
    setPage(totalPages - 1)
  }, [hasNextPage, totalPages])

  const goToPage = useCallback((target: number) => {
    const clamped = Math.max(0, Math.min(target, totalPages - 1))
    setExpandedJobs({})
    setLoadingJobIds(new Set())
    setPage(clamped)
  }, [totalPages])

  const setPageSize = useCallback((size: number) => {
    setExpandedJobs({})
    setLoadingJobIds(new Set())
    setPage(0)
    setPageSizeInternal(size)
  }, [])

  const refresh = useCallback(() => {
    loadRef.current()
  }, [])

  return { runs, chartData, expandedJobs, loadingJobIds, loading, error, timeRange, setTimeRange, toggleJob, closeJob, clearSelection, refresh, zoomIn, resetZoom, zoomDepth: zoomStack.length, page, pageSize, setPageSize, totalCount, totalPages, hasNextPage, nextPage, prevPage, firstPage, lastPage, goToPage }
}
