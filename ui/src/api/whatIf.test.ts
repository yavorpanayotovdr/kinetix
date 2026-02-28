import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { runWhatIfAnalysis } from './whatIf'
import type { WhatIfRequestDto, WhatIfResponseDto } from '../types'

describe('whatIf API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const whatIfResponse: WhatIfResponseDto = {
    baseVaR: '100000.00',
    baseExpectedShortfall: '130000.00',
    baseGreeks: null,
    basePositionRisk: [],
    hypotheticalVaR: '85000.00',
    hypotheticalExpectedShortfall: '110000.00',
    hypotheticalGreeks: null,
    hypotheticalPositionRisk: [],
    varChange: '-15000.00',
    esChange: '-20000.00',
    calculatedAt: '2025-01-15T10:00:00Z',
  }

  const request: WhatIfRequestDto = {
    hypotheticalTrades: [
      {
        instrumentId: 'SPY',
        assetClass: 'EQUITY',
        side: 'BUY',
        quantity: '100',
        priceAmount: '450.00',
        priceCurrency: 'USD',
      },
    ],
  }

  describe('runWhatIfAnalysis', () => {
    it('sends POST with request body and returns result', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(whatIfResponse),
      })

      const result = await runWhatIfAnalysis('port-1', request)

      expect(result).toEqual(whatIfResponse)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/risk/what-if/port-1', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      })
    })

    it('URL-encodes the portfolioId', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(whatIfResponse),
      })

      await runWhatIfAnalysis('port/special & id', request)

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/what-if/port%2Fspecial%20%26%20id',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await runWhatIfAnalysis('port-1', request)

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(runWhatIfAnalysis('port-1', request)).rejects.toThrow(
        'Failed to run what-if analysis: 500 Internal Server Error',
      )
    })
  })
})
