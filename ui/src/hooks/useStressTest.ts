import { useCallback, useEffect, useState } from 'react'
import { fetchScenarios, runStressTest } from '../api/stress'
import type { StressTestResultDto } from '../types'

export interface UseStressTestResult {
  scenarios: string[]
  selectedScenario: string
  setSelectedScenario: (scenario: string) => void
  result: StressTestResultDto | null
  results: StressTestResultDto[]
  loading: boolean
  error: string | null
  run: () => void
}

export function useStressTest(portfolioId: string | null): UseStressTestResult {
  const [scenarios, setScenarios] = useState<string[]>([])
  const [selectedScenario, setSelectedScenario] = useState('')
  const [result, setResult] = useState<StressTestResultDto | null>(null)
  const [results, setResults] = useState<StressTestResultDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadScenarios() {
      try {
        const data = await fetchScenarios()
        if (cancelled) return
        setScenarios(data)
        if (data.length > 0) {
          setSelectedScenario(data[0])
        }
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err.message : String(err))
      }
    }

    loadScenarios()

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    setResult(null)
    setResults([])
    setError(null)
  }, [portfolioId])

  const run = useCallback(async () => {
    if (!portfolioId || !selectedScenario) return
    setLoading(true)
    setError(null)
    try {
      const data = await runStressTest(portfolioId, selectedScenario)
      setResult(data)
      if (data) {
        setResults((prev) => {
          const filtered = prev.filter((r) => r.scenarioName !== data.scenarioName)
          return [...filtered, data]
        })
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [portfolioId, selectedScenario])

  return { scenarios, selectedScenario, setSelectedScenario, result, results, loading, error, run }
}
