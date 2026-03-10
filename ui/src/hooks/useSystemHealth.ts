import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchSystemHealth, type SystemHealthResponse } from '../api/system'

export interface UseSystemHealthResult {
  health: SystemHealthResponse | null
  loading: boolean
  error: string | null
  refresh: () => void
}

export function useSystemHealth(): UseSystemHealthResult {
  const [health, setHealth] = useState<SystemHealthResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const load = useCallback(async () => {
    try {
      const data = await fetchSystemHealth()
      setHealth(data)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [])

  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    loadRef.current()
    intervalRef.current = setInterval(() => loadRef.current(), 30_000)
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [])

  return { health, loading, error, refresh: load }
}
