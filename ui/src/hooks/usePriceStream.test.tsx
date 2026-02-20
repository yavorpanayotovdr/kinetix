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
})
