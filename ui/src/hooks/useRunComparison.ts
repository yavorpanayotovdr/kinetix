import { useState, useCallback } from 'react'
import type { RunComparisonResponseDto, VaRAttributionDto, ComparisonMode, ModelComparisonRequestDto } from '../types'
import * as api from '../api/runComparison'

export interface UseRunComparisonResult {
  comparison: RunComparisonResponseDto | null
  attribution: VaRAttributionDto | null
  loading: boolean
  attributionLoading: boolean
  error: string | null
  threshold: number
  mode: ComparisonMode
  setMode: (mode: ComparisonMode) => void
  setThreshold: (threshold: number) => void
  loadDayOverDay: (bookId: string, targetDate?: string, baseDate?: string) => Promise<void>
  compareJobs: (bookId: string, baseJobId: string, targetJobId: string) => Promise<void>
  compareModels: (bookId: string, request: ModelComparisonRequestDto) => Promise<void>
  loadAttribution: (bookId: string, targetDate?: string, baseDate?: string) => Promise<void>
  reset: () => void
}

export function useRunComparison(): UseRunComparisonResult {
  const [comparison, setComparison] = useState<RunComparisonResponseDto | null>(null)
  const [attribution, setAttribution] = useState<VaRAttributionDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [attributionLoading, setAttributionLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [threshold, setThreshold] = useState(0)
  const [mode, setMode] = useState<ComparisonMode>('DAILY_VAR')

  const loadDayOverDay = useCallback(async (bookId: string, targetDate?: string, baseDate?: string) => {
    setLoading(true)
    setError(null)
    try {
      const result = await api.compareDayOverDay(bookId, targetDate, baseDate)
      setComparison(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [])

  const compareJobs = useCallback(async (bookId: string, baseJobId: string, targetJobId: string) => {
    setLoading(true)
    setError(null)
    try {
      const result = await api.compareByJobIds(bookId, baseJobId, targetJobId)
      setComparison(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [])

  const compareModels = useCallback(async (bookId: string, request: ModelComparisonRequestDto) => {
    setLoading(true)
    setError(null)
    try {
      const result = await api.compareModelVersions(bookId, request)
      setComparison(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [])

  const loadAttribution = useCallback(async (bookId: string, targetDate?: string, baseDate?: string) => {
    setAttributionLoading(true)
    try {
      const result = await api.requestAttribution(bookId, targetDate, baseDate)
      setAttribution(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setAttributionLoading(false)
    }
  }, [])

  const reset = useCallback(() => {
    setComparison(null)
    setAttribution(null)
    setError(null)
    setThreshold(0)
  }, [])

  return {
    comparison,
    attribution,
    loading,
    attributionLoading,
    error,
    threshold,
    mode,
    setMode,
    setThreshold,
    loadDayOverDay,
    compareJobs,
    compareModels,
    loadAttribution,
    reset,
  }
}
