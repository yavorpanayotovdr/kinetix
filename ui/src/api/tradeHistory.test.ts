import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchTradeHistory } from './tradeHistory'

describe('tradeHistory API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns parsed JSON on 200', async () => {
    const trades = [
      {
        tradeId: 't-1',
        bookId: 'book-1',
        instrumentId: 'AAPL',
        assetClass: 'EQUITY',
        side: 'BUY',
        quantity: '100',
        price: { amount: '150.00', currency: 'USD' },
        tradedAt: '2025-01-15T10:00:00Z',
      },
    ]
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(trades),
    })

    const result = await fetchTradeHistory('book-1')

    expect(result).toEqual(trades)
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/books/book-1/trades',
    )
  })

  it('URL-encodes the bookId', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    })

    await fetchTradeHistory('book/special & id')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/books/book%2Fspecial%20%26%20id/trades',
    )
  })

  it('throws on non-200 response', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
    })

    await expect(fetchTradeHistory('book-1')).rejects.toThrow(
      'Failed to fetch trade history: 500 Internal Server Error',
    )
  })
})
