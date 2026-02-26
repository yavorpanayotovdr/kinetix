import { useLayoutEffect, useRef, useState } from 'react'
import type { TimeBucket } from '../utils/timeBuckets'
import { formatChartTime } from '../utils/format'
import { clampTooltipLeft } from '../utils/clampTooltipLeft'

interface ChartTooltipProps {
  bucket: TimeBucket | null
  visible: boolean
  rangeDays: number
  barCenterX: number
  containerWidth: number
}

export function ChartTooltip({ bucket, visible, rangeDays, barCenterX, containerWidth }: ChartTooltipProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [left, setLeft] = useState(barCenterX)

  useLayoutEffect(() => {
    if (!ref.current) return
    const tooltipWidth = ref.current.offsetWidth
    setLeft(clampTooltipLeft(barCenterX, tooltipWidth, containerWidth))
  }, [barCenterX, containerWidth])

  if (!visible || !bucket) return null

  if (bucket.started === 0 && bucket.completed + bucket.failed + bucket.running === 0) return null

  const fromLabel = formatChartTime(bucket.from, rangeDays)
  const toLabel = formatChartTime(bucket.to, rangeDays)

  return (
    <div
      ref={ref}
      data-testid="chart-tooltip"
      className="absolute top-0 bg-slate-800 text-white text-xs rounded shadow-lg px-3 py-2 pointer-events-none whitespace-nowrap"
      style={{ left: `${left}px` }}
    >
      <div className="font-medium mb-1">{fromLabel} – {toLabel}</div>
      <div className="flex gap-3">
        <span className="text-amber-400">Started: {bucket.started}</span>
        <span className="text-green-400">Completed: {bucket.completed}</span>
        <span className="text-red-400">Failed: {bucket.failed}</span>
        <span className="text-indigo-400">Running: {bucket.running}</span>
      </div>
    </div>
  )
}
