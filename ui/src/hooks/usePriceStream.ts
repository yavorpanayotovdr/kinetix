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

interface UsePriceStreamResult {
  positions: PositionDto[]
  connected: boolean
}

export function usePriceStream(
  initialPositions: PositionDto[],
  wsUrl?: string,
): UsePriceStreamResult {
  const [positions, setPositions] = useState<PositionDto[]>(initialPositions)
  const [connected, setConnected] = useState(false)
  const wsRef = useRef<WebSocket | null>(null)

  useEffect(() => {
    setPositions(initialPositions)
  }, [initialPositions])

  useEffect(() => {
    if (initialPositions.length === 0) return

    const url =
      wsUrl ??
      `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/market-data`
    const ws = new WebSocket(url)
    wsRef.current = ws

    const instrumentIds = [
      ...new Set(initialPositions.map((p) => p.instrumentId)),
    ]

    ws.onopen = () => {
      setConnected(true)
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

    ws.onclose = () => {
      setConnected(false)
    }

    return () => {
      const unsubscribeMsg: ClientMessage = {
        type: 'unsubscribe',
        instrumentIds,
      }
      ws.send(JSON.stringify(unsubscribeMsg))
      ws.close()
    }
  }, [initialPositions, wsUrl])

  return { positions, connected }
}
