import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchVaR, triggerVaRCalculation } from '../api/risk'
import { fetchValuationJobsForChart } from '../api/jobHistory'
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

export function useVaR(portfolioId: string | null): UseVaRResult {
  const [varResult, setVarResult] = useState<VaRResultDto | null>(null)
  const [history, setHistory] = useState<VaRHistoryEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(!!portfolioId)
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
    if (!portfolioId) {
      setHistoryLoading(false)
      return
    }
    setHistoryLoading(true)

    try {
      const { from, to } = resolveTimeRange(timeRangeRef.current)
      const items = await fetchValuationJobsForChart(portfolioId, from, to)
      const historical = items
        .filter((job) => job.status === 'COMPLETED' && job.varValue != null && job.completedAt != null)
        .map((job) => ({
          varValue: job.varValue!,
          expectedShortfall: job.expectedShortfall ?? 0,
          calculatedAt: job.completedAt!,
          confidenceLevel: job.confidenceLevel ?? 'CL_95',
          ...(job.delta != null && job.gamma != null && job.vega != null
            ? { delta: job.delta, gamma: job.gamma, vega: job.vega, ...(job.theta != null ? { theta: job.theta } : {}) }
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
  }, [portfolioId])

  const load = useCallback(async () => {
    if (!portfolioId) return
    if (isPolling.current) return
    isPolling.current = true

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
  }, [portfolioId])

  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    if (!portfolioId) return

    initialLoadDone.current = false
    loadRef.current()

    const interval = setInterval(() => loadRef.current(), POLL_INTERVAL)
    return () => clearInterval(interval)
  }, [portfolioId])

  const loadHistoryRef = useRef(loadHistory)
  loadHistoryRef.current = loadHistory

  useEffect(() => {
    if (portfolioId) loadHistoryRef.current()
  }, [portfolioId, fetchVersion])

  const refresh = useCallback(async () => {
    if (!portfolioId) return

    setRefreshing(true)
    setError(null)

    try {
      const result = await triggerVaRCalculation(portfolioId, { confidenceLevel: selectedConfidenceLevel })
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
          const retryResult = await triggerVaRCalculation(portfolioId, { confidenceLevel: selectedConfidenceLevel })
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
  }, [portfolioId, selectedConfidenceLevel])

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

  return { varResult, greeksResult, history, filteredHistory, loading, historyLoading, refreshing, error, refresh, timeRange, setTimeRange, selectedConfidenceLevel, setSelectedConfidenceLevel, zoomIn, resetZoom, zoomDepth: zoomStack.length }
}
