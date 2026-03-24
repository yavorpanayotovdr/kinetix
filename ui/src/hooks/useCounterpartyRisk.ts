import { useCallback, useEffect, useState } from 'react'
import {
  fetchAllCounterpartyExposures,
  fetchCounterpartyExposure,
  fetchCounterpartyExposureHistory,
  triggerCVAComputation,
  triggerPFEComputation,
  type CounterpartyExposureDto,
} from '../api/counterpartyRisk'

export interface UseCounterpartyRiskResult {
  exposures: CounterpartyExposureDto[]
  selected: CounterpartyExposureDto | null
  history: CounterpartyExposureDto[]
  loading: boolean
  computing: boolean
  error: string | null
  selectCounterparty: (counterpartyId: string) => void
  computePFE: (counterpartyId: string) => Promise<void>
  computeCVA: (counterpartyId: string) => Promise<void>
  refresh: () => void
}

export function useCounterpartyRisk(): UseCounterpartyRiskResult {
  const [exposures, setExposures] = useState<CounterpartyExposureDto[]>([])
  const [selected, setSelected] = useState<CounterpartyExposureDto | null>(null)
  const [history, setHistory] = useState<CounterpartyExposureDto[]>([])
  const [loading, setLoading] = useState(false)
  const [computing, setComputing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadAll = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await fetchAllCounterpartyExposures()
      setExposures(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadAll()
  }, [loadAll])

  const selectCounterparty = useCallback(async (counterpartyId: string) => {
    setError(null)
    try {
      const [exposure, hist] = await Promise.all([
        fetchCounterpartyExposure(counterpartyId),
        fetchCounterpartyExposureHistory(counterpartyId),
      ])
      setSelected(exposure)
      setHistory(hist)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }, [])

  const computePFE = useCallback(async (counterpartyId: string) => {
    setComputing(true)
    setError(null)
    try {
      const result = await triggerPFEComputation(counterpartyId)
      setSelected(result)
      setExposures((prev) =>
        prev.map((e) => (e.counterpartyId === counterpartyId ? result : e)),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setComputing(false)
    }
  }, [])

  const computeCVA = useCallback(async (counterpartyId: string) => {
    setComputing(true)
    setError(null)
    try {
      const result = await triggerCVAComputation(counterpartyId)
      if (result === null) {
        setError('No PFE snapshot found. Run PFE computation first.')
        return
      }
      setSelected(result)
      setExposures((prev) =>
        prev.map((e) => (e.counterpartyId === counterpartyId ? result : e)),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setComputing(false)
    }
  }, [])

  return {
    exposures,
    selected,
    history,
    loading,
    computing,
    error,
    selectCounterparty,
    computePFE,
    computeCVA,
    refresh: loadAll,
  }
}
