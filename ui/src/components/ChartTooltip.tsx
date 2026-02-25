import type { TimeBucket } from '../utils/timeBuckets'
import { formatChartTime } from '../utils/format'

interface ChartTooltipProps {
  bucket: TimeBucket | null
  x: number
  y: number
  visible: boolean
  rangeDays: number
}

export function ChartTooltip({ bucket, x, y, visible, rangeDays }: ChartTooltipProps) {
  if (!visible || !bucket) return null

  const fromLabel = formatChartTime(bucket.from, rangeDays)
  const toLabel = formatChartTime(bucket.to, rangeDays)

  return (
    <div
      data-testid="chart-tooltip"
      className="absolute pointer-events-none bg-slate-800 text-white text-xs rounded shadow-lg px-3 py-2 z-10"
      style={{ left: x, top: y }}
    >
      <div className="font-medium mb-1">{fromLabel} – {toLabel}</div>
      <div className="flex gap-3">
        <span className="text-green-400">Completed: {bucket.completed}</span>
        <span className="text-red-400">Failed: {bucket.failed}</span>
        <span className="text-indigo-400">Running: {bucket.running}</span>
      </div>
    </div>
  )
}
