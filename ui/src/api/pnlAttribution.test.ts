import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchPnlAttribution } from './pnlAttribution'
import type { PnlAttributionDto } from '../types'

describe('pnlAttribution API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const pnlAttributionData: PnlAttributionDto = {
    portfolioId: 'port-1',
    date: '2025-01-15',
    totalPnl: '15000.00',
    deltaPnl: '8000.00',
    gammaPnl: '2500.00',
    vegaPnl: '3000.00',
    thetaPnl: '-1500.00',
    rhoPnl: '500.00',
    unexplainedPnl: '2500.00',
    positionAttributions: [
      {
        instrumentId: 'AAPL',
        assetClass: 'EQUITY',
        totalPnl: '8000.00',
        deltaPnl: '5000.00',
        gammaPnl: '1200.00',
        vegaPnl: '1500.00',
        thetaPnl: '-800.00',
        rhoPnl: '300.00',
        unexplainedPnl: '800.00',
      },
    ],
    calculatedAt: '2025-01-15T10:30:00Z',
  }

  describe('fetchPnlAttribution', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(pnlAttributionData),
      })

      const result = await fetchPnlAttribution('port-1')

      expect(result).toEqual(pnlAttributionData)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/pnl-attribution/port-1',
      )
    })

    it('passes date query parameter when provided', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(pnlAttributionData),
      })

      await fetchPnlAttribution('port-1', '2025-01-15')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/pnl-attribution/port-1?date=2025-01-15',
      )
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await fetchPnlAttribution('port-1')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchPnlAttribution('port-1')).rejects.toThrow(
        'Failed to fetch P&L attribution: 500 Internal Server Error',
      )
    })

    it('URL-encodes the portfolioId', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(pnlAttributionData),
      })

      await fetchPnlAttribution('port/special & id')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/pnl-attribution/port%2Fspecial%20%26%20id',
      )
    })
  })
})
