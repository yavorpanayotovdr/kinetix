import { useEffect, useRef, useState } from 'react'
import type { VolPointDiffResponse, VolPointResponse } from '../api/volSurface'

interface VolSkewChartProps {
  points: VolPointResponse[]
  maturities: number[]
  diffPoints?: VolPointDiffResponse[]
  isLoading?: boolean
}

const PADDING = { top: 32, right: 16, bottom: 36, left: 56 }
const CHART_HEIGHT = 220
const DEFAULT_WIDTH = 600

// Colours for up to 6 maturity lines
const MATURITY_COLOURS = ['#3b82f6', '#22c55e', '#a855f7', '#f59e0b', '#ef4444', '#06b6d4']

function computeNiceGridLines(min: number, max: number, count: number): number[] {
  const range = max - min
  if (range === 0) return [min]
  const rough = range / count
  const magnitude = Math.pow(10, Math.floor(Math.log10(rough)))
  const candidates = [1, 2, 2.5, 5, 10]
  let step = candidates[candidates.length - 1] * magnitude
  for (const c of candidates) {
    if (c * magnitude >= rough) { step = c * magnitude; break }
  }
  const lines: number[] = []
  const start = Math.ceil(min / step) * step
  for (let v = start; v <= max + 1e-9; v += step) lines.push(v)
  if (lines.length < 2 && range > 0) {
    lines.length = 0
    const s = range / count
    for (let i = 1; i <= count; i++) lines.push(min + s * i)
  }
  return lines
}

function labelForMaturity(days: number): string {
  if (days < 30) return `${days}D`
  if (days < 365) return `${Math.round(days / 30)}M`
  return `${(days / 365).toFixed(1)}Y`
}

