import { useCallback, useEffect, useState } from 'react'
import { fetchFrtb, generateReport } from '../api/regulatory'
import type { FrtbResultDto } from '../types'

export interface UseRegulatoryResult {
  result: FrtbResultDto | null
  loading: boolean
  error: string | null
  calculate: () => void
  downloadCsv: () => void
  downloadXbrl: () => void
}

export function useRegulatory(bookId: string | null): UseRegulatoryResult {
  const [result, setResult] = useState<FrtbResultDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setResult(null)
    setError(null)
  }, [bookId])

  const calculate = useCallback(async () => {
    if (!bookId) return
    setLoading(true)
    setError(null)
    try {
      const data = await fetchFrtb(bookId)
      setResult(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [bookId])

  const triggerDownload = useCallback((content: string, filename: string) => {
    const blob = new Blob([content], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  }, [])

  const downloadCsv = useCallback(async () => {
    if (!bookId) return
    try {
      const report = await generateReport(bookId, 'CSV')
      if (report) {
        triggerDownload(report.content, `frtb-${bookId}.csv`)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }, [bookId, triggerDownload])

  const downloadXbrl = useCallback(async () => {
    if (!bookId) return
    try {
      const report = await generateReport(bookId, 'XBRL')
      if (report) {
        triggerDownload(report.content, `frtb-${bookId}.xbrl`)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }, [bookId, triggerDownload])

  return { result, loading, error, calculate, downloadCsv, downloadXbrl }
}
