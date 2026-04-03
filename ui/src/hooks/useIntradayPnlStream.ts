import { useEffect, useRef, useState } from 'react'
import type { IntradayPnlSnapshotDto, PnlUpdateMessage } from '../types'

const MAX_SNAPSHOTS = 500

interface UsePnlStreamResult {
  snapshots: IntradayPnlSnapshotDto[]
  latest: IntradayPnlSnapshotDto | null
  connected: boolean
}

export function useIntradayPnlStream(
  bookId: string | null,
  wsUrl?: string,
): UsePnlStreamResult {
  const [snapshots, setSnapshots] = useState<IntradayPnlSnapshotDto[]>([])
  const [connected, setConnected] = useState(false)
  const wsRef = useRef<WebSocket | null>(null)
  const unmountedRef = useRef(false)

  useEffect(() => {
    if (!bookId) return

    unmountedRef.current = false

    const url =
      wsUrl ??
      `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/pnl`

    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onopen = () => {
      if (unmountedRef.current) return
      setConnected(true)
      ws.send(JSON.stringify({ type: 'subscribe', bookId }))
    }

    ws.onmessage = (event: { data: string }) => {
      if (unmountedRef.current) return
      try {
        const update = JSON.parse(event.data) as PnlUpdateMessage
        if (update.type !== 'pnl') return

        const snapshot: IntradayPnlSnapshotDto = {
          snapshotAt: update.snapshotAt,
          baseCurrency: update.baseCurrency,
          trigger: update.trigger,
          totalPnl: update.totalPnl,
          realisedPnl: update.realisedPnl,
          unrealisedPnl: update.unrealisedPnl,
          deltaPnl: update.deltaPnl,
          gammaPnl: update.gammaPnl,
          vegaPnl: update.vegaPnl,
          thetaPnl: update.thetaPnl,
          rhoPnl: update.rhoPnl,
          unexplainedPnl: update.unexplainedPnl,
          highWaterMark: update.highWaterMark,
          correlationId: update.correlationId,
          missingFxRates: update.missingFxRates,
        }

        setSnapshots((prev) => {
          const next = [...prev, snapshot]
          return next.length > MAX_SNAPSHOTS ? next.slice(-MAX_SNAPSHOTS) : next
        })
      } catch {
        // malformed message — ignore
      }
    }

    ws.onclose = () => {
      if (!unmountedRef.current) {
        setConnected(false)
      }
    }

    ws.onerror = () => {
      // onclose will also fire
    }

    return () => {
      unmountedRef.current = true
      ws.onclose = null
      ws.onerror = null
      if (ws.readyState === WebSocket.OPEN) {
        ws.close()
      }
    }
  }, [bookId, wsUrl])

  const latest = snapshots.length > 0 ? snapshots[snapshots.length - 1] : null

  return { snapshots, latest, connected }
}
