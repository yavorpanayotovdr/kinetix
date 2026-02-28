import { useEffect, useState } from 'react'

interface LastUpdatedIndicatorProps {
  timestamp: string | null
}

function formatRelative(timestamp: string): string {
  const diffMs = Date.now() - new Date(timestamp).getTime()
  const diffSeconds = Math.floor(diffMs / 1000)

  if (diffSeconds < 60) return 'just now'

  const diffMinutes = Math.floor(diffSeconds / 60)
  if (diffMinutes < 60) return `${diffMinutes} min ago`

  const diffHours = Math.floor(diffMinutes / 60)
  return `${diffHours} hours ago`
}

function stalenessClass(timestamp: string): string {
  const diffMs = Date.now() - new Date(timestamp).getTime()
  const diffMinutes = diffMs / 60_000

  if (diffMinutes >= 15) return 'text-red-600'
  if (diffMinutes >= 5) return 'text-amber-600'
  return 'text-slate-500'
}

function formatTime(timestamp: string): string {
  return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function LastUpdatedIndicator({ timestamp }: LastUpdatedIndicatorProps) {
  const [, setTick] = useState(0)

  useEffect(() => {
    if (!timestamp) return
    const id = setInterval(() => setTick((t) => t + 1), 30_000)
    return () => clearInterval(id)
  }, [timestamp])

  if (!timestamp) return null

  return (
    <span
      data-testid="last-updated"
      className={`text-xs ${stalenessClass(timestamp)}`}
    >
      Last refreshed: {formatTime(timestamp)} ({formatRelative(timestamp)})
    </span>
  )
}
