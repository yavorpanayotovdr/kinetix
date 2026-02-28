import { useCallback, useEffect, useState } from 'react'
import { fetchPnlAttribution } from '../api/pnlAttribution'
import type { PnlAttributionDto } from '../types'

export interface UsePnlAttributionResult {
  data: PnlAttributionDto | null
  loading: boolean
  error: string | null
}

export function usePnlAttribution(
  portfolioId: string | null,
  date?: string,
): UsePnlAttributionResult {
  const [data, setData] = useState<PnlAttributionDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!portfolioId) return

    setLoading(true)
    setError(null)

    try {
      const result = await fetchPnlAttribution(portfolioId, date)
      setData(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [portfolioId, date])

  useEffect(() => {
    if (!portfolioId) {
      setData(null)
      setLoading(false)
      setError(null)
      return
    }

    load()
  }, [portfolioId, load])

  return { data, loading, error }
}
