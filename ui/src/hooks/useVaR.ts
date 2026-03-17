import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchVaR, triggerVaRCalculation } from '../api/risk'
import { fetchChartData } from '../api/jobHistory'
import type { VaRResultDto, GreeksResultDto, TimeRange } from '../types'
import { resolveTimeRange } from '../utils/resolveTimeRange'

export interface VaRHistoryEntry {
  varValue: number
  expectedShortfall: number
  calculatedAt: string
  confidenceLevel: string
  delta?: number
  gamma?: number
  vega?: number
  theta?: number
}

export interface UseVaRResult {
  varResult: VaRResultDto | null
  greeksResult: GreeksResultDto | null
  history: VaRHistoryEntry[]
  filteredHistory: VaRHistoryEntry[]
  loading: boolean
  historyLoading: boolean
  refreshing: boolean
  error: string | null
  refresh: () => Promise<void>
  timeRange: TimeRange
  setTimeRange: (range: TimeRange) => void
  selectedConfidenceLevel: string
  setSelectedConfidenceLevel: (level: string) => void
  zoomIn: (range: TimeRange) => void
  resetZoom: () => void
  zoomDepth: number
  isLive: boolean
}

const POLL_INTERVAL = 30_000

function aggregateGreeks(greeks: GreeksResultDto | undefined): { delta: number; gamma: number; vega: number; theta: number } | undefined {
  if (!greeks) return undefined
  let delta = 0
  let gamma = 0
  let vega = 0
  for (const ac of greeks.assetClassGreeks) {
    delta += Number(ac.delta)
    gamma += Number(ac.gamma)
    vega += Number(ac.vega)
  }
  const theta = Number(greeks.theta)
  return { delta, gamma, vega, theta }
}

function defaultTimeRange(): TimeRange {
  const now = new Date()
  const from = new Date(now.getTime() - 24 * 60 * 60 * 1000)
  return { from: from.toISOString(), to: now.toISOString(), label: 'Last 24h' }
}

