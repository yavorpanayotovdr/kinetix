import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchTradeHistory } from '../api/tradeHistory'
import type { TradeHistoryDto } from '../types'

export interface UseTradeHistoryResult {
  trades: TradeHistoryDto[]
  loading: boolean
  error: string | null
  refetch: () => void
}

export function useTradeHistory(bookId: string | null): UseTradeHistoryResult {
  const [trades, setTrades] = useState<TradeHistoryDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const initialLoadDone = useRef(false)

  const load = useCallback(async () => {
    if (!bookId) return
    if (!initialLoadDone.current) {
      setLoading(true)
    }
    setError(null)
    try {
      const data = await fetchTradeHistory(bookId)
      setTrades(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
      initialLoadDone.current = true
    }
  }, [bookId])

  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    initialLoadDone.current = false
    loadRef.current()
  }, [bookId])

  const refetch = useCallback(() => {
    loadRef.current()
  }, [])

  return { trades, loading, error, refetch }
}
