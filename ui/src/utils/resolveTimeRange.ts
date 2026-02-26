import type { TimeRange } from '../types'

export const SLIDING_DURATIONS: Record<string, number> = {
  'Last 1h': 60 * 60 * 1000,
  'Last 24h': 24 * 60 * 60 * 1000,
  'Last 7d': 7 * 24 * 60 * 60 * 1000,
}

export function resolveTimeRange(range: TimeRange): { from: string; to: string } {
  const duration = SLIDING_DURATIONS[range.label]
  if (duration) {
    const now = new Date()
    return { from: new Date(now.getTime() - duration).toISOString(), to: now.toISOString() }
  }
  if (range.label === 'Today') {
    const now = new Date()
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    return { from: start.toISOString(), to: now.toISOString() }
  }
  return { from: range.from, to: range.to }
}
