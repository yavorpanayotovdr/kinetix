import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { RotateCcw } from 'lucide-react'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import type { TimeRange } from '../types'
import { formatTimeOnly, formatChartTime } from '../utils/format'
import { formatCompactCurrency } from '../utils/formatCompactCurrency'
import { clampTooltipLeft } from '../utils/clampTooltipLeft'
import { useBrushSelection } from '../hooks/useBrushSelection'
import { resolveTimeRange } from '../utils/resolveTimeRange'

interface VaRTrendChartProps {
  history: VaRHistoryEntry[]
  timeRange?: TimeRange
  onZoom?: (range: TimeRange) => void
  zoomDepth?: number
  onResetZoom?: () => void
}

const PADDING = { top: 32, right: 16, bottom: 32, left: 56 }
const CHART_HEIGHT = 220
const DEFAULT_WIDTH = 600

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

export function VaRTrendChart({ history, timeRange, onZoom, zoomDepth = 0, onResetZoom }: VaRTrendChartProps) {
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

  const plotWidth = containerWidth - PADDING.left - PADDING.right
  const plotHeight = CHART_HEIGHT - PADDING.top - PADDING.bottom

  const { min, max } = useMemo(() => {
    const values = history.map((e) => e.varValue)
    const minVal = Math.min(...values)
    const maxVal = Math.max(...values)
    const range = maxVal - minVal
    const padding = range * 0.1 || maxVal * 0.1 || 1
    return { min: minVal - padding, max: maxVal + padding }
  }, [history])

  const gridLines = useMemo(() => computeNiceGridLines(min, max, 4), [min, max])

  // Resolve the time range fresh (sliding presets use Date.now()) so X-axis
  // labels and data positions reflect the selected period, not stale timestamps.
  // history is included as a dep so the extent re-resolves on every poll cycle.
  const timeExtent = useMemo(() => {
    if (timeRange) {
      const { from, to } = resolveTimeRange(timeRange)
      const fromMs = new Date(from).getTime()
      const toMs = new Date(to).getTime()
      if (toMs > fromMs) return { fromMs, toMs, durationMs: toMs - fromMs }
    }
    // Fallback: derive extent from data timestamps
    if (history.length >= 2) {
      const times = history.map((e) => new Date(e.calculatedAt).getTime())
      const fromMs = Math.min(...times)
      const toMs = Math.max(...times)
      if (toMs > fromMs) return { fromMs, toMs, durationMs: toMs - fromMs }
    }
    return null
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timeRange, history])

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
    if (history.length < 2 || !timeExtent) return []

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
  }, [history, plotWidth, timeExtent])

  const points = useMemo(() => {
    if (history.length < 2) return []
    const range = max - min || 1
    return history.map((entry) => ({
      x: timeExtent
        ? toX(new Date(entry.calculatedAt).getTime())
        : PADDING.left + plotWidth / 2,
      y: PADDING.top + (1 - (entry.varValue - min) / range) * plotHeight,
    }))
  }, [history, plotWidth, plotHeight, min, max, timeExtent, toX])

  const polylinePoints = points.map((p) => `${p.x},${p.y}`).join(' ')

  const areaPoints = useMemo(() => {
    if (points.length === 0) return ''
    const baseY = PADDING.top + plotHeight
    const first = `${points[0].x},${baseY}`
    const last = `${points[points.length - 1].x},${baseY}`
    return `${first} ${polylinePoints} ${last}`
  }, [points, polylinePoints, plotHeight])

  const toY = useCallback(
    (value: number) => {
      const range = max - min || 1
      return PADDING.top + (1 - (value - min) / range) * plotHeight
    },
    [min, max, plotHeight],
  )

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<SVGSVGElement>) => {
      brushHandlers.onMouseMove(e)

      if (points.length === 0) return

      const el = containerRef.current
      if (!el) return

      const rect = el.getBoundingClientRect()
      const mouseX = e.clientX - rect.left

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
    [points, brushHandlers],
  )

  const handleMouseLeave = useCallback(() => {
    brushHandlers.onMouseLeave()
    setHoveredIndex(null)
  }, [brushHandlers])

  useLayoutEffect(() => {
    if (hoveredIndex === null || !tooltipRef.current) return
    const tooltipWidth = tooltipRef.current.offsetWidth
    const pointX = points[hoveredIndex]?.x ?? 0
    setTooltipLeft(clampTooltipLeft(pointX, tooltipWidth, containerWidth))
  }, [hoveredIndex, points, containerWidth])

  const latestValue = history.length > 0 ? history[history.length - 1].varValue : 0
  const formattedLatest = latestValue.toLocaleString('en-US', { style: 'currency', currency: 'USD' })

  if (history.length < 2) {
    return (
      <div data-testid="var-trend-chart" className="rounded bg-slate-800 p-4">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-slate-300">VaR Trend</h3>
        </div>
        <div className="flex items-center justify-center text-sm text-slate-400" style={{ height: CHART_HEIGHT }}>
          Collecting data...
        </div>
      </div>
    )
  }

  return (
    <div ref={containerRef} data-testid="var-trend-chart" className="relative rounded bg-slate-800 p-4 pb-14">
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
        <h3 className="text-sm font-semibold text-slate-300">VaR Trend</h3>
        <span className="text-sm font-mono text-indigo-400">{formattedLatest}</span>
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
                {formatCompactCurrency(v)}
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

        {/* Area fill */}
        <polygon points={areaPoints} fill="rgba(99, 102, 241, 0.15)" />

        {/* Line */}
        <polyline
          points={polylinePoints}
          fill="none"
          stroke="#6366f1"
          strokeWidth={2}
          strokeLinejoin="round"
        />

        {/* Hover crosshair + dot */}
        {hoveredIndex !== null && points[hoveredIndex] && (
          <>
            <line
              data-testid="crosshair"
              x1={points[hoveredIndex].x}
              y1={PADDING.top}
              x2={points[hoveredIndex].x}
              y2={PADDING.top + plotHeight}
              stroke="#94a3b8"
              strokeDasharray="4 2"
              strokeWidth={1}
            />
            <circle
              data-testid="hover-dot"
              cx={points[hoveredIndex].x}
              cy={points[hoveredIndex].y}
              r={4}
              fill="#6366f1"
              stroke="white"
              strokeWidth={2}
            />
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
      {hoveredIndex !== null && history[hoveredIndex] && (
        <div className="relative">
          <div
            ref={tooltipRef}
            data-testid="var-trend-tooltip"
            className="absolute top-0 bg-slate-800 text-white text-xs rounded shadow-lg px-3 py-2 pointer-events-none whitespace-nowrap border border-slate-600"
            style={{ left: `${tooltipLeft}px` }}
          >
            <div className="font-medium mb-1">{formatTimeOnly(history[hoveredIndex].calculatedAt)}</div>
            <div className="flex gap-3">
              <span className="text-indigo-400">
                VaR: {history[hoveredIndex].varValue.toLocaleString('en-US', { style: 'currency', currency: 'USD' })}
              </span>
              <span className="text-amber-400">
                ES: {history[hoveredIndex].expectedShortfall.toLocaleString('en-US', { style: 'currency', currency: 'USD' })}
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
