import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchPortfolios, fetchPositions } from './positions'

describe('positions API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('fetchPortfolios', () => {
    it('returns parsed JSON on 200', async () => {
      const portfolios = [{ portfolioId: 'port-1' }]
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve(portfolios),
      })

      const result = await fetchPortfolios()

      expect(result).toEqual(portfolios)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/portfolios')
    })

    it('throws on non-200 response', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchPortfolios()).rejects.toThrow(
        'Failed to fetch portfolios: 500 Internal Server Error',
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

      const result = await fetchPositions('port-1')

      expect(result).toEqual(positions)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/portfolios/port-1/positions',
      )
    })

    it('URL-encodes the portfolioId', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve([]),
      })

      await fetchPositions('port/special & id')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/portfolios/port%2Fspecial%20%26%20id/positions',
      )
    })

    it('throws on non-200 response', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      await expect(fetchPositions('port-1')).rejects.toThrow(
        'Failed to fetch positions: 404 Not Found',
      )
    })
  })
})
