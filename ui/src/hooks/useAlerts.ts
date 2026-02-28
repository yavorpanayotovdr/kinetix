import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchAlerts } from '../api/notifications'
import type { AlertEventDto } from '../types'

export function useAlerts() {
  const [alerts, setAlerts] = useState<AlertEventDto[]>([])
  const dismissedIds = useRef<Set<string>>(new Set())

  const load = useCallback(async () => {
    try {
      const data = await fetchAlerts(5)
      setAlerts(data.filter((a) => !dismissedIds.current.has(a.id)))
    } catch {
      // silently ignore fetch errors
    }
  }, [])

  const dismissAlert = useCallback((id: string) => {
    dismissedIds.current.add(id)
    setAlerts((prev) => prev.filter((a) => a.id !== id))
  }, [])

  useEffect(() => {
    load()
    const interval = setInterval(load, 30_000)
    return () => clearInterval(interval)
  }, [load])

  return { alerts, dismissAlert }
}
