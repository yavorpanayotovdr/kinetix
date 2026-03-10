import { useCallback, useEffect, useRef, useState } from 'react'
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

  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    loadRef.current()
  }, [portfolioId])

  const refetch = useCallback(() => {
    loadRef.current()
  }, [])

  return { trades, loading, error, refetch }
}
