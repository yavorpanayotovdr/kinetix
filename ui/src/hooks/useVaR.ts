import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchVaR } from '../api/risk'
import type { VaRResultDto } from '../types'

export interface VaRHistoryEntry {
  varValue: number
  expectedShortfall: number
  calculatedAt: string
}

export interface UseVaRResult {
  varResult: VaRResultDto | null
  history: VaRHistoryEntry[]
  loading: boolean
  error: string | null
  refresh: () => void
}

const POLL_INTERVAL = 30_000
const MAX_HISTORY = 60

export function useVaR(portfolioId: string | null): UseVaRResult {
  const [varResult, setVarResult] = useState<VaRResultDto | null>(null)
  const [history, setHistory] = useState<VaRHistoryEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const initialLoadDone = useRef(false)

  const load = useCallback(async () => {
    if (!portfolioId) return

    if (!initialLoadDone.current) {
      setLoading(true)
    }

    try {
      const result = await fetchVaR(portfolioId)
      setVarResult(result)
      setError(null)

      if (result) {
        setHistory((prev) => {
          if (prev.some((e) => e.calculatedAt === result.calculatedAt)) {
            return prev
          }
          const entry: VaRHistoryEntry = {
            varValue: Number(result.varValue),
            expectedShortfall: Number(result.expectedShortfall),
            calculatedAt: result.calculatedAt,
          }
          const next = [...prev, entry]
          return next.length > MAX_HISTORY ? next.slice(-MAX_HISTORY) : next
        })
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
      initialLoadDone.current = true
    }
  }, [portfolioId])

  useEffect(() => {
    if (!portfolioId) return

    initialLoadDone.current = false
    load()

    const interval = setInterval(load, POLL_INTERVAL)
    return () => clearInterval(interval)
  }, [portfolioId, load])

  return { varResult, history, loading, error, refresh: load }
}
