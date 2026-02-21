import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchFrtb, generateReport } from './regulatory'

describe('regulatory API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const frtbResult = {
    portfolioId: 'port-1',
    sbmCharges: [
      { riskClass: 'EQUITY', deltaCharge: '40000.00', vegaCharge: '30000.00', curvatureCharge: '4000.00', totalCharge: '74000.00' },
    ],
    totalSbmCharge: '76612.50',
    grossJtd: '5400.00',
    hedgeBenefit: '0.00',
    netDrc: '5400.00',
    exoticNotional: '400000.00',
    otherNotional: '1700000.00',
    totalRrao: '5700.00',
    totalCapitalCharge: '87712.50',
    calculatedAt: '2025-01-15T10:00:00Z',
  }

  const reportResult = {
    portfolioId: 'port-1',
    format: 'CSV',
    content: 'Component,Risk Class,Delta Charge\nSbM,EQUITY,40000.00',
    generatedAt: '2025-01-15T10:00:00Z',
  }

  describe('fetchFrtb', () => {
    it('returns parsed result', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(frtbResult),
      })

      const result = await fetchFrtb('port-1')

      expect(result).toEqual(frtbResult)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/regulatory/frtb/port-1', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: '{}',
      })
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await fetchFrtb('port-1')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchFrtb('port-1')).rejects.toThrow(
        'Failed to fetch FRTB: 500 Internal Server Error',
      )
    })
  })

  describe('generateReport', () => {
    it('sends POST with format', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(reportResult),
      })

      const result = await generateReport('port-1', 'CSV')

      expect(result).toEqual(reportResult)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/regulatory/report/port-1', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ format: 'CSV' }),
      })
    })

    it('returns null on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await generateReport('port-1', 'CSV')

      expect(result).toBeNull()
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(generateReport('port-1', 'CSV')).rejects.toThrow(
        'Failed to generate report: 500 Internal Server Error',
      )
    })
  })
})
