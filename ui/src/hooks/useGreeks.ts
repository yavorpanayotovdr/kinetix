import { useEffect, useState } from 'react'
import { fetchGreeks } from '../api/stress'
import type { GreeksResultDto } from '../types'

export interface UseGreeksResult {
  greeksResult: GreeksResultDto | null
  loading: boolean
  error: string | null
  volBump: number
  setVolBump: (bump: number) => void
}

export function useGreeks(portfolioId: string | null): UseGreeksResult {
  const [greeksResult, setGreeksResult] = useState<GreeksResultDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [volBump, setVolBump] = useState(0)

  useEffect(() => {
    if (!portfolioId) {
      setGreeksResult(null)
      return
    }

    let cancelled = false
    setLoading(true)
    setError(null)

    async function load() {
      try {
        const data = await fetchGreeks(portfolioId!)
        if (cancelled) return
        setGreeksResult(data)
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
  }, [portfolioId])

  return { greeksResult, loading, error, volBump, setVolBump }
}
