import { useCallback, useEffect, useRef, useState } from 'react'
import {
  fetchSodBaselineStatus,
  createSodSnapshot,
  resetSodBaseline,
  computePnlAttribution,
} from '../api/sodSnapshot'
import type { PnlAttributionDto, SodBaselineStatusDto } from '../types'

export interface UseSodBaselineResult {
  status: SodBaselineStatusDto | null
  loading: boolean
  error: string | null
  creating: boolean
  resetting: boolean
  computing: boolean
  createSnapshot: (jobId?: string) => Promise<void>
  resetBaseline: () => Promise<void>
  computeAttribution: () => Promise<PnlAttributionDto | null>
  refresh: () => Promise<void>
}

export function useSodBaseline(
  bookId: string | null,
): UseSodBaselineResult {
  const [status, setStatus] = useState<SodBaselineStatusDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [computing, setComputing] = useState(false)

  const loadStatus = useCallback(async () => {
    if (!bookId) return
    setLoading(true)
    setError(null)
    try {
      const result = await fetchSodBaselineStatus(bookId)
      setStatus(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [bookId])

  const loadStatusRef = useRef(loadStatus)
  loadStatusRef.current = loadStatus

  useEffect(() => {
    if (!bookId) {
      setStatus(null)
      setLoading(false)
      setError(null)
      return
    }
    loadStatusRef.current()
  }, [bookId])

  const handleCreateSnapshot = useCallback(async (jobId?: string) => {
    if (!bookId) return
    setCreating(true)
    setError(null)
    try {
      const result = await createSodSnapshot(bookId, jobId)
      setStatus(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setCreating(false)
    }
  }, [bookId])

  const handleResetBaseline = useCallback(async () => {
    if (!bookId) return
    setResetting(true)
    setError(null)
    try {
      await resetSodBaseline(bookId)
      setStatus({ exists: false, baselineDate: null, snapshotType: null, createdAt: null, sourceJobId: null, calculationType: null })
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setResetting(false)
    }
  }, [bookId])

  const handleComputeAttribution = useCallback(async (): Promise<PnlAttributionDto | null> => {
    if (!bookId) return null
    setComputing(true)
    setError(null)
    try {
      const result = await computePnlAttribution(bookId)
      return result
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      return null
    } finally {
      setComputing(false)
    }
  }, [bookId])

  return {
    status,
    loading,
    error,
    creating,
    resetting,
    computing,
    createSnapshot: handleCreateSnapshot,
    resetBaseline: handleResetBaseline,
    computeAttribution: handleComputeAttribution,
    refresh: useCallback(async () => { await loadStatusRef.current() }, []),
  }
}
