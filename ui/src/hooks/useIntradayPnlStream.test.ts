import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PnlUpdateMessage } from '../types'
import { useIntradayPnlStream } from './useIntradayPnlStream'

class MockWebSocket {
  static OPEN = 1
  static instances: MockWebSocket[] = []

  readyState = MockWebSocket.OPEN
  url: string
  onopen: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  sentMessages: string[] = []

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }

  send(data: string) {
    this.sentMessages.push(data)
  }

  close() {
    this.readyState = 3
    this.onclose?.()
  }

  simulateOpen() {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.()
  }

  simulateMessage(data: object) {
    this.onmessage?.({ data: JSON.stringify(data) })
  }
}

const makePnlUpdate = (
  overrides: Partial<PnlUpdateMessage> = {},
): PnlUpdateMessage => ({
  type: 'pnl',
  bookId: 'book-1',
  snapshotAt: '2026-03-24T09:30:00Z',
  baseCurrency: 'USD',
  trigger: 'position_change',
  totalPnl: '1500.00',
  realisedPnl: '500.00',
  unrealisedPnl: '1000.00',
  deltaPnl: '1200.00',
  gammaPnl: '80.00',
  vegaPnl: '40.00',
  thetaPnl: '-15.00',
  rhoPnl: '7.00',
  unexplainedPnl: '188.00',
  highWaterMark: '1800.00',
  ...overrides,
})

describe('useIntradayPnlStream', () => {
  beforeEach(() => {
    MockWebSocket.instances = []
    vi.stubGlobal('WebSocket', MockWebSocket)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('connects and subscribes to the given bookId', () => {
    const { result } = renderHook(() =>
      useIntradayPnlStream('book-1'),
    )

    expect(MockWebSocket.instances).toHaveLength(1)
    expect(result.current.connected).toBe(false)

    act(() => {
      MockWebSocket.instances[0].simulateOpen()
    })

    expect(result.current.connected).toBe(true)
    const sent = MockWebSocket.instances[0].sentMessages[0]
    const msg = JSON.parse(sent)
    expect(msg.type).toBe('subscribe')
    expect(msg.bookId).toBe('book-1')
  })

  it('starts with empty snapshots', () => {
    const { result } = renderHook(() =>
      useIntradayPnlStream('book-1'),
    )

    expect(result.current.snapshots).toEqual([])
    expect(result.current.latest).toBeNull()
  })

  it('appends P&L updates to snapshots', () => {
    const { result } = renderHook(() =>
      useIntradayPnlStream('book-1'),
    )

    act(() => {
      MockWebSocket.instances[0].simulateOpen()
    })

    act(() => {
      MockWebSocket.instances[0].simulateMessage(makePnlUpdate({ totalPnl: '1000.00' }))
    })

    act(() => {
      MockWebSocket.instances[0].simulateMessage(makePnlUpdate({ totalPnl: '1500.00', snapshotAt: '2026-03-24T09:31:00Z' }))
    })

    expect(result.current.snapshots).toHaveLength(2)
    expect(result.current.snapshots[0].totalPnl).toBe('1000.00')
    expect(result.current.snapshots[1].totalPnl).toBe('1500.00')
  })

  it('updates latest to the most recent snapshot', () => {
    const { result } = renderHook(() =>
      useIntradayPnlStream('book-1'),
    )

    act(() => {
      MockWebSocket.instances[0].simulateOpen()
    })

    act(() => {
      MockWebSocket.instances[0].simulateMessage(makePnlUpdate({ totalPnl: '1000.00' }))
    })

    act(() => {
      MockWebSocket.instances[0].simulateMessage(makePnlUpdate({ totalPnl: '2000.00', snapshotAt: '2026-03-24T09:31:00Z' }))
    })

    expect(result.current.latest?.totalPnl).toBe('2000.00')
  })

  it('ignores messages with wrong type', () => {
    const { result } = renderHook(() =>
      useIntradayPnlStream('book-1'),
    )

    act(() => {
      MockWebSocket.instances[0].simulateOpen()
    })

    act(() => {
      MockWebSocket.instances[0].simulateMessage({ type: 'error', message: 'something' })
    })

    expect(result.current.snapshots).toHaveLength(0)
  })

  it('does not connect when bookId is null', () => {
    renderHook(() => useIntradayPnlStream(null))

    expect(MockWebSocket.instances).toHaveLength(0)
  })

  it('propagates missingFxRates from the WebSocket message to the snapshot', () => {
    const { result } = renderHook(() =>
      useIntradayPnlStream('book-1'),
    )

    act(() => {
      MockWebSocket.instances[0].simulateOpen()
    })

    act(() => {
      MockWebSocket.instances[0].simulateMessage(
        makePnlUpdate({ missingFxRates: ['USD/JPY', 'EUR/GBP'] }),
      )
    })

    expect(result.current.latest?.missingFxRates).toEqual(['USD/JPY', 'EUR/GBP'])
  })

  it('snapshot has undefined missingFxRates when not present in WebSocket message', () => {
    const { result } = renderHook(() =>
      useIntradayPnlStream('book-1'),
    )

    act(() => {
      MockWebSocket.instances[0].simulateOpen()
    })

    act(() => {
      MockWebSocket.instances[0].simulateMessage(makePnlUpdate())
    })

    expect(result.current.latest?.missingFxRates).toBeUndefined()
  })

  it('sets connected to false when WebSocket closes', () => {
    const { result } = renderHook(() =>
      useIntradayPnlStream('book-1'),
    )

    act(() => {
      MockWebSocket.instances[0].simulateOpen()
    })

    expect(result.current.connected).toBe(true)

    act(() => {
      MockWebSocket.instances[0].onclose?.()
    })

    expect(result.current.connected).toBe(false)
  })
})
