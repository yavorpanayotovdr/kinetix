import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  fetchSodBaselineStatus,
  createSodSnapshot,
  resetSodBaseline,
  computePnlAttribution,
} from './sodSnapshot'
import type { SodBaselineStatusDto, PnlAttributionDto } from '../types'

describe('sodSnapshot API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const statusData: SodBaselineStatusDto = {
    exists: true,
    baselineDate: '2025-01-15',
    snapshotType: 'MANUAL',
    createdAt: '2025-01-15T08:00:00Z',
    sourceJobId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    calculationType: 'PARAMETRIC',
  }

  describe('fetchSodBaselineStatus', () => {
    it('returns parsed JSON on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(statusData),
      })

      const result = await fetchSodBaselineStatus('port-1')

      expect(result).toEqual(statusData)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/sod-snapshot/port-1/status',
      )
    })

    it('returns exists false on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
      })

      const result = await fetchSodBaselineStatus('port-1')

      expect(result).toEqual({
        exists: false,
        baselineDate: null,
        snapshotType: null,
        createdAt: null,
        sourceJobId: null,
        calculationType: null,
      })
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchSodBaselineStatus('port-1')).rejects.toThrow(
        'Failed to fetch SOD baseline status: 500 Internal Server Error',
      )
    })

    it('URL-encodes the portfolioId', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(statusData),
      })

      await fetchSodBaselineStatus('port/special & id')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/sod-snapshot/port%2Fspecial%20%26%20id/status',
      )
    })
  })

  describe('createSodSnapshot', () => {
    it('sends POST and returns status on 201', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 201,
        json: () => Promise.resolve(statusData),
      })

      const result = await createSodSnapshot('port-1')

      expect(result).toEqual(statusData)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/sod-snapshot/port-1',
        { method: 'POST' },
      )
    })

    it('throws with server error message when available', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 422,
        statusText: 'Unprocessable Entity',
        json: () => Promise.resolve({ error: 'no_valuation_data', message: 'No valuation data available' }),
      })

      await expect(createSodSnapshot('port-1')).rejects.toThrow(
        'No valuation data available',
      )
    })

    it('throws with fallback message when body has no message', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.reject(new Error('not json')),
      })

      await expect(createSodSnapshot('port-1')).rejects.toThrow(
        'Failed to create SOD snapshot: 500 Internal Server Error',
      )
    })
  })

  describe('resetSodBaseline', () => {
    it('sends DELETE and succeeds on 204', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 204,
      })

      await resetSodBaseline('port-1')

      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/sod-snapshot/port-1',
        { method: 'DELETE' },
      )
    })

    it('throws on error', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(resetSodBaseline('port-1')).rejects.toThrow(
        'Failed to reset SOD baseline',
      )
    })
  })

  describe('computePnlAttribution', () => {
    const pnlData: PnlAttributionDto = {
      portfolioId: 'port-1',
      date: '2025-01-15',
      totalPnl: '15000.00',
      deltaPnl: '8000.00',
      gammaPnl: '2500.00',
      vegaPnl: '3000.00',
      thetaPnl: '-1500.00',
      rhoPnl: '500.00',
      unexplainedPnl: '2500.00',
      positionAttributions: [],
      calculatedAt: '2025-01-15T10:30:00Z',
    }

    it('sends POST and returns attribution on 200', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(pnlData),
      })

      const result = await computePnlAttribution('port-1')

      expect(result).toEqual(pnlData)
      expect(mockFetch).toHaveBeenCalledWith(
        '/api/v1/risk/pnl-attribution/port-1/compute',
        { method: 'POST' },
      )
    })

    it('throws specific message on 412 (no baseline)', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 412,
        statusText: 'Precondition Failed',
      })

      await expect(computePnlAttribution('port-1')).rejects.toThrow(
        'No SOD baseline exists. Set a baseline first.',
      )
    })

    it('throws on other errors', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(computePnlAttribution('port-1')).rejects.toThrow(
        'Failed to compute P&L attribution',
      )
    })
  })
})
