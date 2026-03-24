import { useEffect, useState } from 'react'
import { fetchCurrentRegime } from '../api/regime'
import type { MarketRegimeDto } from '../types'

const POLL_INTERVAL = 60_000

export interface UseMarketRegimeResult {
  regime: MarketRegimeDto | null
  loading: boolean
  error: string | null
}

export function useMarketRegime(): UseMarketRegimeResult {
  const [regime, setRegime] = useState<MarketRegimeDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const result = await fetchCurrentRegime()
        if (!cancelled) {
          setRegime(result)
          setError(null)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : String(err))
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    load()

    const interval = setInterval(load, POLL_INTERVAL)

    return () => {
      cancelled = true
      clearInterval(interval)
    }
  }, [])

  return { regime, loading, error }
}
