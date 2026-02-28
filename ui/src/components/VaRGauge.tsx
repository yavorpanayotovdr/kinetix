import { useCallback, useEffect, useRef, useState } from 'react'
import { Info, X } from 'lucide-react'
import { useClickOutside } from '../hooks/useClickOutside'
import { formatMoney } from '../utils/format'
import { formatCompactCurrency } from '../utils/formatCompactCurrency'

type Metric = 'var' | 'es'

const metricDescriptions: Record<Metric, string> = {
  var: "Value at Risk (VaR) estimates the maximum expected loss over a given time horizon at the selected confidence level under normal market conditions.",
  es: "Expected Shortfall (ES) estimates the average loss in scenarios that exceed the VaR threshold, capturing the severity of tail-risk events beyond the confidence level.",
}

interface VaRGaugeProps {
  varValue: number
  expectedShortfall: number
  confidenceLevel: string
  varLimit?: number | null
  pvValue?: string
  previousVaR?: number | null
}

function gaugeColorEsBased(ratio: number): string {
  if (ratio < 0.5) return '#22c55e'
  if (ratio < 0.8) return '#f59e0b'
  return '#ef4444'
}

function gaugeColorLimitBased(ratio: number): string {
  if (ratio < 0.6) return '#22c55e'
  if (ratio < 0.85) return '#f59e0b'
  return '#ef4444'
}

function confidenceLabel(level: string): string {
  if (level === 'CL_99') return 'VaR (99%)'
  return 'VaR (95%)'
}

export function VaRGauge({ varValue, expectedShortfall, confidenceLevel, varLimit, pvValue, previousVaR }: VaRGaugeProps) {
  const [openPopover, setOpenPopover] = useState<Metric | null>(null)
  const gaugeRef = useRef<HTMLDivElement>(null)

  const closePopover = useCallback(() => setOpenPopover(null), [])
  useClickOutside(gaugeRef, closePopover)

  useEffect(() => {
    if (!openPopover) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpenPopover(null)
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [openPopover])

  const togglePopover = (metric: Metric) => {
    setOpenPopover(prev => prev === metric ? null : metric)
  }

  const hasLimit = varLimit != null && varLimit > 0
  const maxValue = hasLimit ? varLimit : expectedShortfall * 1.5
  const ratio = Math.min(varValue / maxValue, 1)
  const color = hasLimit ? gaugeColorLimitBased(ratio) : gaugeColorEsBased(ratio)
  const utilisationPct = Math.round(ratio * 100)

  return (
    <div data-testid="var-gauge" ref={gaugeRef} className="flex flex-col gap-1">
      <div className="relative">
        <div data-testid="var-confidence" className="text-xs text-slate-500 inline-flex items-center gap-1">
          {confidenceLabel(confidenceLevel)}
          <Info
            data-testid="var-info"
            className="h-3 w-3 cursor-pointer text-slate-400 hover:text-slate-600 transition-colors"
            onClick={() => togglePopover('var')}
          />
        </div>
        {openPopover === 'var' && (
          <span
            data-testid="var-popover"
            className="absolute top-full left-1/2 -translate-x-1/2 mt-1 w-64 rounded bg-slate-800 px-3 py-2 text-xs font-normal text-white text-justify shadow-lg z-10"
          >
            <button data-testid="var-popover-close" className="float-right ml-2 text-slate-400 hover:text-white" onClick={closePopover}><X className="h-3 w-3" /></button>
            {metricDescriptions.var}
          </span>
        )}
      </div>

      <div className="flex items-center gap-2">
        <div data-testid="var-value" className="text-2xl font-bold">
          {formatMoney(varValue.toFixed(2), 'USD')}
        </div>
        <span
          data-testid="var-status-dot"
          className="h-2.5 w-2.5 rounded-full"
          style={{ backgroundColor: color }}
        />
      </div>

      {previousVaR != null && (() => {
        const change = varValue - previousVaR
        const absChange = Math.abs(change)
        const formattedChange = formatMoney(absChange.toFixed(2), 'USD')
        const hasPct = previousVaR !== 0
        const pct = hasPct ? (change / previousVaR) * 100 : 0
        const arrow = change > 0 ? '\u2191' : change < 0 ? '\u2193' : '\u2014'
        const colorClass = change > 0 ? 'text-red-600' : change < 0 ? 'text-green-600' : 'text-slate-400'
        const sign = change > 0 ? '+' : change < 0 ? '-' : ''

        return (
          <div data-testid="var-change" className={`text-xs ${colorClass}`}>
            {arrow} {formattedChange}{hasPct ? ` (${sign}${Math.abs(pct).toFixed(1)}%)` : ''}
          </div>
        )
      })()}

      <div className="relative">
        <div data-testid="es-value" className="text-sm text-slate-600 inline-flex items-center gap-1">
          ES
          <Info
            data-testid="es-info"
            className="h-3 w-3 cursor-pointer text-slate-400 hover:text-slate-600 transition-colors"
            onClick={() => togglePopover('es')}
          />
          {formatMoney(expectedShortfall.toFixed(2), 'USD')}
          {varValue > 0 && (
            <span data-testid="es-var-ratio" className="text-xs text-slate-400">
              ({(expectedShortfall / varValue).toFixed(2)}x)
            </span>
          )}
        </div>
        {openPopover === 'es' && (
          <span
            data-testid="es-popover"
            className="absolute top-full left-1/2 -translate-x-1/2 mt-1 w-64 rounded bg-slate-800 px-3 py-2 text-xs font-normal text-white text-justify shadow-lg z-10"
          >
            <button data-testid="es-popover-close" className="float-right ml-2 text-slate-400 hover:text-white" onClick={closePopover}><X className="h-3 w-3" /></button>
            {metricDescriptions.es}
          </span>
        )}
      </div>

      {pvValue && (
        <div data-testid="var-pv" className="text-sm text-slate-600">
          PV {formatCompactCurrency(Number(pvValue))}
        </div>
      )}

      {hasLimit && (
        <>
          <div className="border-t border-slate-100 mt-1 pt-1" />
          <div data-testid="var-limit" className="flex items-center justify-between text-xs text-slate-500">
            <span>Limit</span>
            <span>{utilisationPct}%</span>
          </div>
          <div data-testid="var-limit-bar" className="h-1.5 rounded-full bg-slate-200 overflow-hidden">
            <div
              data-testid="var-limit-bar-fill"
              className="h-full rounded-full transition-all"
              style={{ width: `${utilisationPct}%`, backgroundColor: color }}
            />
          </div>
        </>
      )}
    </div>
  )
}
