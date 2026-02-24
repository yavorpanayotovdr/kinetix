import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchSystemHealth } from './system'

describe('system API', () => {
  const mockFetch = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const sampleHealth = {
    status: 'UP',
    services: {
      gateway: { status: 'UP' },
      'position-service': { status: 'UP' },
      'price-service': { status: 'UP' },
      'risk-orchestrator': { status: 'UP' },
      'notification-service': { status: 'UP' },
      'rates-service': { status: 'UP' },
      'reference-data-service': { status: 'UP' },
      'volatility-service': { status: 'UP' },
      'correlation-service': { status: 'UP' },
    },
  }

  describe('fetchSystemHealth', () => {
    it('returns system health response', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(sampleHealth),
      })

      const result = await fetchSystemHealth()

      expect(result).toEqual(sampleHealth)
      expect(mockFetch).toHaveBeenCalledWith('/api/v1/system/health')
    })

    it('throws on 500', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
      })

      await expect(fetchSystemHealth()).rejects.toThrow(
        'Failed to fetch system health: 500 Internal Server Error',
      )
    })

    it('returns degraded status when a service is down', async () => {
      const degradedHealth = {
        status: 'DEGRADED',
        services: {
          gateway: { status: 'UP' },
          'position-service': { status: 'DOWN' },
          'price-service': { status: 'UP' },
          'risk-orchestrator': { status: 'UP' },
          'notification-service': { status: 'UP' },
          'rates-service': { status: 'UP' },
          'reference-data-service': { status: 'UP' },
          'volatility-service': { status: 'UP' },
          'correlation-service': { status: 'UP' },
        },
      }

      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(degradedHealth),
      })

      const result = await fetchSystemHealth()

      expect(result.status).toBe('DEGRADED')
      expect(result.services['position-service'].status).toBe('DOWN')
    })
  })
})
