import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { BookDto, PositionDto } from '../types'

vi.mock('../api/positions')

import { fetchBooks, fetchPositions } from '../api/positions'
import { usePositions } from './usePositions'

const mockFetchBooks = vi.mocked(fetchBooks)
const mockFetchPositions = vi.mocked(fetchPositions)

const position: PositionDto = {
  bookId: 'book-1',
  instrumentId: 'AAPL',
  assetClass: 'EQUITY',
  quantity: '100',
  averageCost: { amount: '150.00', currency: 'USD' },
  marketPrice: { amount: '155.00', currency: 'USD' },
  marketValue: { amount: '15500.00', currency: 'USD' },
  unrealizedPnl: { amount: '500.00', currency: 'USD' },
}

describe('usePositions', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('starts in loading state', () => {
    mockFetchBooks.mockReturnValue(new Promise(() => {}))

    const { result } = renderHook(() => usePositions())

    expect(result.current.loading).toBe(true)
    expect(result.current.positions).toEqual([])
    expect(result.current.bookId).toBeNull()
    expect(result.current.books).toEqual([])
    expect(result.current.error).toBeNull()
  })

  it('loads positions from first book and exposes sorted book list', async () => {
    const books: BookDto[] = [
      { bookId: 'book-2' },
      { bookId: 'book-1' },
    ]
    mockFetchBooks.mockResolvedValue(books)
    mockFetchPositions.mockResolvedValue([position])

    const { result } = renderHook(() => usePositions())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.positions).toEqual([position])
    expect(result.current.bookId).toBe('book-2')
    expect(result.current.books).toEqual(['book-1', 'book-2'])
    expect(result.current.error).toBeNull()
    expect(mockFetchPositions).toHaveBeenCalledWith('book-2')
  })

  it('returns empty positions when no books exist', async () => {
    mockFetchBooks.mockResolvedValue([])

    const { result } = renderHook(() => usePositions())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.positions).toEqual([])
    expect(result.current.bookId).toBeNull()
    expect(result.current.books).toEqual([])
    expect(mockFetchPositions).not.toHaveBeenCalled()
  })

  it('sets error on fetch failure', async () => {
    mockFetchBooks.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => usePositions())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('Network error')
    expect(result.current.positions).toEqual([])
  })

  it('selectBook switches book and reloads positions', async () => {
    const books: BookDto[] = [
      { bookId: 'book-1' },
      { bookId: 'book-2' },
    ]
    mockFetchBooks.mockResolvedValue(books)
    mockFetchPositions.mockResolvedValue([position])

    const { result } = renderHook(() => usePositions())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    const position2: PositionDto = { ...position, bookId: 'book-2', instrumentId: 'MSFT' }
    mockFetchPositions.mockResolvedValue([position2])

    await act(async () => {
      result.current.selectBook('book-2')
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.bookId).toBe('book-2')
    expect(result.current.positions).toEqual([position2])
    expect(mockFetchPositions).toHaveBeenCalledWith('book-2')
  })
})
