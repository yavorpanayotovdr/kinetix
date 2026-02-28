import { useEffect, useState } from 'react'
import { fetchRules } from '../api/notifications'

export interface UseVarLimitResult {
  varLimit: number | null
  loading: boolean
}

export function useVarLimit(): UseVarLimitResult {
  const [varLimit, setVarLimit] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const rules = await fetchRules()
        if (cancelled) return
        const varBreachRule = rules.find(
          (r) => r.type === 'VAR_BREACH' && r.enabled,
        )
        setVarLimit(varBreachRule ? varBreachRule.threshold : null)
      } catch {
        if (!cancelled) setVarLimit(null)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()

    return () => {
      cancelled = true
    }
  }, [])

  return { varLimit, loading }
}
