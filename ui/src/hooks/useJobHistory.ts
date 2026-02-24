import { useCallback, useEffect, useState } from 'react'
import { fetchCalculationJobs, fetchCalculationJobDetail } from '../api/jobHistory'
import type { CalculationJobSummaryDto, CalculationJobDetailDto } from '../types'

export interface UseJobHistoryResult {
  runs: CalculationJobSummaryDto[]
  expandedJobs: Record<string, CalculationJobDetailDto>
  loadingJobIds: Set<string>
  loading: boolean
  error: string | null
  toggleJob: (jobId: string) => void
  closeJob: (jobId: string) => void
  clearSelection: () => void
  refresh: () => void
}

export function useJobHistory(portfolioId: string | null): UseJobHistoryResult {
  const [runs, setRuns] = useState<CalculationJobSummaryDto[]>([])
  const [expandedJobs, setExpandedJobs] = useState<Record<string, CalculationJobDetailDto>>({})
  const [loadingJobIds, setLoadingJobIds] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!portfolioId) return

    setLoading(true)
    setError(null)

    try {
      const result = await fetchCalculationJobs(portfolioId)
      setRuns(result)
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
      const detail = await fetchCalculationJobDetail(jobId)
      setExpandedJobs((prev) => ({ ...prev, [jobId]: detail }))
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

  const refresh = useCallback(() => {
    load()
  }, [load])

  return { runs, expandedJobs, loadingJobIds, loading, error, toggleJob, closeJob, clearSelection, refresh }
}
