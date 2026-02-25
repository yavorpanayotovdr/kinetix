import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { RotateCcw } from 'lucide-react'
import type { TimeBucket } from '../utils/timeBuckets'
import type { TimeRange } from '../types'
import { formatChartTime } from '../utils/format'
import { useBrushSelection } from '../hooks/useBrushSelection'
import { ChartTooltip } from './ChartTooltip'

interface JobTimechartProps {
  buckets: TimeBucket[]
  timeRange: TimeRange
  onZoom: (range: TimeRange) => void
  zoomDepth: number
  onResetZoom: () => void
}

const PADDING = { top: 24, right: 16, bottom: 28, left: 40 }
const CHART_HEIGHT = 180
const BAR_GAP = 1
const DEFAULT_WIDTH = 600

const STATUS_COLORS = {
  started: '#f59e0b',   // amber-500
  completed: '#22c55e', // green-500
  failed: '#ef4444',    // red-500
  running: '#6366f1',   // indigo-500
}

export function JobTimechart({ buckets, timeRange, onZoom, zoomDepth, onResetZoom }: JobTimechartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [containerWidth, setContainerWidth] = useState(DEFAULT_WIDTH)
  const [tooltip, setTooltip] = useState<{ bucket: TimeBucket; barCenterX: number } | null>(null)

  useEffect(() => {
    const el = containerRef.current
    if (!el) return

    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) {
        setContainerWidth(entry.contentRect.width)
      }
    })
    observer.observe(el)
    setContainerWidth(el.clientWidth)

    return () => observer.disconnect()
  }, [])

  const rangeMs = new Date(timeRange.to).getTime() - new Date(timeRange.from).getTime()
  const rangeDays = rangeMs / (24 * 60 * 60 * 1000)
  const plotWidth = containerWidth - PADDING.left - PADDING.right
  const plotHeight = CHART_HEIGHT - PADDING.top - PADDING.bottom

  const maxCount = useMemo(() => {
    let max = 0
    for (const b of buckets) {
      const total = b.started + b.completed + b.failed + b.running
      if (total > max) max = total
    }
    return Math.max(max, 1)
  }, [buckets])

  const handleBrushEnd = useCallback(
    (startX: number, endX: number) => {
      const fromMs = new Date(timeRange.from).getTime()
      const toMs = new Date(timeRange.to).getTime()

      const leftPct = (startX - PADDING.left) / plotWidth
      const rightPct = (endX - PADDING.left) / plotWidth

      const zoomFrom = new Date(fromMs + leftPct * (toMs - fromMs))
      const zoomTo = new Date(fromMs + rightPct * (toMs - fromMs))

      onZoom({
        from: zoomFrom.toISOString(),
        to: zoomTo.toISOString(),
        label: 'Custom',
      })
    },
    [timeRange, onZoom, plotWidth],
  )

  const handleClick = useCallback(
    (x: number) => {
      if (buckets.length === 0) return

      const bw = plotWidth / buckets.length
      const index = Math.floor((x - PADDING.left) / bw)
      if (index < 0 || index >= buckets.length) return

      const bucket = buckets[index]
      onZoom({
        from: bucket.from.toISOString(),
        to: bucket.to.toISOString(),
        label: 'Custom',
      })
    },
    [buckets, plotWidth, onZoom],
  )

  const { brush, handlers } = useBrushSelection({ onBrushEnd: handleBrushEnd, onClick: handleClick })

  const barWidth = buckets.length > 0 ? plotWidth / buckets.length : 0

  const xLabels = useMemo(() => {
    const count = Math.min(6, buckets.length)
    if (count === 0) return []
    const step = Math.max(1, Math.floor(buckets.length / count))
    const labels: { x: number; text: string }[] = []
    const bw = buckets.length > 0 ? plotWidth / buckets.length : 0

    for (let i = 0; i < buckets.length; i += step) {
      labels.push({
        x: PADDING.left + i * bw + bw / 2,
        text: formatChartTime(buckets[i].from, rangeDays),
      })
    }
    return labels
  }, [buckets, rangeDays, plotWidth])

  const yGridLines = useMemo(() => {
    const lines: number[] = []
    const step = maxCount <= 5 ? 1 : Math.ceil(maxCount / 4)
    for (let v = step; v <= maxCount; v += step) {
      lines.push(v)
    }
    return lines
  }, [maxCount])

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<SVGSVGElement>) => {
      handlers.onMouseMove(e)

      const el = containerRef.current
      if (buckets.length === 0 || !el) {
        setTooltip(null)
        return
      }

      const rect = el.getBoundingClientRect()
      const mouseX = e.clientX - rect.left
      const pw = rect.width - PADDING.left - PADDING.right
      const bw = pw / buckets.length
      const index = Math.floor((mouseX - PADDING.left) / bw)

      if (index >= 0 && index < buckets.length) {
        const barCenterX = PADDING.left + index * bw + bw / 2
        setTooltip({ bucket: buckets[index], barCenterX })
      } else {
        setTooltip(null)
      }
    },
    [buckets, handlers],
  )

  const handleMouseLeave = useCallback(
    () => {
      handlers.onMouseLeave()
      setTooltip(null)
    },
    [handlers],
  )

  return (
    <div ref={containerRef} data-testid="job-timechart" className="relative w-full mb-3 pb-14 rounded bg-slate-800">
      {zoomDepth > 0 && (
        <button
          data-testid="reset-zoom"
          onClick={onResetZoom}
          className="absolute top-2 right-2 z-10 flex items-center gap-1 px-2 py-1 text-xs text-slate-300 bg-slate-700 hover:bg-slate-600 rounded"
        >
          <RotateCcw className="h-3 w-3" />
          Reset zoom
        </button>
      )}

      <svg
        width="100%"
        height={CHART_HEIGHT}
        className="select-none cursor-pointer"
        onMouseDown={handlers.onMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handlers.onMouseUp}
        onMouseLeave={handleMouseLeave}
      >
        {/* Y-axis grid lines */}
        {yGridLines.map((v) => {
          const y = PADDING.top + plotHeight - (v / maxCount) * plotHeight
          return (
            <g key={v}>
              <line
                x1={PADDING.left}
                y1={y}
                x2={containerWidth - PADDING.right}
                y2={y}
                stroke="#334155"
                strokeDasharray="4 2"
              />
              <text x={PADDING.left - 6} y={y + 3} textAnchor="end" fill="#94a3b8" fontSize={10}>
                {v}
              </text>
            </g>
          )
        })}

        {/* X-axis labels */}
        {xLabels.map((label, i) => (
          <text
            key={i}
            x={label.x}
            y={CHART_HEIGHT - 6}
            textAnchor="middle"
            fill="#94a3b8"
            fontSize={10}
          >
            {label.text}
          </text>
        ))}

        {/* Bars */}
        {buckets.map((bucket, i) => {
          const bw = Math.max(1, barWidth - BAR_GAP)
          const x = PADDING.left + i * barWidth + BAR_GAP / 2
          const total = bucket.started + bucket.completed + bucket.failed + bucket.running

          if (total === 0) return null

          const completedH = (bucket.completed / maxCount) * plotHeight
          const failedH = (bucket.failed / maxCount) * plotHeight
          const runningH = (bucket.running / maxCount) * plotHeight
          const startedH = (bucket.started / maxCount) * plotHeight

          const baseY = PADDING.top + plotHeight

          return (
            <g key={i}>
              {bucket.completed > 0 && (
                <rect
                  x={x}
                  y={baseY - completedH}
                  width={bw}
                  height={completedH}
                  fill={STATUS_COLORS.completed}
                  rx={1}
                />
              )}
              {bucket.failed > 0 && (
                <rect
                  x={x}
                  y={baseY - completedH - failedH}
                  width={bw}
                  height={failedH}
                  fill={STATUS_COLORS.failed}
                  rx={1}
                />
              )}
              {bucket.running > 0 && (
                <rect
                  x={x}
                  y={baseY - completedH - failedH - runningH}
                  width={bw}
                  height={runningH}
                  fill={STATUS_COLORS.running}
                  rx={1}
                />
              )}
              {bucket.started > 0 && (
                <rect
                  x={x}
                  y={baseY - completedH - failedH - runningH - startedH}
                  width={bw}
                  height={startedH}
                  fill={STATUS_COLORS.started}
                  rx={1}
                />
              )}
            </g>
          )
        })}

        {/* Brush selection overlay */}
        {brush.active && (
          <rect
            x={Math.min(brush.startX, brush.currentX)}
            y={PADDING.top}
            width={Math.abs(brush.currentX - brush.startX)}
            height={plotHeight}
            fill="rgba(99, 102, 241, 0.2)"
            stroke="#6366f1"
            strokeWidth={1}
          />
        )}
      </svg>

      <div className="relative">
        <ChartTooltip
          bucket={tooltip?.bucket ?? null}
          visible={tooltip !== null}
          rangeDays={rangeDays}
          barCenterX={tooltip?.barCenterX ?? 0}
        />
      </div>
    </div>
  )
}
