import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchVaR, triggerVaRCalculation, fetchPositionRisk } from './risk'

describe('risk API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const varResult = {
    bookId: 'book-1',
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

      const result = await fetchVaR('book-1')

      expect(result).toEqual(varResult)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/var/book-1')
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await fetchVaR('book-1')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchVaR('book-1')).rejects.toThrow(
        'Failed to fetch VaR: 500 Internal Server Error',
      )
    })

    it('URL-encodes the bookId', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      await fetchVaR('book/special & id')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/var/book%2Fspecial%20%26%20id',
      )
    })

    it('appends valuationDate query param when provided', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      await fetchVaR('book-1', '2025-03-10')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/var/book-1?valuationDate=2025-03-10',
      )
    })

    it('does not append valuationDate when null', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      await fetchVaR('book-1', null)

      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/var/book-1')
    })

    it('does not append valuationDate when undefined', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      await fetchVaR('book-1')

      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/var/book-1')
    })
  })

  describe('triggerVaRCalculation', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      const result = await triggerVaRCalculation('book-1')

      expect(result).toEqual(varResult)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/var/book-1', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requestedOutputs: ['VAR', 'EXPECTED_SHORTFALL', 'GREEKS', 'PV'] }),
      })
    })

    it('sends request body when provided', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(varResult),
      })

      await triggerVaRCalculation('book-1', {
        calculationType: 'MONTE_CARLO',
        confidenceLevel: 'CL_99',
      })

      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/var/book-1', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          calculationType: 'MONTE_CARLO',
          confidenceLevel: 'CL_99',
          requestedOutputs: ['VAR', 'EXPECTED_SHORTFALL', 'GREEKS', 'PV'],
        }),
      })
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await triggerVaRCalculation('book-1')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(triggerVaRCalculation('book-1')).rejects.toThrow(
        '500 Internal Server Error',
      )
    })
  })

  describe('fetchPositionRisk', () => {
    const positionRiskData = [
      {
        instrumentId: 'AAPL',
        assetClass: 'EQUITY',
        marketValue: '15500.00',
        delta: '1234.56',
        gamma: '45.67',
        vega: '89.01',
        varContribution: '800.00',
        esContribution: '1000.00',
        percentageOfTotal: '64.85',
      },
    ]

    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(positionRiskData),
      })

      const result = await fetchPositionRisk('book-1')

      expect(result).toEqual(positionRiskData)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/positions/book-1')
    })

    it('returns empty array on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await fetchPositionRisk('book-1')

      expect(result).toEqual([])
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchPositionRisk('book-1')).rejects.toThrow(
        'Failed to fetch position risk: 500 Internal Server Error',
      )
    })

    it('URL-encodes the bookId', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      })

      await fetchPositionRisk('book/special & id')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/positions/book%2Fspecial%20%26%20id',
      )
    })

    it('appends valuationDate query param when provided', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(positionRiskData),
      })

      await fetchPositionRisk('book-1', '2025-03-10')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/positions/book-1?valuationDate=2025-03-10',
      )
    })

    it('does not append valuationDate when null', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(positionRiskData),
      })

      await fetchPositionRisk('book-1', null)

      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/positions/book-1')
    })
  })

  describe('triggerVaRCalculation error handling', () => {
    it('attaches status and uses message from JSON error body on 503', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 503,
        statusText: 'Service Unavailable',
        json: () => Promise.resolve({ code: 'service_unavailable', message: 'Risk engine temporarily unavailable' }),
      })

      try {
        await triggerVaRCalculation('p1')
        expect.fail('should have thrown')
      } catch (e: unknown) {
        const err = e as Error & { status: number }
        expect(err.status).toBe(503)
        expect(err.message).toBe('Risk engine temporarily unavailable')
      }
    })

    it('attaches status and uses message from JSON error body on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.resolve({ code: 'internal_error', message: 'An unexpected error occurred' }),
      })

      try {
        await triggerVaRCalculation('p1')
        expect.fail('should have thrown')
      } catch (e: unknown) {
        const err = e as Error & { status: number }
        expect(err.status).toBe(500)
        expect(err.message).toBe('An unexpected error occurred')
      }
    })

    it('falls back to status text when body is not JSON', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 502,
        statusText: 'Bad Gateway',
        json: () => Promise.reject(new Error('not json')),
      })

      try {
        await triggerVaRCalculation('p1')
        expect.fail('should have thrown')
      } catch (e: unknown) {
        const err = e as Error & { status: number }
        expect(err.status).toBe(502)
        expect(err.message).toContain('Bad Gateway')
      }
    })
  })
})
