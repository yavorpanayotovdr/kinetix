import { useCallback, useEffect, useState } from 'react'
import { fetchValuationJobs, fetchValuationJobDetail } from '../api/jobHistory'
import type { ValuationJobSummaryDto, ValuationJobDetailDto, TimeRange } from '../types'

function defaultTimeRange(): TimeRange {
  const now = new Date()
  const from = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  return { from: from.toISOString(), to: now.toISOString(), label: 'Last 24h' }
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
}

const POLL_INTERVAL = 5_000

export function useJobHistory(portfolioId: string | null): UseJobHistoryResult {
  const [runs, setRuns] = useState<ValuationJobSummaryDto[]>([])
  const [expandedJobs, setExpandedJobs] = useState<Record<string, ValuationJobDetailDto>>({})
  const [loadingJobIds, setLoadingJobIds] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [timeRange, setTimeRangeInternal] = useState<TimeRange>(defaultTimeRange)
  const [zoomStack, setZoomStack] = useState<TimeRange[]>([])

  const load = useCallback(async () => {
    if (!portfolioId) return

    setLoading(true)
    setError(null)

    try {
      const result = await fetchValuationJobs(portfolioId, 200, 0, timeRange.from, timeRange.to)
      setRuns(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [portfolioId, timeRange])

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
  }, [portfolioId, load])

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
    setTimeRangeInternal(range)
  }, [])

  const zoomIn = useCallback((range: TimeRange) => {
    setZoomStack((prev) => [...prev, timeRange])
    setTimeRangeInternal(range)
  }, [timeRange])

  const resetZoom = useCallback(() => {
    if (zoomStack.length > 0) {
      setTimeRangeInternal(zoomStack[0])
      setZoomStack([])
    }
  }, [zoomStack])

  const refresh = useCallback(() => {
    load()
  }, [load])

  return { runs, expandedJobs, loadingJobIds, loading, error, timeRange, setTimeRange, toggleJob, closeJob, clearSelection, refresh, zoomIn, resetZoom, zoomDepth: zoomStack.length }
}
