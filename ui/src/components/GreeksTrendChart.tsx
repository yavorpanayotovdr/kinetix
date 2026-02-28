import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { RotateCcw } from 'lucide-react'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import type { TimeRange } from '../types'
import { formatTimeOnly, formatChartTime } from '../utils/format'
import { clampTooltipLeft } from '../utils/clampTooltipLeft'
import { useBrushSelection } from '../hooks/useBrushSelection'
import { resolveTimeRange } from '../utils/resolveTimeRange'

interface GreeksTrendChartProps {
  history: VaRHistoryEntry[]
  timeRange?: TimeRange
  onZoom?: (range: TimeRange) => void
  zoomDepth?: number
  onResetZoom?: () => void
}

const PADDING = { top: 32, right: 16, bottom: 32, left: 56 }
const CHART_HEIGHT = 220
const DEFAULT_WIDTH = 600

const SERIES = [
  { key: 'delta' as const, color: '#3b82f6', label: 'Delta' },
  { key: 'gamma' as const, color: '#22c55e', label: 'Gamma' },
  { key: 'vega' as const, color: '#a855f7', label: 'Vega' },
  { key: 'theta' as const, color: '#f59e0b', label: 'Theta' },
]

function computeNiceGridLines(min: number, max: number, count: number): number[] {
  const range = max - min
  if (range === 0) return [min]

  const rough = range / count
  const magnitude = Math.pow(10, Math.floor(Math.log10(rough)))
  const candidates = [1, 2, 2.5, 5, 10]
  let step = candidates[candidates.length - 1] * magnitude
  for (const c of candidates) {
    if (c * magnitude >= rough) {
      step = c * magnitude
      break
    }
  }

  const lines: number[] = []
  const start = Math.ceil(min / step) * step
  for (let v = start; v <= max; v += step) {
    lines.push(v)
  }

  if (lines.length < 2 && range > 0) {
    lines.length = 0
    const simpleStep = range / count
    for (let i = 1; i <= count; i++) {
      lines.push(min + simpleStep * i)
    }
  }

  return lines
}

function formatCompactNumber(value: number): string {
  const abs = Math.abs(value)
  const sign = value < 0 ? '-' : ''

  if (abs >= 1_000_000) return `${sign}${(abs / 1_000_000).toFixed(1).replace(/\.0$/, '')}M`
  if (abs >= 1_000) return `${sign}${(abs / 1_000).toFixed(1).replace(/\.0$/, '')}K`
  return `${sign}${abs.toFixed(1).replace(/\.0$/, '')}`
}