export function VolSkewChart({ points, maturities, diffPoints, isLoading }: VolSkewChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [containerWidth, setContainerWidth] = useState(DEFAULT_WIDTH)
  const [hiddenMaturities, setHiddenMaturities] = useState<Set<number>>(new Set())

  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) setContainerWidth(entry.contentRect.width)
    })
    observer.observe(el)
    setContainerWidth(el.clientWidth)
    return () => observer.disconnect()
  }, [points.length])

  const plotWidth = containerWidth - PADDING.left - PADDING.right
  const plotHeight = CHART_HEIGHT - PADDING.top - PADDING.bottom

  if (isLoading && points.length === 0) {
    return (
      <div data-testid="vol-skew-chart" className="rounded bg-slate-800 p-4">
        <h3 className="text-sm font-semibold text-slate-300 mb-3">Vol Skew</h3>
        <div role="status" aria-label="Loading chart data" className="space-y-3 animate-pulse" style={{ height: CHART_HEIGHT }}>
          <div className="h-2 bg-slate-700 rounded w-full" />
          <div className="h-2 bg-slate-700 rounded w-3/4" />
        </div>
      </div>
    )
  }

  if (points.length === 0) {
    return (
      <div data-testid="vol-skew-chart" className="rounded bg-slate-800 p-4">
        <h3 className="text-sm font-semibold text-slate-300 mb-3">Vol Skew</h3>
        <div className="flex items-center justify-center text-sm text-slate-400" style={{ height: CHART_HEIGHT }}>
          No vol surface data available.
        </div>
      </div>
    )
  }

  // Build strike → x mapping from the sorted unique strikes
  const allStrikes = [...new Set(points.map((p) => p.strike))].sort((a, b) => a - b)
  const atmStrike = allStrikes[Math.floor(allStrikes.length / 2)]

  // Express strike as % of ATM for the x-axis
  const strikePercents = allStrikes.map((s) => (s / atmStrike) * 100)
  const xMin = Math.min(...strikePercents)
  const xMax = Math.max(...strikePercents)

  const toX = (strike: number) => {
    const pct = (strike / atmStrike) * 100
    return PADDING.left + ((pct - xMin) / (xMax - xMin || 1)) * plotWidth
  }

  // Y-axis: implied vol range across all visible points
  const visiblePoints = points.filter((p) => !hiddenMaturities.has(p.maturityDays))
  const allVols = visiblePoints.map((p) => p.impliedVol)
  const yMin = allVols.length > 0 ? Math.min(...allVols) : 0
  const yMax = allVols.length > 0 ? Math.max(...allVols) : 1
  const yPad = (yMax - yMin) * 0.1 || 0.01
  const dataMin = yMin - yPad
  const dataMax = yMax + yPad

  const toY = (vol: number) => {
    const range = dataMax - dataMin || 1
    return PADDING.top + (1 - (vol - dataMin) / range) * plotHeight
  }

  const gridLines = computeNiceGridLines(dataMin, dataMax, 4)

  // x-axis tick labels at ATM ± 10% ± 5%
  const xTickPercents = [-10, -5, 0, 5, 10].map((d) => 100 + d)
  const xTicks = xTickPercents
    .filter((pct) => pct >= xMin - 1 && pct <= xMax + 1)
    .map((pct) => ({
      x: PADDING.left + ((pct - xMin) / (xMax - xMin || 1)) * plotWidth,
      label: `${pct === 100 ? 'ATM' : `${pct > 100 ? '+' : ''}${pct - 100}%`}`,
    }))

  // Build polyline for each maturity
  const maturityLines = maturities.map((mat, i) => {
    const pts = points
      .filter((p) => p.maturityDays === mat)
      .sort((a, b) => a.strike - b.strike)
      .map((p) => `${toX(p.strike).toFixed(1)},${toY(p.impliedVol).toFixed(1)}`)
      .join(' ')
    return { mat, pts, colour: MATURITY_COLOURS[i % MATURITY_COLOURS.length] }
  })

  // Diff dashed overlay (only for maturities that have diff data)
  const diffMaturities = diffPoints ? [...new Set(diffPoints.map((d) => d.maturityDays))] : []
  const diffLines = diffMaturities.map((mat, i) => {
    const pts = (diffPoints ?? [])
      .filter((d) => d.maturityDays === mat)
      .sort((a, b) => a.strike - b.strike)
      .map((d) => `${toX(d.strike).toFixed(1)},${toY(d.compareVol).toFixed(1)}`)
      .join(' ')
    return { mat, pts, colour: MATURITY_COLOURS[i % MATURITY_COLOURS.length] }
  })

  return (
    <div ref={containerRef} data-testid="vol-skew-chart" className="rounded bg-slate-800 p-4">
      <h3 className="text-sm font-semibold text-slate-300 mb-2">Vol Skew</h3>

      {/* Maturity toggles */}
      <div className="flex flex-wrap gap-2 mb-2">
        {maturities.map((mat, i) => {
          const isOn = !hiddenMaturities.has(mat)
          const colour = MATURITY_COLOURS[i % MATURITY_COLOURS.length]
          return (
            <button
              key={mat}
              type="button"
              data-testid={`maturity-toggle-${mat}`}
              aria-pressed={isOn}
              onClick={() =>
                setHiddenMaturities((prev) => {
                  const next = new Set(prev)
                  if (next.has(mat)) next.delete(mat)
                  else next.add(mat)
                  return next
                })
              }
              className="flex items-center gap-1 text-xs text-slate-400 bg-transparent border-0 p-0 cursor-pointer"
              style={{ opacity: isOn ? 1 : 0.4 }}
            >
              <span className="inline-block w-3 h-0.5 rounded" style={{ backgroundColor: colour }} />
              {labelForMaturity(mat)}
            </button>
          )
        })}
      </div>

      <svg width="100%" height={CHART_HEIGHT} className="select-none">
        {/* Y-axis grid lines */}
        {gridLines.map((v) => {
          const y = toY(v)
          return (
            <g key={v}>
              <line x1={PADDING.left} y1={y} x2={containerWidth - PADDING.right} y2={y} stroke="#334155" strokeDasharray="4 2" />
              <text x={PADDING.left - 6} y={y + 3} textAnchor="end" fill="#94a3b8" fontSize={10} className="font-mono tabular-nums">
                {(v * 100).toFixed(1)}%
              </text>
            </g>
          )
        })}

        {/* X-axis labels */}
        {xTicks.map((t, i) => (
          <text key={i} x={t.x} y={CHART_HEIGHT - 6} textAnchor="middle" fill="#94a3b8" fontSize={10}>
            {t.label}
          </text>
        ))}

        {/* Base vol lines (solid) */}
        {maturityLines.map(({ mat, pts, colour }) => (
          pts.length > 0 && (
            <polyline
              key={`base-${mat}`}
              points={pts}
              fill="none"
              stroke={colour}
              strokeWidth={2}
              strokeLinejoin="round"
              opacity={hiddenMaturities.has(mat) ? 0.2 : 1}
              style={{ transition: 'opacity 150ms' }}
            />
          )
        ))}

        {/* Diff vol overlay (dashed) */}
        {diffLines.map(({ mat, pts, colour }) => (
          pts.length > 0 && !hiddenMaturities.has(mat) && (
            <polyline
              key={`diff-${mat}`}
              points={pts}
              fill="none"
              stroke={colour}
              strokeWidth={1.5}
              strokeLinejoin="round"
              strokeDasharray="5 3"
              opacity={0.6}
            />
          )
        ))}
      </svg>
    </div>
  )
}
