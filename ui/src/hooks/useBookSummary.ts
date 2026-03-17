import { useCallback, useEffect, useState } from 'react'
import { fetchBookSummary } from '../api/bookSummary'
import type { PortfolioAggregationDto } from '../types'

export interface UseBookSummaryResult {
  summary: PortfolioAggregationDto | null
  baseCurrency: string
  setBaseCurrency: (currency: string) => void
  loading: boolean
  error: string | null
}

export function useBookSummary(bookId: string | null): UseBookSummaryResult {
  const [summary, setSummary] = useState<PortfolioAggregationDto | null>(null)
  const [baseCurrency, setBaseCurrency] = useState('USD')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!bookId) return

    let cancelled = false

    fetchBookSummary(bookId, baseCurrency)
      .then((data) => {
        if (!cancelled) setSummary(data)
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [bookId, baseCurrency])

  const handleSetBaseCurrency = useCallback((currency: string) => {
    setLoading(true)
    setError(null)
    setBaseCurrency(currency)
  }, [])

  return { summary, baseCurrency, setBaseCurrency: handleSetBaseCurrency, loading, error }
}
