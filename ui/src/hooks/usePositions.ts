import { useEffect, useState } from 'react'
import { fetchPortfolios, fetchPositions } from '../api/positions'
import type { PositionDto } from '../types'

interface UsePositionsResult {
  positions: PositionDto[]
  portfolioId: string | null
  loading: boolean
  error: string | null
}

export function usePositions(): UsePositionsResult {
  const [positions, setPositions] = useState<PositionDto[]>([])
  const [portfolioId, setPortfolioId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const portfolios = await fetchPortfolios()
        if (cancelled) return

        if (portfolios.length === 0) {
          setLoading(false)
          return
        }

        const firstPortfolioId = portfolios[0].portfolioId
        setPortfolioId(firstPortfolioId)

        const positionData = await fetchPositions(firstPortfolioId)
        if (cancelled) return

        setPositions(positionData)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err.message : String(err))
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    load()

    return () => {
      cancelled = true
    }
  }, [])

  return { positions, portfolioId, loading, error }
}
