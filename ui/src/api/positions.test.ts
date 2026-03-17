import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchBooks, fetchPositions } from './positions'

describe('positions API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('fetchBooks', () => {
    it('returns parsed JSON on 200', async () => {
      const books = [{ bookId: 'book-1' }]
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(books),
      })

      const result = await fetchBooks()

      expect(result).toEqual(books)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/books')
    })

    it('throws on non-200 response', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchBooks()).rejects.toThrow(
        'Failed to fetch books: 500 Internal Server Error',
      )
    })
  })

  describe('fetchPositions', () => {
    it('returns parsed JSON on 200', async () => {
      const positions = [{ instrumentId: 'AAPL', quantity: '100' }]
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(positions),
      })

      const result = await fetchPositions('book-1')

      expect(result).toEqual(positions)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/books/book-1/positions',
      )
    })

    it('URL-encodes the bookId', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve([]),
      })

      await fetchPositions('book/special & id')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/books/book%2Fspecial%20%26%20id/positions',
      )
    })

    it('throws on non-200 response', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      await expect(fetchPositions('book-1')).rejects.toThrow(
        'Failed to fetch positions: 404 Not Found',
      )
    })
  })
})
