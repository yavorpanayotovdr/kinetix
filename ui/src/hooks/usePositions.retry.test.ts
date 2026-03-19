import { renderHook, waitFor, act } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePositions } from './usePositions'

vi.mock('../api/positions', () => ({
  fetchBooks: vi.fn(),
  fetchPositions: vi.fn(),
}))

import { fetchBooks, fetchPositions } from '../api/positions'

const mockFetchBooks = vi.mocked(fetchBooks)
const mockFetchPositions = vi.mocked(fetchPositions)

describe('usePositions — retryInitialLoad', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('retryInitialLoad function is exposed on the hook result', async () => {
    mockFetchBooks.mockResolvedValue([{ bookId: 'book-1' }])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => usePositions())

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(typeof result.current.retryInitialLoad).toBe('function')
  })

  it('retryInitialLoad re-fetches books and positions after an initial failure', async () => {
    // First call fails
    mockFetchBooks.mockRejectedValueOnce(new Error('Network error'))
    // Retry succeeds
    mockFetchBooks.mockResolvedValue([{ bookId: 'book-1' }])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => usePositions())

    await waitFor(() => expect(result.current.error).toBe('Network error'))
    expect(result.current.loading).toBe(false)

    act(() => {
      result.current.retryInitialLoad()
    })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBeNull()
    expect(result.current.books).toEqual(['book-1'])
  })

  it('retryInitialLoad clears the error before re-fetching', async () => {
    mockFetchBooks.mockRejectedValueOnce(new Error('Server down'))
    mockFetchBooks.mockResolvedValue([{ bookId: 'book-1' }])
    mockFetchPositions.mockResolvedValue([])

    const { result } = renderHook(() => usePositions())

    await waitFor(() => expect(result.current.error).toBe('Server down'))

    act(() => {
      result.current.retryInitialLoad()
    })

    // Error should be cleared immediately on retry
    expect(result.current.error).toBeNull()
    expect(result.current.loading).toBe(true)
  })
})
