import { useCallback, useEffect, useState } from 'react'
import { fetchCalculationRuns, fetchCalculationRunDetail } from '../api/runHistory'
import type { CalculationRunSummaryDto, CalculationRunDetailDto } from '../types'

export interface UseRunHistoryResult {
  runs: CalculationRunSummaryDto[]
  selectedRun: CalculationRunDetailDto | null
  loading: boolean
  error: string | null
  selectRun: (runId: string) => void
  clearSelection: () => void
  refresh: () => void
}

export function useRunHistory(portfolioId: string | null): UseRunHistoryResult {
  const [runs, setRuns] = useState<CalculationRunSummaryDto[]>([])
  const [selectedRun, setSelectedRun] = useState<CalculationRunDetailDto | null>(null)
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
      setSelectedRun(null)
      return
    }
    load()
  }, [portfolioId, load])

  const selectRun = useCallback(async (runId: string) => {
    setLoading(true)
    setError(null)

    try {
      const detail = await fetchCalculationRunDetail(runId)
      setSelectedRun(detail)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [])

  const clearSelection = useCallback(() => {
    setSelectedRun(null)
  }, [])

  const refresh = useCallback(() => {
    load()
  }, [load])

  return { runs, selectedRun, loading, error, selectRun, clearSelection, refresh }
}
