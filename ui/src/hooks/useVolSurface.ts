import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchVolSurface, fetchVolSurfaceDiff } from '../api/volSurface'
import type { VolSurfaceDiffResponse, VolSurfaceResponse } from '../api/volSurface'

export interface UseVolSurfaceResult {
  surface: VolSurfaceResponse | null
  diff: VolSurfaceDiffResponse | null
  maturities: number[]
  loading: boolean
  diffLoading: boolean
  error: string | null
}

export function useVolSurface(
  instrumentId: string | null,
  compareDate?: string,
): UseVolSurfaceResult {
  const [surface, setSurface] = useState<VolSurfaceResponse | null>(null)
  const [diff, setDiff] = useState<VolSurfaceDiffResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [diffLoading, setDiffLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const initialLoadDone = useRef(false)

  const loadSurface = useCallback(async () => {
    if (!instrumentId) return

    if (!initialLoadDone.current) {
      setLoading(true)
    }
    setError(null)

    try {
      const data = await fetchVolSurface(instrumentId)
      setSurface(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setSurface(null)
    } finally {
      setLoading(false)
      initialLoadDone.current = true
    }
  }, [instrumentId])

  const loadSurfaceRef = useRef(loadSurface)
  loadSurfaceRef.current = loadSurface

  useEffect(() => {
    if (!instrumentId) {
      setSurface(null)
      setDiff(null)
      setLoading(false)
      setError(null)
      initialLoadDone.current = false
      return
    }

    initialLoadDone.current = false
    loadSurfaceRef.current()
  }, [instrumentId])

  const loadDiff = useCallback(async () => {
    if (!instrumentId || !compareDate) {
      setDiff(null)
      return
    }

    setDiffLoading(true)
    try {
      const data = await fetchVolSurfaceDiff(instrumentId, compareDate)
      setDiff(data)
    } catch {
      setDiff(null)
    } finally {
      setDiffLoading(false)
    }
  }, [instrumentId, compareDate])

  const loadDiffRef = useRef(loadDiff)
  loadDiffRef.current = loadDiff

  useEffect(() => {
    loadDiffRef.current()
  }, [instrumentId, compareDate])

  const maturities = useMemo(() => {
    if (!surface) return []
    const unique = [...new Set(surface.points.map((p) => p.maturityDays))]
    return unique.sort((a, b) => a - b)
  }, [surface])

  return { surface, diff, maturities, loading, diffLoading, error }
}
