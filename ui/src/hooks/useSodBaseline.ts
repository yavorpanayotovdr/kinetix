import { useCallback, useEffect, useState } from 'react'
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
  createSnapshot: () => Promise<void>
  resetBaseline: () => Promise<void>
  computeAttribution: () => Promise<PnlAttributionDto | null>
  refresh: () => Promise<void>
}

export function useSodBaseline(
  portfolioId: string | null,
): UseSodBaselineResult {
  const [status, setStatus] = useState<SodBaselineStatusDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [computing, setComputing] = useState(false)

  const loadStatus = useCallback(async () => {
    if (!portfolioId) return
    setLoading(true)
    setError(null)
    try {
      const result = await fetchSodBaselineStatus(portfolioId)
      setStatus(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [portfolioId])

  useEffect(() => {
    if (!portfolioId) {
      setStatus(null)
      setLoading(false)
      setError(null)
      return
    }
    loadStatus()
  }, [portfolioId, loadStatus])

  const handleCreateSnapshot = useCallback(async () => {
    if (!portfolioId) return
    setCreating(true)
    setError(null)
    try {
      const result = await createSodSnapshot(portfolioId)
      setStatus(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setCreating(false)
    }
  }, [portfolioId])

  const handleResetBaseline = useCallback(async () => {
    if (!portfolioId) return
    setResetting(true)
    setError(null)
    try {
      await resetSodBaseline(portfolioId)
      setStatus({ exists: false, baselineDate: null, snapshotType: null, createdAt: null })
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setResetting(false)
    }
  }, [portfolioId])

  const handleComputeAttribution = useCallback(async (): Promise<PnlAttributionDto | null> => {
    if (!portfolioId) return null
    setComputing(true)
    setError(null)
    try {
      const result = await computePnlAttribution(portfolioId)
      return result
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      return null
    } finally {
      setComputing(false)
    }
  }, [portfolioId])

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
    refresh: loadStatus,
  }
}
