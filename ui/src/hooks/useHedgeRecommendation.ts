import { useState, useCallback } from 'react'
import type { HedgeRecommendationDto, HedgeSuggestRequestDto, HedgeTarget } from '../types'
import { suggestHedge, fetchLatestHedgeRecommendations } from '../api/hedge'

interface UseHedgeRecommendationResult {
  recommendation: HedgeRecommendationDto | null
  history: HedgeRecommendationDto[]
  loading: boolean
  error: string | null
  suggest: (target: HedgeTarget, reductionPct: number, maxSuggestions?: number) => Promise<void>
  loadHistory: () => Promise<void>
  clear: () => void
}

export function useHedgeRecommendation(bookId: string | null): UseHedgeRecommendationResult {
  const [recommendation, setRecommendation] = useState<HedgeRecommendationDto | null>(null)
  const [history, setHistory] = useState<HedgeRecommendationDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const suggest = useCallback(
    async (target: HedgeTarget, reductionPct: number, maxSuggestions = 5) => {
      if (!bookId) return
      setLoading(true)
      setError(null)
      try {
        const request: HedgeSuggestRequestDto = {
          targetMetric: target,
          targetReductionPct: reductionPct,
          maxSuggestions,
        }
        const result = await suggestHedge(bookId, request)
        setRecommendation(result)
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to suggest hedge')
      } finally {
        setLoading(false)
      }
    },
    [bookId],
  )

  const loadHistory = useCallback(async () => {
    if (!bookId) return
    setLoading(true)
    setError(null)
    try {
      const results = await fetchLatestHedgeRecommendations(bookId)
      setHistory(results)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load hedge history')
    } finally {
      setLoading(false)
    }
  }, [bookId])

  const clear = useCallback(() => {
    setRecommendation(null)
    setError(null)
  }, [])

  return { recommendation, history, loading, error, suggest, loadHistory, clear }
}
