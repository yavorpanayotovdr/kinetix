import { useCallback, useEffect, useState } from 'react'
import { fetchCalculationRuns, fetchCalculationRunDetail } from '../api/runHistory'
import type { CalculationRunSummaryDto, CalculationRunDetailDto } from '../types'

export interface UseRunHistoryResult {
  runs: CalculationRunSummaryDto[]
  selectedRunId: string | null
  selectedRun: CalculationRunDetailDto | null
  detailLoading: boolean
  loading: boolean
  error: string | null
  selectRun: (runId: string) => void
  clearSelection: () => void
  refresh: () => void
}

export function useRunHistory(portfolioId: string | null): UseRunHistoryResult {
  const [runs, setRuns] = useState<CalculationRunSummaryDto[]>([])
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null)
  const [selectedRun, setSelectedRun] = useState<CalculationRunDetailDto | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!portfolioId) return

    setLoading(true)
    setError(null)

    try {
      const result = await fetchCalculationRuns(portfolioId)
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
      setSelectedRunId(null)
      setSelectedRun(null)
      return
    }
    load()
  }, [portfolioId, load])

  const selectRun = useCallback(async (runId: string) => {
    if (runId === selectedRunId) {
      setSelectedRunId(null)
      setSelectedRun(null)
      return
    }

    setSelectedRunId(runId)
    setSelectedRun(null)
    setDetailLoading(true)

    try {
      const detail = await fetchCalculationRunDetail(runId)
      setSelectedRun(detail)
    } catch (err) {
      setSelectedRunId(null)
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setDetailLoading(false)
    }
  }, [selectedRunId])

  const clearSelection = useCallback(() => {
    setSelectedRunId(null)
    setSelectedRun(null)
  }, [])

  const refresh = useCallback(() => {
    load()
  }, [load])

  return { runs, selectedRunId, selectedRun, detailLoading, loading, error, selectRun, clearSelection, refresh }
}
