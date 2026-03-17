import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchPnlAttribution } from '../api/pnlAttribution'
import type { PnlAttributionDto } from '../types'

export interface UsePnlAttributionResult {
  data: PnlAttributionDto | null
  loading: boolean
  error: string | null
}

export function usePnlAttribution(
  bookId: string | null,
  date?: string,
): UsePnlAttributionResult {
  const [data, setData] = useState<PnlAttributionDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!bookId) return

    setLoading(true)
    setError(null)

    try {
      const result = await fetchPnlAttribution(bookId, date)
      setData(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [bookId, date])

  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    if (!bookId) {
      setData(null)
      setLoading(false)
      setError(null)
      return
    }

    loadRef.current()
  }, [bookId, date])

  return { data, loading, error }
}
