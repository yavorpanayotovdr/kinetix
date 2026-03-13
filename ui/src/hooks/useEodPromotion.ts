import { useState, useCallback } from 'react'
import {
  promoteToOfficialEod,
  demoteOfficialEod,
  type EodPromotionResponse,
} from '../api/officialEod'

type PromotionState = 'idle' | 'loading' | 'promoted' | 'error'

interface UseEodPromotionResult {
  state: PromotionState
  error: string | null
  errorStatus: number | null
  promote: (jobId: string, promotedBy: string) => Promise<EodPromotionResponse | null>
  demote: (jobId: string, demotedBy: string) => Promise<EodPromotionResponse | null>
  reset: () => void
}

export function useEodPromotion(): UseEodPromotionResult {
  const [state, setState] = useState<PromotionState>('idle')
  const [error, setError] = useState<string | null>(null)
  const [errorStatus, setErrorStatus] = useState<number | null>(null)

  const promote = useCallback(async (jobId: string, promotedBy: string) => {
    setState('loading')
    setError(null)
    setErrorStatus(null)
    try {
      const result = await promoteToOfficialEod(jobId, promotedBy)
      setState('promoted')
      return result
    } catch (e: unknown) {
      const err = e as Error & { status?: number }
      setState('error')
      setError(err.message)
      setErrorStatus(err.status ?? null)
      return null
    }
  }, [])

  const demote = useCallback(async (jobId: string, demotedBy: string) => {
    setState('loading')
    setError(null)
    setErrorStatus(null)
    try {
      const result = await demoteOfficialEod(jobId, demotedBy)
      setState('idle')
      return result
    } catch (e: unknown) {
      const err = e as Error & { status?: number }
      setState('error')
      setError(err.message)
      setErrorStatus(err.status ?? null)
      return null
    }
  }, [])

  const reset = useCallback(() => {
    setState('idle')
    setError(null)
    setErrorStatus(null)
  }, [])

  return { state, error, errorStatus, promote, demote, reset }
}
