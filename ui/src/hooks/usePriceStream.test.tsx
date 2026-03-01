import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PositionDto, PriceUpdateMessage } from '../types'
import { applyPriceUpdate, usePriceStream } from './usePriceStream'

// --- Pure function tests ---

const makePosition = (overrides: Partial<PositionDto> = {}): PositionDto => ({
  portfolioId: 'port-1',
  instrumentId: 'AAPL',
  assetClass: 'EQUITY',
  quantity: '100',
  averageCost: { amount: '150.00', currency: 'USD' },
  marketPrice: { amount: '155.00', currency: 'USD' },
  marketValue: { amount: '15500.00', currency: 'USD' },
  unrealizedPnl: { amount: '500.00', currency: 'USD' },
  ...overrides,
})

const makePriceUpdate = (
  overrides: Partial<PriceUpdateMessage> = {},
): PriceUpdateMessage => ({
  type: 'price',
  instrumentId: 'AAPL',
  priceAmount: '160.00',
  priceCurrency: 'USD',
  timestamp: '2025-01-01T00:00:00Z',
  source: 'BLOOMBERG',
  ...overrides,
})

describe('applyPriceUpdate', () => {
  it('updates marketPrice, recalculates marketValue and unrealizedPnl', () => {
    const position = makePosition()
    const update = makePriceUpdate({ priceAmount: '160.00' })

    const result = applyPriceUpdate(position, update)

    expect(result.marketPrice).toEqual({ amount: '160.00', currency: 'USD' })
    // marketValue = 100 * 160.00 = 16000.00
    expect(result.marketValue).toEqual({ amount: '16000.00', currency: 'USD' })
    // unrealizedPnl = (160.00 - 150.00) * 100 = 1000.00
    expect(result.unrealizedPnl).toEqual({
      amount: '1000.00',
      currency: 'USD',
    })
  })

  it('handles short positions (negative quantity)', () => {
    const position = makePosition({
      quantity: '-50',
      averageCost: { amount: '150.00', currency: 'USD' },
    })
    const update = makePriceUpdate({ priceAmount: '160.00' })

    const result = applyPriceUpdate(position, update)

    // marketValue = -50 * 160 = -8000.00
    expect(result.marketValue).toEqual({ amount: '-8000.00', currency: 'USD' })
    // unrealizedPnl = (160 - 150) * -50 = -500.00
    expect(result.unrealizedPnl).toEqual({
      amount: '-500.00',
      currency: 'USD',
    })
  })

  it('handles negative P&L', () => {
    const position = makePosition()
    const update = makePriceUpdate({ priceAmount: '140.00' })

    const result = applyPriceUpdate(position, update)

    // unrealizedPnl = (140 - 150) * 100 = -1000.00
    expect(result.unrealizedPnl).toEqual({
      amount: '-1000.00',
      currency: 'USD',
    })
  })

  it('preserves unchanged fields', () => {
    const position = makePosition()
    const update = makePriceUpdate()

    const result = applyPriceUpdate(position, update)

    expect(result.portfolioId).toBe('port-1')
    expect(result.instrumentId).toBe('AAPL')
    expect(result.assetClass).toBe('EQUITY')
    expect(result.quantity).toBe('100')
    expect(result.averageCost).toEqual({ amount: '150.00', currency: 'USD' })
  })
})

// --- Hook tests ---

type MessageHandler = (event: { data: string }) => void

class MockWebSocket {
  static instances: MockWebSocket[] = []

  url: string
  onopen: (() => void) | null = null
  onmessage: MessageHandler | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  readyState = 0
  sent: string[] = []
  closed = false

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }

  send(data: string) {
    this.sent.push(data)
  }

  close() {
    this.closed = true
  }

  simulateOpen() {
    this.readyState = 1
    this.onopen?.()
  }

  simulateMessage(data: unknown) {
    this.onmessage?.({ data: JSON.stringify(data) })
  }

  simulateClose() {
    this.readyState = 3
    this.onclose?.()
  }

  simulateError() {
    this.onerror?.()
  }
}

