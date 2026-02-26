import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchVaR, triggerVaRCalculation } from '../api/risk'
import type { VaRResultDto, TimeRange } from '../types'
import { resolveTimeRange } from '../utils/resolveTimeRange'

export interface VaRHistoryEntry {
  varValue: number
  expectedShortfall: number
  calculatedAt: string
}

export interface UseVaRResult {
  varResult: VaRResultDto | null
  history: VaRHistoryEntry[]
  filteredHistory: VaRHistoryEntry[]
  loading: boolean
  refreshing: boolean
  error: string | null
  refresh: () => Promise<void>
  timeRange: TimeRange
  setTimeRange: (range: TimeRange) => void
  zoomIn: (range: TimeRange) => void
  resetZoom: () => void
  zoomDepth: number
}

const POLL_INTERVAL = 30_000
const MAX_HISTORY = 60

function defaultTimeRange(): TimeRange {
  const now = new Date()
  const from = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  return { from: from.toISOString(), to: now.toISOString(), label: 'Last 24h' }
}

export function useVaR(portfolioId: string | null): UseVaRResult {
  const [varResult, setVarResult] = useState<VaRResultDto | null>(null)
  const [history, setHistory] = useState<VaRHistoryEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [timeRange, setTimeRangeInternal] = useState<TimeRange>(defaultTimeRange)
  const [zoomStack, setZoomStack] = useState<TimeRange[]>([])
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

  const refresh = useCallback(async () => {
    if (!portfolioId) return

    setRefreshing(true)
    setError(null)

    try {
      const result = await triggerVaRCalculation(portfolioId)
      setVarResult(result)

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
      setRefreshing(false)
    }
  }, [portfolioId])

  const filteredHistory = useMemo(() => {
    const { from, to } = resolveTimeRange(timeRange)
    const fromMs = new Date(from).getTime()
    const toMs = new Date(to).getTime()
    return history.filter((e) => {
      const t = new Date(e.calculatedAt).getTime()
      return t >= fromMs && t <= toMs
    })
  }, [history, timeRange])

  const setTimeRange = useCallback((range: TimeRange) => {
    setZoomStack([])
    setTimeRangeInternal(range)
  }, [])

  const zoomIn = useCallback((range: TimeRange) => {
    setZoomStack((prev) => [...prev, timeRange])
    setTimeRangeInternal(range)
  }, [timeRange])

  const resetZoom = useCallback(() => {
    if (zoomStack.length > 0) {
      setTimeRangeInternal(zoomStack[0])
      setZoomStack([])
    }
  }, [zoomStack])

  return { varResult, history, filteredHistory, loading, refreshing, error, refresh, timeRange, setTimeRange, zoomIn, resetZoom, zoomDepth: zoomStack.length }
}
