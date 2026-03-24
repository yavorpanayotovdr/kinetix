import { useCallback, useEffect, useRef, useState } from 'react'
import type { FactorRiskDto } from '../types'
import { fetchFactorRiskHistory } from '../api/factorRisk'

interface UseFactorRiskHistoryResult {
  history: FactorRiskDto[]
  loading: boolean
  error: string | null
}

export function useFactorRiskHistory(
  bookId: string | null,
  limit: number = 30,
): UseFactorRiskHistoryResult {
  const [history, setHistory] = useState<FactorRiskDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!bookId) return

    setLoading(true)
    setError(null)

    try {
      const data = await fetchFactorRiskHistory(bookId, limit)
      setHistory(data)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Failed to load factor risk history',
      )
    } finally {
      setLoading(false)
    }
  }, [bookId, limit])

  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    if (!bookId) {
      setHistory([])
      return
    }
    loadRef.current()
  }, [bookId, limit])

  return { history, loading, error }
}
