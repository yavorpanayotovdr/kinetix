import { useCallback, useEffect, useState } from 'react'
import { fetchTradeHistory } from '../api/tradeHistory'
import type { TradeHistoryDto } from '../types'

export interface UseTradeHistoryResult {
  trades: TradeHistoryDto[]
  loading: boolean
  error: string | null
  refetch: () => void
}

export function useTradeHistory(portfolioId: string | null): UseTradeHistoryResult {
  const [trades, setTrades] = useState<TradeHistoryDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!portfolioId) return
    setLoading(true)
    setError(null)
    try {
      const data = await fetchTradeHistory(portfolioId)
      setTrades(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [portfolioId])

  useEffect(() => {
    load()
  }, [load])

  return { trades, loading, error, refetch: load }
}
