import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchAlerts } from '../api/notifications'
import type { AlertEventDto } from '../types'

const MAX_RECONNECT_ATTEMPTS = 20
const MAX_BACKOFF_MS = 30000
const BASE_BACKOFF_MS = 1000
const POLLING_FALLBACK_MS = 5 * 60_000
const BURST_INTERVAL_MS = 600
const BURST_THRESHOLD = 3
const BURST_WINDOW_MS = 2000

interface AlertWebSocketMessage {
  type: 'alert'
  eventType: string
  alert: AlertEventDto
}

interface UseAlertStreamResult {
  alerts: AlertEventDto[]
  connected: boolean
  reconnecting: boolean
  acknowledgeLocal: (alertId: string, status: string) => void
}

export function useAlertStream(wsUrl?: string): UseAlertStreamResult {
  const [alerts, setAlerts] = useState<AlertEventDto[]>([])
  const [connected, setConnected] = useState(false)
  const [reconnecting, setReconnecting] = useState(false)
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const pollingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const attemptRef = useRef(0)
  const unmountedRef = useRef(false)

  // Burst queue state
  const burstQueueRef = useRef<AlertEventDto[]>([])
  const burstTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const recentTimestampsRef = useRef<number[]>([])
  const drainRef = useRef<() => void>(() => {})

  const addAlert = useCallback((alert: AlertEventDto) => {
    setAlerts((prev) => {
      const exists = prev.some((a) => a.id === alert.id)
      if (exists) {
        return prev.map((a) => (a.id === alert.id ? alert : a))
      }
      return [alert, ...prev]
    })
  }, [])

  // Set up drain function via ref to avoid circular dependency
  useEffect(() => {
    drainRef.current = () => {
      const queue = burstQueueRef.current
      if (queue.length === 0) {
        burstTimerRef.current = null
        return
      }
      const next = queue.shift()!
      addAlert(next)
      if (queue.length > 0) {
        burstTimerRef.current = setTimeout(() => drainRef.current(), BURST_INTERVAL_MS)
      } else {
        burstTimerRef.current = null
      }
    }
  }, [addAlert])

  const handleIncoming = useCallback(
    (alert: AlertEventDto) => {
      const now = Date.now()
      recentTimestampsRef.current = recentTimestampsRef.current.filter(
        (t) => now - t < BURST_WINDOW_MS,
      )
      recentTimestampsRef.current.push(now)

      if (recentTimestampsRef.current.length > BURST_THRESHOLD) {
        burstQueueRef.current.push(alert)
        if (!burstTimerRef.current) {
          burstTimerRef.current = setTimeout(() => drainRef.current(), BURST_INTERVAL_MS)
        }
      } else {
        addAlert(alert)
      }
    },
    [addAlert],
  )

  const acknowledgeLocal = useCallback((alertId: string, status: string) => {
    setAlerts((prev) =>
      prev.map((a) => (a.id === alertId ? { ...a, status } : a)),
    )
  }, [])

  const loadAlerts = useCallback(async () => {
    try {
      const data = await fetchAlerts(50)
      setAlerts(data)
    } catch {
      // silently ignore
    }
  }, [])

  const connectRef = useRef<() => void>(() => {})

  useEffect(() => {
    connectRef.current = () => {
      const url =
        wsUrl ??
        `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/alerts`
      const ws = new WebSocket(url)
      wsRef.current = ws

      ws.onopen = () => {
        setConnected(true)
        setReconnecting(false)
        attemptRef.current = 0

        // Stop polling fallback
        if (pollingTimerRef.current) {
          clearInterval(pollingTimerRef.current)
          pollingTimerRef.current = null
        }

        // Catch up on missed alerts
        loadAlerts()
      }

      ws.onmessage = (event: { data: string }) => {
        try {
          const msg = JSON.parse(event.data) as AlertWebSocketMessage
          if (msg.type !== 'alert') return
          handleIncoming(msg.alert)
        } catch {
          // ignore malformed messages
        }
      }

      const scheduleReconnect = () => {
        if (unmountedRef.current) return
        if (attemptRef.current >= MAX_RECONNECT_ATTEMPTS) return

        setConnected(false)
        setReconnecting(true)

        // Start polling fallback while disconnected
        if (!pollingTimerRef.current) {
          pollingTimerRef.current = setInterval(loadAlerts, POLLING_FALLBACK_MS)
        }

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
        // onclose also fires after onerror
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
      if (pollingTimerRef.current) {
        clearInterval(pollingTimerRef.current)
        pollingTimerRef.current = null
      }
      if (burstTimerRef.current) {
        clearTimeout(burstTimerRef.current)
        burstTimerRef.current = null
      }
      const ws = wsRef.current
      if (ws) {
        ws.onclose = null
        ws.onerror = null
        ws.close()
      }
    }
  }, [wsUrl, loadAlerts, handleIncoming])

  return { alerts, connected, reconnecting, acknowledgeLocal }
}
