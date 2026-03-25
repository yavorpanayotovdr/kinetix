import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchIntradayVaRTimeline } from './intradayVaRTimeline'

describe('fetchIntradayVaRTimeline', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('calls the correct endpoint with from and to parameters', async () => {
    const mockFetch = vi.mocked(fetch)
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ bookId: 'book-1', varPoints: [], tradeAnnotations: [] }),
    } as Response)

    await fetchIntradayVaRTimeline('book-1', '2026-03-25T09:00:00Z', '2026-03-25T17:00:00Z')

    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/risk/var/book-1/intraday?from=2026-03-25T09%3A00%3A00Z&to=2026-03-25T17%3A00%3A00Z',
    )
  })

  it('returns the parsed response on success', async () => {
    const mockFetch = vi.mocked(fetch)
    const timeline = {
      bookId: 'book-1',
      varPoints: [
        {
          timestamp: '2026-03-25T09:30:00Z',
          varValue: 12500.0,
          expectedShortfall: 15000.0,
          delta: 0.65,
          gamma: null,
          vega: null,
        },
      ],
      tradeAnnotations: [
        {
          timestamp: '2026-03-25T09:15:00Z',
          instrumentId: 'AAPL',
          side: 'BUY',
          quantity: '100',
          tradeId: 'T001',
        },
      ],
    }
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => timeline,
    } as Response)

    const result = await fetchIntradayVaRTimeline('book-1', '2026-03-25T09:00:00Z', '2026-03-25T17:00:00Z')

    expect(result).toEqual(timeline)
  })

  it('throws an error when the response is not ok', async () => {
    const mockFetch = vi.mocked(fetch)
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => ({ error: 'missing from parameter' }),
    } as Response)

    await expect(
      fetchIntradayVaRTimeline('book-1', '', '2026-03-25T17:00:00Z'),
    ).rejects.toThrow('missing from parameter')
  })
})
