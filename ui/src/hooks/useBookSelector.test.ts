import { renderHook, act, waitFor } from '@testing-library/react'
import { describe, expect, it, beforeEach, vi } from 'vitest'
import { useBookSelector } from './useBookSelector'

vi.mock('../api/positions', () => ({
  fetchBooks: vi.fn(),
  fetchPositions: vi.fn(),
}))

import { fetchBooks, fetchPositions } from '../api/positions'

const mockFetchBooks = vi.mocked(fetchBooks)
const mockFetchPositions = vi.mocked(fetchPositions)

describe('useBookSelector', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('should include All Books option when multiple books exist', async () => {
    mockFetchBooks.mockResolvedValue([
      { portfolioId: 'book-1' },
      { portfolioId: 'book-2' },
    ])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => useBookSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.bookOptions).toContainEqual({
      value: '__ALL__',
      label: 'All Books',
    })
    expect(result.current.bookOptions).toContainEqual({
      value: 'book-1',
      label: 'book-1',
    })
    expect(result.current.bookOptions).toContainEqual({
      value: 'book-2',
      label: 'book-2',
    })
  })

  it('should not include All Books option when only one book exists', async () => {
    mockFetchBooks.mockResolvedValue([
      { portfolioId: 'book-1' },
    ])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => useBookSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.bookOptions).not.toContainEqual(
      expect.objectContaining({ value: '__ALL__' }),
    )
  })

  it('should set isAllSelected to true when All Books is selected', async () => {
    mockFetchBooks.mockResolvedValue([
      { portfolioId: 'book-1' },
      { portfolioId: 'book-2' },
    ])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => useBookSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.selectBook('__ALL__')
    })

    expect(result.current.isAllSelected).toBe(true)
    expect(result.current.selectedBookId).toBe('__ALL__')
  })

  it('should aggregate positions when All Books is selected', async () => {
    mockFetchBooks.mockResolvedValue([
      { portfolioId: 'book-1' },
      { portfolioId: 'book-2' },
    ])
    mockFetchPositions
      .mockResolvedValueOnce([]) // initial load for book-1
      .mockResolvedValueOnce([
        {
          bookId: 'book-1',
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          quantity: '100',
          averageCost: { amount: '150.00', currency: 'USD' },
          marketPrice: { amount: '155.00', currency: 'USD' },
          marketValue: { amount: '15500.00', currency: 'USD' },
          unrealizedPnl: { amount: '500.00', currency: 'USD' },
        },
      ])
      .mockResolvedValueOnce([
        {
          bookId: 'book-2',
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          quantity: '50',
          averageCost: { amount: '145.00', currency: 'USD' },
          marketPrice: { amount: '155.00', currency: 'USD' },
          marketValue: { amount: '7750.00', currency: 'USD' },
          unrealizedPnl: { amount: '500.00', currency: 'USD' },
        },
      ])

    const { result } = renderHook(() => useBookSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    await act(async () => {
      result.current.selectBook('__ALL__')
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.aggregatedPositions).toHaveLength(1)
    expect(result.current.aggregatedPositions[0].instrumentId).toBe('AAPL')
    expect(result.current.aggregatedPositions[0].quantity).toBe('150')
  })

  it('should return individual book IDs list when All Books selected', async () => {
    mockFetchBooks.mockResolvedValue([
      { portfolioId: 'book-1' },
      { portfolioId: 'book-2' },
    ])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => useBookSelector())

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    act(() => {
      result.current.selectBook('__ALL__')
    })

    expect(result.current.allBookIds).toEqual(['book-1', 'book-2'])
  })
})
