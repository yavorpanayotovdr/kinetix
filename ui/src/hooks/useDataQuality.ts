import { useEffect, useState } from 'react'
import { fetchDataQualityStatus } from '../api/dataQuality'
import type { DataQualityStatus } from '../types'

const POLL_INTERVAL = 30_000

export interface UseDataQualityResult {
  status: DataQualityStatus | null
  loading: boolean
  error: string | null
}

export function useDataQuality(): UseDataQualityResult {
  const [status, setStatus] = useState<DataQualityStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const result = await fetchDataQualityStatus()
        if (!cancelled) {
          setStatus(result)
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

  return { status, loading, error }
}
