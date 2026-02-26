import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchVaR, triggerVaRCalculation } from './risk'

describe('risk API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const varResult = {
    portfolioId: 'port-1',
    calculationType: 'HISTORICAL',
    confidenceLevel: 'CL_95',
    varValue: '1234567.89',
    expectedShortfall: '1567890.12',
    componentBreakdown: [
      { assetClass: 'EQUITY', varContribution: '800000.00', percentageOfTotal: '64.85' },
    ],
    calculatedAt: '2025-01-15T10:30:00Z',
  }

  describe('fetchVaR', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      const result = await fetchVaR('port-1')

      expect(result).toEqual(varResult)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/var/port-1')
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await fetchVaR('port-1')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchVaR('port-1')).rejects.toThrow(
        'Failed to fetch VaR: 500 Internal Server Error',
      )
    })

    it('URL-encodes the portfolioId', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      await fetchVaR('port/special & id')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/var/port%2Fspecial%20%26%20id',
      )
    })
  })

  describe('triggerVaRCalculation', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      const result = await triggerVaRCalculation('port-1')

      expect(result).toEqual(varResult)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/var/port-1', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requestedOutputs: ['VAR', 'EXPECTED_SHORTFALL', 'GREEKS'] }),
      })
    })

    it('sends request body when provided', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      await triggerVaRCalculation('port-1', {
        calculationType: 'MONTE_CARLO',
        confidenceLevel: 'CL_99',
      })

      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/var/port-1', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          calculationType: 'MONTE_CARLO',
          confidenceLevel: 'CL_99',
          requestedOutputs: ['VAR', 'EXPECTED_SHORTFALL', 'GREEKS'],
        }),
      })
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await triggerVaRCalculation('port-1')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(triggerVaRCalculation('port-1')).rejects.toThrow(
        'Failed to trigger VaR calculation: 500 Internal Server Error',
      )
    })
  })
})
