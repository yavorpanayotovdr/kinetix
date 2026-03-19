import { useCallback, useEffect, useState } from 'react'
import { fetchBookSummary } from '../api/bookSummary'
import { fetchDeskSummary, fetchDivisionSummary, fetchFirmSummary } from '../api/hierarchy'
import type { BookAggregationDto } from '../types'
import type { HierarchySelection } from './useHierarchySelector'

export interface UseHierarchySummaryResult {
  summary: BookAggregationDto | null
  baseCurrency: string
  setBaseCurrency: (currency: string) => void
  loading: boolean
  error: string | null
  summaryLabel: string
}

export function useHierarchySummary(
  selection: HierarchySelection,
): UseHierarchySummaryResult {
  const [summary, setSummary] = useState<BookAggregationDto | null>(null)
  const [baseCurrency, setBaseCurrencyState] = useState('USD')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)

    async function fetchSummary() {
      try {
        let data: BookAggregationDto
        switch (selection.level) {
          case 'firm':
            data = await fetchFirmSummary(baseCurrency)
            break
          case 'division':
            if (!selection.divisionId) return
            data = await fetchDivisionSummary(selection.divisionId, baseCurrency)
            break
          case 'desk':
            if (!selection.deskId) return
            data = await fetchDeskSummary(selection.deskId, baseCurrency)
            break
          case 'book':
            if (!selection.bookId) return
            data = await fetchBookSummary(selection.bookId, baseCurrency)
            break
        }
        if (!cancelled) setSummary(data)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    fetchSummary()
    return () => { cancelled = true }
  }, [selection.level, selection.divisionId, selection.deskId, selection.bookId, baseCurrency])

  const setBaseCurrency = useCallback((currency: string) => {
    setLoading(true)
    setError(null)
    setBaseCurrencyState(currency)
  }, [])

  const summaryLabel = (() => {
    switch (selection.level) {
      case 'firm': return 'Firm Summary'
      case 'division': return 'Division Summary'
      case 'desk': return 'Desk Summary'
      case 'book': return 'Book Summary'
    }
  })()

  return { summary, baseCurrency, setBaseCurrency, loading, error, summaryLabel }
}
