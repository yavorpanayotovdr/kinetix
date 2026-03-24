import { useState, useCallback } from 'react'
import type { LiquidityRiskResultDto } from '../types'
import {
  fetchLatestLiquidityRisk,
  triggerLiquidityRiskCalculation,
} from '../api/liquidity'

interface UseLiquidityRiskResult {
  result: LiquidityRiskResultDto | null
  loading: boolean
  error: string | null
  refresh: (baseVar?: number) => Promise<void>
}

export function useLiquidityRisk(bookId: string | null): UseLiquidityRiskResult {
  const [result, setResult] = useState<LiquidityRiskResultDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(
    async (baseVar?: number) => {
      if (!bookId) return

      setLoading(true)
      setError(null)

      try {
        if (baseVar !== undefined) {
          const calculated = await triggerLiquidityRiskCalculation(
            bookId,
            baseVar,
          )
          setResult(calculated)
        } else {
          const latest = await fetchLatestLiquidityRisk(bookId)
          setResult(latest)
        }
      } catch (err) {
        setError(
          err instanceof Error ? err.message : 'Failed to load liquidity risk',
        )
      } finally {
        setLoading(false)
      }
    },
    [bookId],
  )

  return { result, loading, error, refresh }
}
