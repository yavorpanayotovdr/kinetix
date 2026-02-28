import { useCallback, useEffect, useState } from 'react'
import { fetchPositionRisk } from '../api/risk'
import type { PositionRiskDto } from '../types'

export interface UsePositionRiskResult {
  positionRisk: PositionRiskDto[]
  loading: boolean
  error: string | null
  refresh: () => Promise<void>
}

export function usePositionRisk(portfolioId: string | null): UsePositionRiskResult {
  const [positionRisk, setPositionRisk] = useState<PositionRiskDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!portfolioId) return

    setLoading(true)
    setError(null)

    try {
      const data = await fetchPositionRisk(portfolioId)
      setPositionRisk(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setPositionRisk([])
    } finally {
      setLoading(false)
    }
  }, [portfolioId])

  useEffect(() => {
    if (!portfolioId) {
      setPositionRisk([])
      setLoading(false)
      setError(null)
      return
    }

    load()
  }, [portfolioId, load])

  const refresh = useCallback(async () => {
    await load()
  }, [load])

  return { positionRisk, loading, error, refresh }
}
