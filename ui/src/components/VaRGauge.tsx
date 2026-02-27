import { useCallback, useEffect, useRef, useState } from 'react'
import { Info } from 'lucide-react'
import { useClickOutside } from '../hooks/useClickOutside'
import { formatMoney } from '../utils/format'

type Metric = 'var' | 'es'

const metricDescriptions: Record<Metric, string> = {
  var: "Value at Risk (VaR) estimates the maximum expected loss over a given time horizon at the selected confidence level under normal market conditions.",
  es: "Expected Shortfall (ES) estimates the average loss in scenarios that exceed the VaR threshold, capturing the severity of tail-risk events beyond the confidence level.",
}

interface VaRGaugeProps {
  varValue: number
  expectedShortfall: number
  confidenceLevel: string
}

function gaugeColor(ratio: number): string {
  if (ratio < 0.5) return '#22c55e'
  if (ratio < 0.8) return '#f59e0b'
  return '#ef4444'
}

function confidenceLabel(level: string): string {
  if (level === 'CL_99') return 'VaR (99%)'
  return 'VaR (95%)'
}

export function VaRGauge({ varValue, expectedShortfall, confidenceLevel }: VaRGaugeProps) {
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

  const maxValue = expectedShortfall * 1.5
  const ratio = Math.min(varValue / maxValue, 1)
  const color = gaugeColor(ratio)

  const radius = 80
  const circumference = Math.PI * radius
  const dashOffset = circumference * (1 - ratio)

  return (
    <div ref={gaugeRef} data-testid="var-gauge" className="flex flex-col items-center">
      <svg viewBox="0 0 200 120" className="w-48 h-28">
        <path
          d="M 20 90 A 80 80 0 0 1 180 90"
          fill="none"
          stroke="#e2e8f0"
          strokeWidth="12"
          strokeLinecap="round"
        />
        <path
          d="M 20 90 A 80 80 0 0 1 180 90"
          fill="none"
          stroke={color}
          strokeWidth="12"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={dashOffset}
        />
      </svg>
      <div data-testid="var-confidence" className="text-xs text-slate-500 -mt-2">
        {confidenceLabel(confidenceLevel)}
      </div>
      <div className="relative">
        <div data-testid="var-value" className="text-lg font-bold mt-1 inline-flex items-center gap-1">
          {formatMoney(varValue.toFixed(2), 'USD')}
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
            {metricDescriptions.var}
          </span>
        )}
      </div>
      <div className="relative">
        <div data-testid="es-value" className="text-xs text-slate-500 mt-1 inline-flex items-center gap-1">
          ES: {formatMoney(expectedShortfall.toFixed(2), 'USD')}
          <Info
            data-testid="es-info"
            className="h-3 w-3 cursor-pointer text-slate-400 hover:text-slate-600 transition-colors"
            onClick={() => togglePopover('es')}
          />
        </div>
        {openPopover === 'es' && (
          <span
            data-testid="es-popover"
            className="absolute top-full left-1/2 -translate-x-1/2 mt-1 w-64 rounded bg-slate-800 px-3 py-2 text-xs font-normal text-white text-justify shadow-lg z-10"
          >
            {metricDescriptions.es}
          </span>
        )}
      </div>
    </div>
  )
}
