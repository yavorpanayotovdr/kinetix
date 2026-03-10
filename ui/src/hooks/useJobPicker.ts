import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { fetchValuationJobs } from '../api/jobHistory'
import { isUuidPrefix, jobMatchesSearch } from '../utils/jobSearch'
import type { ValuationJobSummaryDto, TimeRange } from '../types'

const PAGE_SIZE = 20

function todayRange(): TimeRange {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  return { from: start.toISOString(), to: now.toISOString(), label: 'Today' }
}

const SLIDING_DURATIONS: Record<string, number> = {
  'Last 1h': 60 * 60 * 1000,
  'Last 24h': 24 * 60 * 60 * 1000,
  'Last 7d': 7 * 24 * 60 * 60 * 1000,
}

function resolveQueryRange(range: TimeRange): { from: string; to: string } {
  const duration = SLIDING_DURATIONS[range.label]
  if (duration) {
    const now = new Date()
    return { from: new Date(now.getTime() - duration).toISOString(), to: now.toISOString() }
  }
  if (range.label === 'Today') {
    const now = new Date()
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    return { from: start.toISOString(), to: now.toISOString() }
  }
  return { from: range.from, to: range.to }
}

export interface UseJobPickerResult {
  jobs: ValuationJobSummaryDto[]
  loading: boolean
  error: string | null
  timeRange: TimeRange
  setTimeRange: (r: TimeRange) => void
  search: string
  setSearch: (s: string) => void
  page: number
  totalPages: number
  totalCount: number
  hasNextPage: boolean
  nextPage: () => void
  prevPage: () => void
  firstPage: () => void
  lastPage: () => void
  refresh: () => void
}

export function useJobPicker(
  portfolioId: string | null,
  open: boolean,
): UseJobPickerResult {
  const [allJobs, setAllJobs] = useState<ValuationJobSummaryDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [timeRange, setTimeRangeInternal] = useState<TimeRange>(todayRange)
  const [search, setSearchInternal] = useState('')
  const [page, setPage] = useState(0)
  const [totalCount, setTotalCount] = useState(0)
  const [fetchVersion, setFetchVersion] = useState(0)

  const timeRangeRef = useRef(timeRange)
  timeRangeRef.current = timeRange

  const pageRef = useRef(page)
  pageRef.current = page

  // Reset state when dialog closes
  const prevOpen = useRef(open)
  useEffect(() => {
    if (prevOpen.current && !open) {
      setSearchInternal('')
      setPage(0)
      setTimeRangeInternal(todayRange())
    }
    prevOpen.current = open
  }, [open])

  const load = useCallback(async () => {
    if (!portfolioId || !open) return

    setLoading(true)
    setError(null)

    try {
      const { from, to } = resolveQueryRange(timeRangeRef.current)
      const { items, totalCount: count } = await fetchValuationJobs(
        portfolioId,
        PAGE_SIZE,
        pageRef.current * PAGE_SIZE,
        from,
        to,
        'COMPLETED',
      )
      setTotalCount(count)
      setAllJobs(items)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [portfolioId, open])

  const loadRef = useRef(load)
  loadRef.current = load

  useEffect(() => {
    if (!portfolioId || !open) return
    loadRef.current()
  }, [portfolioId, open, fetchVersion, page])

  const setTimeRange = useCallback((range: TimeRange) => {
    setPage(0)
    setTimeRangeInternal(range)
    setFetchVersion((v) => v + 1)
  }, [])

  const setSearch = useCallback((term: string) => {
    setSearchInternal(term)
  }, [])

  const filteredJobs = useMemo(() => {
    const trimmed = search.trim()
    if (!trimmed) return allJobs

    if (isUuidPrefix(trimmed)) {
      return allJobs.filter((j) =>
        j.jobId.toLowerCase().includes(trimmed.toLowerCase()),
      )
    }

    return allJobs.filter((j) => jobMatchesSearch(j, trimmed))
  }, [allJobs, search])

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE))
  const hasNextPage = (page + 1) * PAGE_SIZE < totalCount

  const nextPage = useCallback(() => {
    if (!hasNextPage) return
    setPage((p) => p + 1)
  }, [hasNextPage])

  const prevPage = useCallback(() => {
    setPage((p) => (p === 0 ? 0 : p - 1))
  }, [])

  const firstPage = useCallback(() => {
    setPage(0)
  }, [])

  const lastPage = useCallback(() => {
    if (!hasNextPage) return
    setPage(totalPages - 1)
  }, [hasNextPage, totalPages])

  const refresh = useCallback(() => {
    setFetchVersion((v) => v + 1)
  }, [])

  return {
    jobs: filteredJobs,
    loading,
    error,
    timeRange,
    setTimeRange,
    search,
    setSearch,
    page,
    totalPages,
    totalCount,
    hasNextPage,
    nextPage,
    prevPage,
    firstPage,
    lastPage,
    refresh,
  }
}
