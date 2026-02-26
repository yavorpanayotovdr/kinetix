import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import { formatTimeOnly } from '../utils/format'
import { formatCompactCurrency } from '../utils/formatCompactCurrency'
import { clampTooltipLeft } from '../utils/clampTooltipLeft'

interface VaRTrendChartProps {
  history: VaRHistoryEntry[]
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

export function VaRTrendChart({ history }: VaRTrendChartProps) {
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

  const xLabels = useMemo(() => {
    if (history.length < 2) return []
    const count = Math.min(6, history.length)
    const step = Math.max(1, Math.floor((history.length - 1) / (count - 1)))
    const labels: { x: number; text: string }[] = []

    for (let i = 0; i < history.length; i += step) {
      const x = PADDING.left + (i / (history.length - 1)) * plotWidth
      labels.push({ x, text: formatTimeOnly(history[i].calculatedAt) })
    }

    const lastIdx = history.length - 1
    if (labels.length > 0 && labels[labels.length - 1].x < PADDING.left + plotWidth - 20) {
      labels.push({
        x: PADDING.left + plotWidth,
        text: formatTimeOnly(history[lastIdx].calculatedAt),
      })
    }

    return labels
  }, [history, plotWidth])

  const points = useMemo(() => {
    if (history.length < 2) return []
    const range = max - min || 1
    return history.map((entry, i) => ({
      x: PADDING.left + (i / (history.length - 1)) * plotWidth,
      y: PADDING.top + (1 - (entry.varValue - min) / range) * plotHeight,
    }))
  }, [history, plotWidth, plotHeight, min, max])

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
    [points],
  )

  const handleMouseLeave = useCallback(() => {
    setHoveredIndex(null)
  }, [])

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
      <div className="flex items-center justify-between mb-1">
        <h3 className="text-sm font-semibold text-slate-300">VaR Trend</h3>
        <span className="text-sm font-mono text-indigo-400">{formattedLatest}</span>
      </div>

      <svg
        width="100%"
        height={CHART_HEIGHT}
        className="select-none"
        onMouseMove={handleMouseMove}
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
