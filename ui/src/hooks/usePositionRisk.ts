import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchPositionRisk } from '../api/risk'
import type { PositionRiskDto } from '../types'

export interface UsePositionRiskResult {
  positionRisk: PositionRiskDto[]
  loading: boolean
  error: string | null
  refresh: () => Promise<void>
}

export function usePositionRisk(bookId: string | null, valuationDate: string | null = null): UsePositionRiskResult {
  const [positionRisk, setPositionRisk] = useState<PositionRiskDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const initialLoadDone = useRef(false)

  const load = useCallback(async () => {
    if (!bookId) return

    const isInitialLoad = !initialLoadDone.current
    if (isInitialLoad) {
      setLoading(true)
    }
    setError(null)

    try {
      const data = await fetchPositionRisk(bookId, valuationDate)
      setPositionRisk(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      if (isInitialLoad) {
        // First load for this book — clear any stale data from a previous book.
        setPositionRisk([])
      }
      // On subsequent refresh failures, preserve the last known data so the
      // user can still see the stale values while the error is displayed.
    } finally {
      setLoading(false)
      initialLoadDone.current = true
    }
  }, [bookId, valuationDate])

  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    if (!bookId) {
      setPositionRisk([])
      setLoading(false)
      setError(null)
      return
    }

    initialLoadDone.current = false
    loadRef.current()
  }, [bookId, valuationDate])

  const refresh = useCallback(async () => {
    await loadRef.current()
  }, [])

  return { positionRisk, loading, error, refresh }
}
