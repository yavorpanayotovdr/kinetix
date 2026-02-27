import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchValuationJobs, fetchValuationJobDetail } from '../api/jobHistory'
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

export function useJobHistory(portfolioId: string | null): UseJobHistoryResult {
  const [runs, setRuns] = useState<ValuationJobSummaryDto[]>([])
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

  const load = useCallback(async () => {
    if (!portfolioId) return

    setLoading(true)
    setError(null)

    try {
      const { from, to } = resolveQueryRange(timeRangeRef.current)
      const { items, totalCount: count } = await fetchValuationJobs(portfolioId, pageSizeRef.current, pageRef.current * pageSizeRef.current, from, to)
      setTotalCount(count)
      setRuns(items)
      setTimeRangeInternal((prev) => {
        if (prev.from === from && prev.to === to) return prev
        return { ...prev, from, to }
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [portfolioId])

  useEffect(() => {
    if (!portfolioId) {
      setRuns([])
      setExpandedJobs({})
      setLoadingJobIds(new Set())
      return
    }
    load()

    const interval = setInterval(load, POLL_INTERVAL)
    return () => clearInterval(interval)
  }, [portfolioId, load, fetchVersion, page, pageSize])

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
    load()
  }, [load])

  return { runs, expandedJobs, loadingJobIds, loading, error, timeRange, setTimeRange, toggleJob, closeJob, clearSelection, refresh, zoomIn, resetZoom, zoomDepth: zoomStack.length, page, pageSize, setPageSize, totalCount, totalPages, hasNextPage, nextPage, prevPage, firstPage, lastPage, goToPage }
}
