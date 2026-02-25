import type { TimeBucket } from '../utils/timeBuckets'
import { formatChartTime } from '../utils/format'

interface ChartTooltipProps {
  bucket: TimeBucket | null
  visible: boolean
  rangeDays: number
  barCenterX: number
}

export function ChartTooltip({ bucket, visible, rangeDays, barCenterX }: ChartTooltipProps) {
  if (!visible || !bucket) return null

  const total = bucket.completed + bucket.failed + bucket.running
  if (total === 0) return null

  const fromLabel = formatChartTime(bucket.from, rangeDays)
  const toLabel = formatChartTime(bucket.to, rangeDays)

  return (
    <div
      data-testid="chart-tooltip"
      className="absolute top-0 -translate-x-1/2 bg-slate-800 text-white text-xs rounded shadow-lg px-3 py-2 pointer-events-none"
      style={{ left: `${barCenterX}px` }}
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
