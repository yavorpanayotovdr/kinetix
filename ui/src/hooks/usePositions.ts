import { useCallback, useEffect, useState } from 'react'
import { fetchBooks, fetchPositions } from '../api/positions'
import type { PositionDto } from '../types'

export interface UsePositionsResult {
  positions: PositionDto[]
  bookId: string | null
  books: string[]
  selectBook: (id: string) => void
  refreshPositions: () => void
  retryInitialLoad: () => void
  loading: boolean
  error: string | null
}

export function usePositions(): UsePositionsResult {
  const [positions, setPositions] = useState<PositionDto[]>([])
  const [bookId, setBookId] = useState<string | null>(null)
  const [books, setBooks] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retryCount, setRetryCount] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const bookList = await fetchBooks()
        if (cancelled) return

        const ids = bookList.map((b) => b.bookId).sort()
        setBooks(ids)

        if (bookList.length === 0) {
          setLoading(false)
          return
        }

        const firstBookId = bookList[0].bookId
        setBookId(firstBookId)

        const positionData = await fetchPositions(firstBookId)
        if (cancelled) return

        setPositions(positionData)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err.message : String(err))
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    load()

    return () => {
      cancelled = true
    }
  }, [retryCount])

  const retryInitialLoad = useCallback(() => {
    setRetryCount((c) => c + 1)
  }, [])

  const selectBook = useCallback(async (id: string) => {
    setBookId(id)
    setLoading(true)
    setError(null)
    try {
      const positionData = await fetchPositions(id)
      setPositions(positionData)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [])

  const refreshPositions = useCallback(async () => {
    if (!bookId) return
    try {
      const positionData = await fetchPositions(bookId)
      setPositions(positionData)
    } catch {
      // Silently ignore — refresh is best-effort on reconnect
    }
  }, [bookId])

  return { positions, bookId, books, selectBook, refreshPositions, retryInitialLoad, loading, error }
}
