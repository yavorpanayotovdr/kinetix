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
      {bucket.jobIds.length > 0 && (
        <div className="mt-1 border-t border-slate-600 pt-1">
          {bucket.jobIds.slice(0, 5).map((id) => (
            <div key={id} className="font-mono text-slate-300">{id.slice(0, 8)}</div>
          ))}
          {bucket.jobIds.length > 5 && (
            <div className="text-slate-400">and {bucket.jobIds.length - 5} more</div>
          )}
        </div>
      )}
    </div>
  )
}
