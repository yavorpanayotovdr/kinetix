import { useEffect, useRef, useState } from 'react'
import type { VolPointDiffResponse, VolPointResponse } from '../api/volSurface'

interface VolTermStructureChartProps {
  points: VolPointResponse[]
  diffPoints?: VolPointDiffResponse[]
  isLoading?: boolean
}

const PADDING = { top: 32, right: 16, bottom: 36, left: 56 }
const CHART_HEIGHT = 220
const DEFAULT_WIDTH = 600

const ATM_COLOUR = '#3b82f6'
const DIFF_COLOUR = '#3b82f6'

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

/** Pick the ATM point for each maturity: the strike closest to the median strike */
function extractAtmSeries(points: VolPointResponse[]): { maturityDays: number; impliedVol: number }[] {
  const allStrikes = [...new Set(points.map((p) => p.strike))].sort((a, b) => a - b)
  const atmStrike = allStrikes[Math.floor(allStrikes.length / 2)]

  const byMaturity = new Map<number, VolPointResponse[]>()
  for (const p of points) {
    const bucket = byMaturity.get(p.maturityDays) ?? []
    bucket.push(p)
    byMaturity.set(p.maturityDays, bucket)
  }

  return [...byMaturity.entries()]
    .map(([mat, pts]) => {
      const atm = pts.reduce((best, p) =>
        Math.abs(p.strike - atmStrike) < Math.abs(best.strike - atmStrike) ? p : best
      )
      return { maturityDays: mat, impliedVol: atm.impliedVol }
    })
    .sort((a, b) => a.maturityDays - b.maturityDays)
}

export function VolTermStructureChart({ points, diffPoints, isLoading }: VolTermStructureChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [containerWidth, setContainerWidth] = useState(DEFAULT_WIDTH)

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
      <div data-testid="vol-term-structure-chart" className="rounded bg-slate-800 p-4">
        <h3 className="text-sm font-semibold text-slate-300 mb-3">ATM Term Structure</h3>
        <div role="status" aria-label="Loading chart data" className="space-y-3 animate-pulse" style={{ height: CHART_HEIGHT }}>
          <div className="h-2 bg-slate-700 rounded w-full" />
          <div className="h-2 bg-slate-700 rounded w-3/4" />
        </div>
      </div>
    )
  }

  if (points.length === 0) {
    return (
      <div data-testid="vol-term-structure-chart" className="rounded bg-slate-800 p-4">
        <h3 className="text-sm font-semibold text-slate-300 mb-3">ATM Term Structure</h3>
        <div className="flex items-center justify-center text-sm text-slate-400" style={{ height: CHART_HEIGHT }}>
          No vol surface data available.
        </div>
      </div>
    )
  }

  const atmSeries = extractAtmSeries(points)

  if (atmSeries.length < 2) {
    return (
      <div data-testid="vol-term-structure-chart" className="rounded bg-slate-800 p-4">
        <h3 className="text-sm font-semibold text-slate-300 mb-3">ATM Term Structure</h3>
        <div className="flex items-center justify-center text-sm text-slate-400" style={{ height: CHART_HEIGHT }}>
          Need at least two maturities to draw a term structure.
        </div>
      </div>
    )
  }

  const maturities = atmSeries.map((s) => s.maturityDays)
  const xMin = maturities[0]
  const xMax = maturities[maturities.length - 1]

  const toX = (days: number) =>
    PADDING.left + ((days - xMin) / (xMax - xMin || 1)) * plotWidth

  const allVols = atmSeries.map((s) => s.impliedVol)

  // Include diff compare vols in y extent if present
  const diffAtmVols = diffPoints
    ? extractAtmSeries(diffPoints.map((d) => ({ strike: d.strike, maturityDays: d.maturityDays, impliedVol: d.compareVol })))
      .map((s) => s.impliedVol)
    : []

  const combinedVols = [...allVols, ...diffAtmVols]
  const yMin = Math.min(...combinedVols)
  const yMax = Math.max(...combinedVols)
  const yPad = (yMax - yMin) * 0.1 || 0.01
  const dataMin = yMin - yPad
  const dataMax = yMax + yPad

  const toY = (vol: number) => {
    const range = dataMax - dataMin || 1
    return PADDING.top + (1 - (vol - dataMin) / range) * plotHeight
  }

  const gridLines = computeNiceGridLines(dataMin, dataMax, 4)

  const atmPolyline = atmSeries
    .map((s) => `${toX(s.maturityDays).toFixed(1)},${toY(s.impliedVol).toFixed(1)}`)
    .join(' ')

  // Diff dashed overlay using the same ATM extraction
  let diffPolyline: string | null = null
  if (diffPoints && diffPoints.length > 0) {
    const diffAtmSeries = extractAtmSeries(
      diffPoints.map((d) => ({ strike: d.strike, maturityDays: d.maturityDays, impliedVol: d.compareVol }))
    )
    if (diffAtmSeries.length >= 2) {
      diffPolyline = diffAtmSeries
        .map((s) => `${toX(s.maturityDays).toFixed(1)},${toY(s.impliedVol).toFixed(1)}`)
        .join(' ')
    }
  }

  // X-axis labels: up to 6 evenly spaced maturity ticks
  const labelCount = Math.min(6, maturities.length)
  const xLabels = maturities
    .filter((_, i) => {
      if (labelCount === 1) return i === 0
      return i % Math.max(1, Math.floor(maturities.length / (labelCount - 1))) === 0 ||
             i === maturities.length - 1
    })
    .slice(0, labelCount)
    .map((days) => ({ x: toX(days), label: labelForMaturity(days) }))

  return (
    <div ref={containerRef} data-testid="vol-term-structure-chart" className="rounded bg-slate-800 p-4">
      <div className="flex items-center gap-3 mb-2">
        <h3 className="text-sm font-semibold text-slate-300">ATM Term Structure</h3>
        <span className="flex items-center gap-1 text-xs text-slate-400">
          <span className="inline-block w-3 h-0.5 bg-blue-500 rounded" />
          ATM vol
        </span>
        {diffPolyline && (
          <span className="flex items-center gap-1 text-xs text-slate-400">
            <span className="inline-block w-3 h-0.5 border-b-2 border-dashed border-blue-500" />
            Prior day
          </span>
        )}
      </div>

      <svg width="100%" height={CHART_HEIGHT} className="select-none" aria-label="ATM vol term structure chart" role="img">
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
        {xLabels.map((t, i) => (
          <text key={i} x={t.x} y={CHART_HEIGHT - 6} textAnchor="middle" fill="#94a3b8" fontSize={10}>
            {t.label}
          </text>
        ))}

        {/* Diff (dashed) line */}
        {diffPolyline && (
          <polyline
            points={diffPolyline}
            fill="none"
            stroke={DIFF_COLOUR}
            strokeWidth={1.5}
            strokeLinejoin="round"
            strokeDasharray="5 3"
            opacity={0.6}
          />
        )}

        {/* ATM vol (solid) line */}
        <polyline
          points={atmPolyline}
          fill="none"
          stroke={ATM_COLOUR}
          strokeWidth={2.5}
          strokeLinejoin="round"
        />
      </svg>
    </div>
  )
}