export function GreeksTrendChart({ history, timeRange, onZoom, zoomDepth = 0, onResetZoom }: GreeksTrendChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const tooltipRef = useRef<HTMLDivElement>(null)
  const [containerWidth, setContainerWidth] = useState(DEFAULT_WIDTH)
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)
  const [tooltipLeft, setTooltipLeft] = useState(0)

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

  // Filter to entries that have greeks data
  const greeksHistory = useMemo(
    () => history.filter((e) => e.delta !== undefined && e.gamma !== undefined && e.vega !== undefined),
    [history],
  )

  const plotWidth = containerWidth - PADDING.left - PADDING.right
  const plotHeight = CHART_HEIGHT - PADDING.top - PADDING.bottom

  const { min, max } = useMemo(() => {
    const values = greeksHistory.flatMap((e) => {
      const v = [e.delta!, e.gamma!, e.vega!]
      if (e.theta !== undefined) v.push(e.theta)
      return v
    })
    if (values.length === 0) return { min: 0, max: 1 }
    const minVal = Math.min(...values)
    const maxVal = Math.max(...values)
    const range = maxVal - minVal
    const padding = range * 0.1 || Math.abs(maxVal) * 0.1 || 1
    return { min: minVal - padding, max: maxVal + padding }
  }, [greeksHistory])

  const gridLines = useMemo(() => computeNiceGridLines(min, max, 4), [min, max])

  const timeExtent = useMemo(() => {
    if (timeRange) {
      const { from, to } = resolveTimeRange(timeRange)
      const fromMs = new Date(from).getTime()
      const toMs = new Date(to).getTime()
      if (toMs > fromMs) return { fromMs, toMs, durationMs: toMs - fromMs }
    }
    if (greeksHistory.length >= 2) {
      const times = greeksHistory.map((e) => new Date(e.calculatedAt).getTime())
      const fromMs = Math.min(...times)
      const toMs = Math.max(...times)
      if (toMs > fromMs) return { fromMs, toMs, durationMs: toMs - fromMs }
    }
    return null
  }, [timeRange, greeksHistory])

  const toX = useCallback(
    (timestampMs: number) => {
      if (timeExtent) {
        const pct = Math.max(0, Math.min(1, (timestampMs - timeExtent.fromMs) / timeExtent.durationMs))
        return PADDING.left + pct * plotWidth
      }
      return 0
    },
    [timeExtent, plotWidth],
  )

  const toY = useCallback(
    (value: number) => {
      const range = max - min || 1
      return PADDING.top + (1 - (value - min) / range) * plotHeight
    },
    [min, max, plotHeight],
  )

  const handleBrushEnd = useCallback(
    (startX: number, endX: number) => {
      if (!timeExtent || !onZoom) return

      const leftPct = (startX - PADDING.left) / plotWidth
      const rightPct = (endX - PADDING.left) / plotWidth

      const zoomFrom = new Date(timeExtent.fromMs + leftPct * timeExtent.durationMs)
      const zoomTo = new Date(timeExtent.fromMs + rightPct * timeExtent.durationMs)

      onZoom({
        from: zoomFrom.toISOString(),
        to: zoomTo.toISOString(),
        label: 'Custom',
      })
    },
    [timeExtent, onZoom, plotWidth],
  )

  const { brush, handlers: brushHandlers } = useBrushSelection({ onBrushEnd: handleBrushEnd })

  const xLabels = useMemo(() => {
    if (greeksHistory.length < 2 || !timeExtent) return []

    const count = 6
    const rangeDays = timeExtent.durationMs / (24 * 60 * 60 * 1000)
    const labels: { x: number; text: string }[] = []
    for (let i = 0; i <= count; i++) {
      const t = timeExtent.fromMs + (i / count) * timeExtent.durationMs
      labels.push({
        x: PADDING.left + (i / count) * plotWidth,
        text: formatChartTime(new Date(t), rangeDays),
      })
    }
    return labels
  }, [greeksHistory, plotWidth, timeExtent])

  const seriesPoints = useMemo(() => {
    if (greeksHistory.length < 2) return null

    return SERIES.map((s) => ({
      ...s,
      points: greeksHistory.map((entry) => {
        const value = entry[s.key]
        return {
          x: timeExtent
            ? toX(new Date(entry.calculatedAt).getTime())
            : PADDING.left + plotWidth / 2,
          y: value !== undefined ? toY(value) : null,
        }
      }),
    }))
  }, [greeksHistory, plotWidth, timeExtent, toX, toY])

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<SVGSVGElement>) => {
      brushHandlers.onMouseMove(e)

      if (!seriesPoints || seriesPoints[0].points.length === 0) return

      const el = containerRef.current
      if (!el) return

      const rect = el.getBoundingClientRect()
      const mouseX = e.clientX - rect.left

      const points = seriesPoints[0].points
      let closest = 0
      let closestDist = Infinity
      for (let i = 0; i < points.length; i++) {
        const dist = Math.abs(points[i].x - mouseX)
        if (dist < closestDist) {
          closestDist = dist
          closest = i
        }
      }

      setHoveredIndex(closest)
    },
    [seriesPoints, brushHandlers],
  )

  const handleMouseLeave = useCallback(() => {
    brushHandlers.onMouseLeave()
    setHoveredIndex(null)
  }, [brushHandlers])

  useLayoutEffect(() => {
    if (hoveredIndex === null || !tooltipRef.current || !seriesPoints) return
    const tooltipWidth = tooltipRef.current.offsetWidth
    const pointX = seriesPoints[0].points[hoveredIndex]?.x ?? 0
    setTooltipLeft(clampTooltipLeft(pointX, tooltipWidth, containerWidth))
  }, [hoveredIndex, seriesPoints, containerWidth])

  if (greeksHistory.length === 0) {
    return (
      <div data-testid="greeks-trend-chart" className="rounded bg-slate-800 p-4">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-slate-300">Greeks Trend</h3>
        </div>
        <div className="flex items-center justify-center text-sm text-slate-400" style={{ height: CHART_HEIGHT }}>
          Collecting data...
        </div>
      </div>
    )
  }

  if (greeksHistory.length === 1) {
    return (
      <div data-testid="greeks-trend-chart" className="rounded bg-slate-800 p-4">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-slate-300">Greeks Trend</h3>
        </div>
        <div className="flex items-center justify-center text-sm text-slate-400" style={{ height: CHART_HEIGHT }}>
          Trend data requires at least 2 calculations. Current values shown in the table above.
        </div>
      </div>
    )
  }

  return (
    <div ref={containerRef} data-testid="greeks-trend-chart" className="relative rounded bg-slate-800 p-4 pb-14">
      {zoomDepth > 0 && onResetZoom && (
        <button
          data-testid="reset-zoom"
          onClick={onResetZoom}
          className="absolute top-2 right-2 z-10 flex items-center gap-1 px-2 py-1 text-xs text-slate-300 bg-slate-700 hover:bg-slate-600 rounded"
        >
          <RotateCcw className="h-3 w-3" />
          Reset zoom
        </button>
      )}

      <div className="flex items-center justify-between mb-1">
        <h3 className="text-sm font-semibold text-slate-300">Greeks Trend</h3>
      </div>

      <div className="flex items-center gap-3 mb-1 text-xs text-slate-400">
        {SERIES.map((s) => (
          <span key={s.key} className="flex items-center gap-1">
            <span className="inline-block w-3 h-0.5 rounded" style={{ backgroundColor: s.color }} /> {s.label}
          </span>
        ))}
      </div>

      <svg
        width="100%"
        height={CHART_HEIGHT}
        className={`select-none ${onZoom ? 'cursor-crosshair' : ''}`}
        onMouseDown={onZoom ? brushHandlers.onMouseDown : undefined}
        onMouseMove={handleMouseMove}
        onMouseUp={onZoom ? brushHandlers.onMouseUp : undefined}
        onMouseLeave={handleMouseLeave}
      >
        {/* Y-axis grid lines */}
        {gridLines.map((v) => {
          const y = toY(v)
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
                {formatCompactNumber(v)}
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

        {/* Series lines */}
        {seriesPoints?.map((s) => {
          const validPoints = s.points.filter((p): p is { x: number; y: number } => p.y !== null)
          if (validPoints.length < 2) return null
          const polyline = validPoints.map((p) => `${p.x},${p.y}`).join(' ')
          return (
            <polyline
              key={s.key}
              points={polyline}
              fill="none"
              stroke={s.color}
              strokeWidth={2}
              strokeLinejoin="round"
            />
          )
        })}

        {/* Hover crosshair + dots */}
        {hoveredIndex !== null && seriesPoints && (
          <>
            <line
              data-testid="crosshair"
              x1={seriesPoints[0].points[hoveredIndex].x}
              y1={PADDING.top}
              x2={seriesPoints[0].points[hoveredIndex].x}
              y2={PADDING.top + plotHeight}
              stroke="#94a3b8"
              strokeDasharray="4 2"
              strokeWidth={1}
            />
            {seriesPoints.map((s) => {
              const point = s.points[hoveredIndex]
              if (point.y === null) return null
              return (
                <circle
                  key={s.key}
                  data-testid={`hover-dot-${s.key}`}
                  cx={point.x}
                  cy={point.y}
                  r={4}
                  fill={s.color}
                  stroke="white"
                  strokeWidth={2}
                />
              )
            })}
          </>
        )}

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

      {/* Tooltip */}
      {hoveredIndex !== null && greeksHistory[hoveredIndex] && (
        <div className="relative">
          <div
            ref={tooltipRef}
            data-testid="greeks-trend-tooltip"
            className="absolute top-0 bg-slate-800 text-white text-xs rounded shadow-lg px-3 py-2 pointer-events-none whitespace-nowrap border border-slate-600"
            style={{ left: `${tooltipLeft}px` }}
          >
            <div className="font-medium mb-1">{formatTimeOnly(greeksHistory[hoveredIndex].calculatedAt)}</div>
            <div className="flex gap-3">
              <span style={{ color: '#3b82f6' }}>
                Delta: {greeksHistory[hoveredIndex].delta?.toFixed(2)}
              </span>
              <span style={{ color: '#22c55e' }}>
                Gamma: {greeksHistory[hoveredIndex].gamma?.toFixed(2)}
              </span>
              <span style={{ color: '#a855f7' }}>
                Vega: {greeksHistory[hoveredIndex].vega?.toFixed(2)}
              </span>
              {greeksHistory[hoveredIndex].theta !== undefined && (
                <span style={{ color: '#f59e0b' }}>
                  Theta: {greeksHistory[hoveredIndex].theta?.toFixed(2)}
                </span>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
