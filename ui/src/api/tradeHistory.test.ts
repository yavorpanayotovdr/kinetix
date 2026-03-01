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
        portfolioId: 'port-1',
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

    const result = await fetchTradeHistory('port-1')

    expect(result).toEqual(trades)
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/portfolios/port-1/trades',
    )
  })

  it('URL-encodes the portfolioId', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    })

    await fetchTradeHistory('port/special & id')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/portfolios/port%2Fspecial%20%26%20id/trades',
    )
  })

  it('throws on non-200 response', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
    })

    await expect(fetchTradeHistory('port-1')).rejects.toThrow(
      'Failed to fetch trade history: 500 Internal Server Error',
    )
  })
})
