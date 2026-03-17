import { useCallback, useEffect, useState } from 'react'
import { fetchScenarios, runAllStressTests } from '../api/stress'
import type { StressTestResultDto } from '../types'

export interface UseRunAllScenariosResult {
  scenarios: string[]
  results: StressTestResultDto[]
  selectedScenario: string | null
  setSelectedScenario: (scenario: string | null) => void
  confidenceLevel: string
  setConfidenceLevel: (cl: string) => void
  timeHorizonDays: string
  setTimeHorizonDays: (days: string) => void
  loading: boolean
  error: string | null
  runAll: () => void
  appendResult: (result: StressTestResultDto) => void
}

export function useRunAllScenarios(bookId: string | null): UseRunAllScenariosResult {
  const [scenarios, setScenarios] = useState<string[]>([])
  const [results, setResults] = useState<StressTestResultDto[]>([])
  const [selectedScenario, setSelectedScenario] = useState<string | null>(null)
  const [confidenceLevel, setConfidenceLevel] = useState('CL_95')
  const [timeHorizonDays, setTimeHorizonDays] = useState('1')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const data = await fetchScenarios()
        if (cancelled) return
        setScenarios(data)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err.message : String(err))
      }
    }
    load()
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    setResults([])
    setSelectedScenario(null)
    setError(null)
  }, [bookId])

  const runAll = useCallback(async () => {
    if (!bookId || scenarios.length === 0) return
    setLoading(true)
    setError(null)
    try {
      const data = await runAllStressTests(bookId, scenarios, {
        confidenceLevel,
        timeHorizonDays,
      })
      const sorted = [...data].sort(
        (a, b) => Math.abs(Number(b.pnlImpact)) - Math.abs(Number(a.pnlImpact)),
      )
      setResults(sorted)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [bookId, scenarios, confidenceLevel, timeHorizonDays])

  const appendResult = useCallback((result: StressTestResultDto) => {
    setResults((prev) => {
      const filtered = prev.filter((r) => r.scenarioName !== result.scenarioName)
      const updated = [...filtered, result]
      return updated.sort(
        (a, b) => Math.abs(Number(b.pnlImpact)) - Math.abs(Number(a.pnlImpact)),
      )
    })
  }, [])

  return {
    scenarios,
    results,
    selectedScenario,
    setSelectedScenario,
    confidenceLevel,
    setConfidenceLevel,
    timeHorizonDays,
    setTimeHorizonDays,
    loading,
    error,
    runAll,
    appendResult,
  }
}