export function useVaR(bookId: string | null, valuationDate: string | null = null): UseVaRResult {
  const isLive = valuationDate === null
  const [varResult, setVarResult] = useState<VaRResultDto | null>(null)
  const [history, setHistory] = useState<VaRHistoryEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(!!bookId)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [timeRange, setTimeRangeInternal] = useState<TimeRange>(defaultTimeRange)
  const [zoomStack, setZoomStack] = useState<TimeRange[]>([])
  const [fetchVersion, setFetchVersion] = useState(0)
  const [selectedConfidenceLevel, setSelectedConfidenceLevelInternal] = useState('CL_95')
  const initialLoadDone = useRef(false)
  const isPolling = useRef(false)
  const timeRangeRef = useRef(timeRange)
  timeRangeRef.current = timeRange

  const loadHistory = useCallback(async () => {
    if (!bookId) {
      setHistoryLoading(false)
      return
    }
    setHistoryLoading(true)

    try {
      const { from, to } = resolveTimeRange(timeRangeRef.current)
      const response = await fetchChartData(bookId, from, to)
      const historical = response.points
        .filter((p) => p.varValue != null)
        .map((p) => ({
          varValue: p.varValue!,
          expectedShortfall: p.expectedShortfall ?? 0,
          calculatedAt: p.bucket,
          confidenceLevel: p.confidenceLevel ?? 'CL_95',
          ...(p.delta != null && p.gamma != null && p.vega != null
            ? { delta: p.delta, gamma: p.gamma, vega: p.vega, ...(p.theta != null ? { theta: p.theta } : {}) }
            : {}),
        }))
        .sort((a, b) => new Date(a.calculatedAt).getTime() - new Date(b.calculatedAt).getTime())

      setHistory((prev) => {
        if (historical.length === 0) return prev
        const historicalMap = new Map(historical.map((e) => [e.calculatedAt, e]))
        const merged = prev.map((e) => {
          const h = historicalMap.get(e.calculatedAt)
          if (!h) return e
          historicalMap.delete(e.calculatedAt)
          // Preserve the entry that has Greeks
          if (e.delta !== undefined && h.delta === undefined) return e
          return h
        })
        return [...merged, ...historicalMap.values()].sort(
          (a, b) => new Date(a.calculatedAt).getTime() - new Date(b.calculatedAt).getTime(),
        )
      })
    } catch {
      // History fetch failure is non-critical; polling continues
    } finally {
      setHistoryLoading(false)
    }
  }, [bookId])

  const load = useCallback(async () => {
    if (!bookId) return
    if (isPolling.current) return
    isPolling.current = true

    if (!initialLoadDone.current) {
      setLoading(true)
    }

    try {
      const result = await fetchVaR(bookId, valuationDate)
      setVarResult((prev) => {
        if (prev && result && prev.calculatedAt === result.calculatedAt && prev.varValue === result.varValue) {
          return prev
        }
        return result
      })
      setError(null)

      if (result && isLive) {
        setHistory((prev) => {
          if (prev.some((e) => e.calculatedAt === result.calculatedAt)) {
            return prev
          }
          const greeks = aggregateGreeks(result.greeks)
          const entry: VaRHistoryEntry = {
            varValue: Number(result.varValue),
            expectedShortfall: Number(result.expectedShortfall),
            calculatedAt: result.calculatedAt,
            confidenceLevel: result.confidenceLevel,
            ...greeks,
          }
          return [...prev, entry]
        })
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
      initialLoadDone.current = true
      isPolling.current = false
    }
  }, [bookId, valuationDate, isLive])

  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    if (!bookId) return

    initialLoadDone.current = false
    loadRef.current()

    if (!isLive) return // Historical mode: single fetch, no polling

    const interval = setInterval(() => loadRef.current(), POLL_INTERVAL)
    return () => clearInterval(interval)
  }, [bookId, isLive])

  const loadHistoryRef = useRef(loadHistory)
  loadHistoryRef.current = loadHistory

  useEffect(() => {
    if (!bookId) return

    loadHistoryRef.current()

    if (!isLive) return

    // Re-fetch chart data periodically so sliding time ranges stay in sync
    // with the filteredHistory window that re-resolves from Date.now()
    const interval = setInterval(() => loadHistoryRef.current(), POLL_INTERVAL)
    return () => clearInterval(interval)
  }, [bookId, fetchVersion, isLive])

  const refresh = useCallback(async () => {
    if (!bookId) return

    setRefreshing(true)
    setError(null)

    try {
      const result = await triggerVaRCalculation(bookId, { confidenceLevel: selectedConfidenceLevel })
      setVarResult(result)

      if (result) {
        setHistory((prev) => {
          if (prev.some((e) => e.calculatedAt === result.calculatedAt)) {
            return prev
          }
          const greeks = aggregateGreeks(result.greeks)
          const entry: VaRHistoryEntry = {
            varValue: Number(result.varValue),
            expectedShortfall: Number(result.expectedShortfall),
            calculatedAt: result.calculatedAt,
            confidenceLevel: result.confidenceLevel,
            ...greeks,
          }
          return [...prev, entry]
        })
      }
    } catch (err: unknown) {
      if (err instanceof Error && (err as Error & { status: number }).status === 503) {
        await new Promise(resolve => setTimeout(resolve, 5000))
        try {
          const retryResult = await triggerVaRCalculation(bookId, { confidenceLevel: selectedConfidenceLevel })
          setVarResult(retryResult)

          if (retryResult) {
            setHistory((prev) => {
              if (prev.some((e) => e.calculatedAt === retryResult.calculatedAt)) {
                return prev
              }
              const greeks = aggregateGreeks(retryResult.greeks)
              const entry: VaRHistoryEntry = {
                varValue: Number(retryResult.varValue),
                expectedShortfall: Number(retryResult.expectedShortfall),
                calculatedAt: retryResult.calculatedAt,
                confidenceLevel: retryResult.confidenceLevel,
                ...greeks,
              }
              return [...prev, entry]
            })
          }
          return
        } catch (retryErr: unknown) {
          setError(retryErr instanceof Error ? retryErr.message : 'VaR calculation failed')
        }
      } else {
        setError(err instanceof Error ? err.message : String(err))
      }
    } finally {
      setRefreshing(false)
    }
  }, [bookId, selectedConfidenceLevel])

  const filteredHistory = useMemo(() => {
    const { from, to } = resolveTimeRange(timeRange)
    const fromMs = new Date(from).getTime()
    const toMs = new Date(to).getTime()
    return history.filter((e) => {
      const t = new Date(e.calculatedAt).getTime()
      return t >= fromMs && t <= toMs && e.confidenceLevel === selectedConfidenceLevel
    })
  }, [history, timeRange, selectedConfidenceLevel])

  const setSelectedConfidenceLevel = useCallback((level: string) => {
    setSelectedConfidenceLevelInternal(level)
    setZoomStack([])
  }, [])

  const setTimeRange = useCallback((range: TimeRange) => {
    setZoomStack([])
    setTimeRangeInternal(range)
    setFetchVersion((v) => v + 1)
  }, [])

  const zoomIn = useCallback((range: TimeRange) => {
    setZoomStack((prev) => [...prev, timeRange])
    setTimeRangeInternal(range)
    setFetchVersion((v) => v + 1)
  }, [timeRange])

  const resetZoom = useCallback(() => {
    if (zoomStack.length > 0) {
      setTimeRangeInternal(zoomStack[0])
      setZoomStack([])
      setFetchVersion((v) => v + 1)
    }
  }, [zoomStack])

  const greeksResult = varResult?.greeks ?? null

  return { varResult, greeksResult, history, filteredHistory, loading, historyLoading, refreshing, error, refresh, timeRange, setTimeRange, selectedConfidenceLevel, setSelectedConfidenceLevel, zoomIn, resetZoom, zoomDepth: zoomStack.length, isLive }
}
