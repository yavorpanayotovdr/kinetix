import { useCallback, useEffect, useState } from 'react'
import { fetchPortfolios, fetchPositions } from '../api/positions'
import type { PositionDto } from '../types'

export interface UsePositionsResult {
  positions: PositionDto[]
  portfolioId: string | null
  portfolios: string[]
  selectPortfolio: (id: string) => void
  loading: boolean
  error: string | null
}

export function usePositions(): UsePositionsResult {
  const [positions, setPositions] = useState<PositionDto[]>([])
  const [portfolioId, setPortfolioId] = useState<string | null>(null)
  const [portfolios, setPortfolios] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const portfolioList = await fetchPortfolios()
        if (cancelled) return

        const ids = portfolioList.map((p) => p.portfolioId)
        setPortfolios(ids)

        if (portfolioList.length === 0) {
          setLoading(false)
          return
        }

        const firstPortfolioId = portfolioList[0].portfolioId
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

  const selectPortfolio = useCallback(async (id: string) => {
    setPortfolioId(id)
    setLoading(true)
    setError(null)
    try {
      const positionData = await fetchPositions(id)
      setPositions(positionData)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [])

  return { positions, portfolioId, portfolios, selectPortfolio, loading, error }
}
