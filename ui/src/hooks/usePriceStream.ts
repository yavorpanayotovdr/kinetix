import { useEffect, useRef, useState } from 'react'
import type { ClientMessage, PositionDto, PriceUpdateMessage } from '../types'

export function applyPriceUpdate(
  position: PositionDto,
  update: PriceUpdateMessage,
): PositionDto {
  const quantity = Number(position.quantity)
  const newPrice = Number(update.priceAmount)
  const avgCost = Number(position.averageCost.amount)

  const marketValue = quantity * newPrice
  const unrealizedPnl = (newPrice - avgCost) * quantity

  return {
    ...position,
    marketPrice: { amount: update.priceAmount, currency: update.priceCurrency },
    marketValue: {
      amount: marketValue.toFixed(2),
      currency: update.priceCurrency,
    },
    unrealizedPnl: {
      amount: unrealizedPnl.toFixed(2),
      currency: update.priceCurrency,
    },
  }
}

const MAX_RECONNECT_ATTEMPTS = 20
const MAX_BACKOFF_MS = 30000
const BASE_BACKOFF_MS = 1000

interface UsePriceStreamResult {
  positions: PositionDto[]
  connected: boolean
  reconnecting: boolean
}

export function usePriceStream(
  initialPositions: PositionDto[],
  wsUrl?: string,
): UsePriceStreamResult {
  const [positions, setPositions] = useState<PositionDto[]>(initialPositions)
  const [connected, setConnected] = useState(false)
  const [reconnecting, setReconnecting] = useState(false)
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const attemptRef = useRef(0)
  const unmountedRef = useRef(false)

  useEffect(() => {
    setPositions(initialPositions)
  }, [initialPositions])

  const connectRef = useRef<() => void>(() => {})

  useEffect(() => {
    connectRef.current = () => {
      if (initialPositions.length === 0) return

      const url =
        wsUrl ??
        `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/prices`
      const ws = new WebSocket(url)
      wsRef.current = ws

      const instrumentIds = [
        ...new Set(initialPositions.map((p) => p.instrumentId)),
      ]

      ws.onopen = () => {
        setConnected(true)
        setReconnecting(false)
        attemptRef.current = 0
        const subscribeMsg: ClientMessage = {
          type: 'subscribe',
          instrumentIds,
        }
        ws.send(JSON.stringify(subscribeMsg))
      }

      ws.onmessage = (event: { data: string }) => {
        const update = JSON.parse(event.data) as PriceUpdateMessage
        if (update.type !== 'price') return

        setPositions((prev) =>
          prev.map((pos) => {
            if (pos.instrumentId !== update.instrumentId) return pos
            if (pos.marketPrice.currency !== update.priceCurrency) return pos
            return applyPriceUpdate(pos, update)
          }),
        )
      }

      const scheduleReconnect = () => {
        if (unmountedRef.current) return
        if (attemptRef.current >= MAX_RECONNECT_ATTEMPTS) return

        setConnected(false)
        setReconnecting(true)

        const backoff = Math.min(
          BASE_BACKOFF_MS * Math.pow(2, attemptRef.current),
          MAX_BACKOFF_MS,
        )
        attemptRef.current += 1

        reconnectTimerRef.current = setTimeout(() => {
          if (!unmountedRef.current) {
            connectRef.current()
          }
        }, backoff)
      }

      ws.onclose = () => {
        setConnected(false)
        scheduleReconnect()
      }

      ws.onerror = () => {
        // onclose will also fire after onerror
      }
    }

    unmountedRef.current = false
    connectRef.current()

    return () => {
      unmountedRef.current = true
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current)
        reconnectTimerRef.current = null
      }
      const ws = wsRef.current
      if (ws) {
        const instrumentIds = [
          ...new Set(initialPositions.map((p) => p.instrumentId)),
        ]
        ws.onclose = null
        ws.onerror = null
        if (ws.readyState === 1) {
          const unsubscribeMsg: ClientMessage = {
            type: 'unsubscribe',
            instrumentIds,
          }
          ws.send(JSON.stringify(unsubscribeMsg))
        }
        ws.close()
      }
    }
  }, [initialPositions, wsUrl])

  return { positions, connected, reconnecting }
}
