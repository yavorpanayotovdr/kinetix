import { useCallback, useEffect, useState } from 'react'
import { fetchBooks, fetchPositions } from '../api/positions'
import type { PositionDto } from '../types'

export const ALL_BOOKS = '__ALL__'

export interface BookOption {
  value: string
  label: string
}

export interface UseBookSelectorResult {
  bookOptions: BookOption[]
  selectedBookId: string | null
  isAllSelected: boolean
  allBookIds: string[]
  positions: PositionDto[]
  aggregatedPositions: PositionDto[]
  selectBook: (id: string) => void
  loading: boolean
  error: string | null
}

export function useBookSelector(): UseBookSelectorResult {
  const [bookIds, setBookIds] = useState<string[]>([])
  const [selectedBookId, setSelectedBookId] = useState<string | null>(null)
  const [positions, setPositions] = useState<PositionDto[]>([])
  const [aggregatedPositions, setAggregatedPositions] = useState<PositionDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const bookList = await fetchBooks()
        if (cancelled) return

        const ids = bookList.map((b) => b.portfolioId).sort()
        setBookIds(ids)

        if (ids.length === 0) {
          setLoading(false)
          return
        }

        const firstBookId = ids[0]
        setSelectedBookId(firstBookId)

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
  }, [])

  const isAllSelected = selectedBookId === ALL_BOOKS

  const bookOptions: BookOption[] = [
    ...(bookIds.length > 1
      ? [{ value: ALL_BOOKS, label: 'All Books' }]
      : []),
    ...bookIds.map((id) => ({ value: id, label: id })),
  ]

  const selectBook = useCallback(async (id: string) => {
    setSelectedBookId(id)
    setLoading(true)
    setError(null)

    try {
      if (id === ALL_BOOKS) {
        const allPositions: PositionDto[] = []
        for (const bookId of bookIds) {
          const posData = await fetchPositions(bookId)
          allPositions.push(...posData)
        }
        setPositions(allPositions)
        setAggregatedPositions(aggregatePositions(allPositions))
      } else {
        const posData = await fetchPositions(id)
        setPositions(posData)
        setAggregatedPositions([])
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [bookIds])

  return {
    bookOptions,
    selectedBookId,
    isAllSelected,
    allBookIds: bookIds,
    positions,
    aggregatedPositions,
    selectBook,
    loading,
    error,
  }
}

function aggregatePositions(positions: PositionDto[]): PositionDto[] {
  const byInstrument = new Map<string, PositionDto[]>()

  for (const pos of positions) {
    const existing = byInstrument.get(pos.instrumentId) ?? []
    existing.push(pos)
    byInstrument.set(pos.instrumentId, existing)
  }

  return Array.from(byInstrument.entries()).map(([instrumentId, group]) => {
    const totalQuantity = group.reduce((sum, p) => sum + Number(p.quantity), 0)
    const totalMarketValue = group.reduce(
      (sum, p) => sum + Number(p.marketValue.amount),
      0,
    )
    const totalUnrealizedPnl = group.reduce(
      (sum, p) => sum + Number(p.unrealizedPnl.amount),
      0,
    )
    const currency = group[0].marketPrice.currency

    return {
      bookId: ALL_BOOKS,
      instrumentId,
      assetClass: group[0].assetClass,
      quantity: String(totalQuantity),
      averageCost: group[0].averageCost,
      marketPrice: group[0].marketPrice,
      marketValue: { amount: totalMarketValue.toFixed(2), currency },
      unrealizedPnl: { amount: totalUnrealizedPnl.toFixed(2), currency },
    }
  })
}