describe('usePriceStream', () => {
  beforeEach(() => {
    MockWebSocket.instances = []
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('does not connect when positions are empty', () => {
    renderHook(() => usePriceStream([]))

    expect(MockWebSocket.instances).toHaveLength(0)
  })

  it('connects and subscribes on open', () => {
    const positions = [
      makePosition({ instrumentId: 'AAPL' }),
      makePosition({ instrumentId: 'GOOGL' }),
    ]

    renderHook(() => usePriceStream(positions, 'ws://localhost/ws'))

    expect(MockWebSocket.instances).toHaveLength(1)
    const ws = MockWebSocket.instances[0]
    expect(ws.url).toBe('ws://localhost/ws')

    act(() => {
      ws.simulateOpen()
    })

    expect(ws.sent).toHaveLength(1)
    const subscribeMsg = JSON.parse(ws.sent[0])
    expect(subscribeMsg.type).toBe('subscribe')
    expect(subscribeMsg.instrumentIds).toEqual(
      expect.arrayContaining(['AAPL', 'GOOGL']),
    )
  })

  it('updates positions on price message', () => {
    const positions = [makePosition()]

    const { result } = renderHook(() =>
      usePriceStream(positions, 'ws://localhost/ws'),
    )

    const ws = MockWebSocket.instances[0]

    act(() => {
      ws.simulateOpen()
    })

    act(() => {
      ws.simulateMessage(makePriceUpdate({ priceAmount: '170.00' }))
    })

    const updated = result.current.positions.find(
      (p) => p.instrumentId === 'AAPL',
    )!
    expect(updated.marketPrice.amount).toBe('170.00')
    expect(updated.marketValue.amount).toBe('17000.00')
  })

  it('tracks connected state', () => {
    const positions = [makePosition()]

    const { result } = renderHook(() =>
      usePriceStream(positions, 'ws://localhost/ws'),
    )

    expect(result.current.connected).toBe(false)

    const ws = MockWebSocket.instances[0]

    act(() => {
      ws.simulateOpen()
    })

    expect(result.current.connected).toBe(true)

    act(() => {
      ws.simulateClose()
    })

    expect(result.current.connected).toBe(false)
  })

  it('sends unsubscribe and closes on unmount', () => {
    const positions = [makePosition()]

    const { unmount } = renderHook(() =>
      usePriceStream(positions, 'ws://localhost/ws'),
    )

    const ws = MockWebSocket.instances[0]

    act(() => {
      ws.simulateOpen()
    })

    unmount()

    expect(ws.sent).toHaveLength(2)
    const unsubscribeMsg = JSON.parse(ws.sent[1])
    expect(unsubscribeMsg.type).toBe('unsubscribe')
    expect(ws.closed).toBe(true)
  })

  it('skips updates where currency does not match', () => {
    const positions = [makePosition()]

    const { result } = renderHook(() =>
      usePriceStream(positions, 'ws://localhost/ws'),
    )

    const ws = MockWebSocket.instances[0]

    act(() => {
      ws.simulateOpen()
    })

    act(() => {
      ws.simulateMessage(
        makePriceUpdate({ priceAmount: '170.00', priceCurrency: 'EUR' }),
      )
    })

    const pos = result.current.positions.find(
      (p) => p.instrumentId === 'AAPL',
    )!
    // Should not have been updated — currency mismatch
    expect(pos.marketPrice.amount).toBe('155.00')
  })

  it('deduplicates instrument IDs in subscribe message', () => {
    const positions = [
      makePosition({ instrumentId: 'AAPL' }),
      makePosition({ instrumentId: 'AAPL', portfolioId: 'port-2' }),
    ]

    renderHook(() => usePriceStream(positions, 'ws://localhost/ws'))

    const ws = MockWebSocket.instances[0]

    act(() => {
      ws.simulateOpen()
    })

    const subscribeMsg = JSON.parse(ws.sent[0])
    expect(subscribeMsg.instrumentIds).toEqual(['AAPL'])
  })

  describe('auto-reconnect', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('should attempt to reconnect on disconnect', () => {
      const positions = [makePosition()]

      renderHook(() => usePriceStream(positions, 'ws://localhost/ws'))

      const ws = MockWebSocket.instances[0]

      act(() => {
        ws.simulateOpen()
      })

      act(() => {
        ws.simulateClose()
      })

      // First reconnect after 1s
      act(() => {
        vi.advanceTimersByTime(1000)
      })

      expect(MockWebSocket.instances).toHaveLength(2)
    })

    it('should use exponential backoff', () => {
      const positions = [makePosition()]

      renderHook(() => usePriceStream(positions, 'ws://localhost/ws'))

      const ws1 = MockWebSocket.instances[0]

      act(() => {
        ws1.simulateOpen()
      })
      act(() => {
        ws1.simulateClose()
      })

      // 1st reconnect at 1s
      act(() => {
        vi.advanceTimersByTime(1000)
      })
      expect(MockWebSocket.instances).toHaveLength(2)

      const ws2 = MockWebSocket.instances[1]
      act(() => {
        ws2.simulateClose()
      })

      // 2nd reconnect at 2s
      act(() => {
        vi.advanceTimersByTime(1999)
      })
      expect(MockWebSocket.instances).toHaveLength(2)

      act(() => {
        vi.advanceTimersByTime(1)
      })
      expect(MockWebSocket.instances).toHaveLength(3)
    })

    it('should show reconnecting state', () => {
      const positions = [makePosition()]

      const { result } = renderHook(() =>
        usePriceStream(positions, 'ws://localhost/ws'),
      )

      const ws = MockWebSocket.instances[0]

      act(() => {
        ws.simulateOpen()
      })

      expect(result.current.reconnecting).toBe(false)

      act(() => {
        ws.simulateClose()
      })

      expect(result.current.reconnecting).toBe(true)
    })

    it('should cap backoff at 30 seconds', () => {
      const positions = [makePosition()]

      renderHook(() => usePriceStream(positions, 'ws://localhost/ws'))

      const ws1 = MockWebSocket.instances[0]
      act(() => {
        ws1.simulateOpen()
      })
      act(() => {
        ws1.simulateClose()
      })

      // Simulate several failed reconnections: 1s, 2s, 4s, 8s, 16s, 30s (capped)
      for (let i = 0; i < 5; i++) {
        act(() => {
          vi.advanceTimersByTime(30000)
        })
        const ws = MockWebSocket.instances[MockWebSocket.instances.length - 1]
        act(() => {
          ws.simulateClose()
        })
      }

      const countBefore = MockWebSocket.instances.length

      // Next backoff should be capped at 30s, not 64s
      act(() => {
        vi.advanceTimersByTime(30000)
      })

      expect(MockWebSocket.instances).toHaveLength(countBefore + 1)
    })

    it('should reset backoff on successful reconnection', () => {
      const positions = [makePosition()]

      const { result } = renderHook(() =>
        usePriceStream(positions, 'ws://localhost/ws'),
      )

      const ws1 = MockWebSocket.instances[0]
      act(() => {
        ws1.simulateOpen()
      })
      act(() => {
        ws1.simulateClose()
      })

      // Reconnect after 1s
      act(() => {
        vi.advanceTimersByTime(1000)
      })

      const ws2 = MockWebSocket.instances[1]
      act(() => {
        ws2.simulateOpen()
      })

      expect(result.current.reconnecting).toBe(false)
      expect(result.current.connected).toBe(true)

      // Disconnect again - backoff should be reset to 1s
      act(() => {
        ws2.simulateClose()
      })

      act(() => {
        vi.advanceTimersByTime(1000)
      })

      expect(MockWebSocket.instances).toHaveLength(3)
    })

    it('should stop reconnecting after 20 attempts', () => {
      const positions = [makePosition()]

      renderHook(() => usePriceStream(positions, 'ws://localhost/ws'))

      const ws1 = MockWebSocket.instances[0]
      act(() => {
        ws1.simulateOpen()
      })
      act(() => {
        ws1.simulateClose()
      })

      // Simulate 20 failed reconnections
      for (let i = 0; i < 20; i++) {
        act(() => {
          vi.advanceTimersByTime(30000)
        })
        const ws = MockWebSocket.instances[MockWebSocket.instances.length - 1]
        act(() => {
          ws.simulateClose()
        })
      }

      const countAfter20 = MockWebSocket.instances.length

      // 21st attempt should not happen
      act(() => {
        vi.advanceTimersByTime(60000)
      })

      expect(MockWebSocket.instances).toHaveLength(countAfter20)
    })

    it('should not reconnect on intentional unmount', () => {
      const positions = [makePosition()]

      const { unmount } = renderHook(() =>
        usePriceStream(positions, 'ws://localhost/ws'),
      )

      const ws = MockWebSocket.instances[0]
      act(() => {
        ws.simulateOpen()
      })

      unmount()

      act(() => {
        vi.advanceTimersByTime(5000)
      })

      // Should only have the original connection, no reconnect
      expect(MockWebSocket.instances).toHaveLength(1)
    })
  })
})
