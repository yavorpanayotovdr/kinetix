import { useEffect, useState } from 'react'
import { fetchDataQualityStatus } from '../api/dataQuality'
import type { DataQualityStatus } from '../types'

const POLL_INTERVAL = 30_000

const MONITORING_UNAVAILABLE_STATUS: DataQualityStatus = {
  overall: 'CRITICAL',
  checks: [
    {
      name: 'Data Quality Monitoring',
      status: 'CRITICAL',
      message: 'Monitoring unavailable',
      lastChecked: new Date().toISOString(),
    },
  ],
}

export interface UseDataQualityResult {
  status: DataQualityStatus | null
  syntheticStatus: DataQualityStatus | null
  loading: boolean
  error: string | null
}

export function useDataQuality(): UseDataQualityResult {
  const [status, setStatus] = useState<DataQualityStatus | null>(null)
  const [syntheticStatus, setSyntheticStatus] = useState<DataQualityStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const result = await fetchDataQualityStatus()
        if (!cancelled) {
          setStatus((prev) => {
            if (prev && prev.overall === result.overall && prev.checks.length === result.checks.length && JSON.stringify(prev.checks) === JSON.stringify(result.checks)) return prev
            return result
          })
          setError(null)
          setSyntheticStatus(null)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : String(err))
          setSyntheticStatus(MONITORING_UNAVAILABLE_STATUS)
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

  return { status, syntheticStatus, loading, error }
}
