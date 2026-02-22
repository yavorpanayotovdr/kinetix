import { formatMoney } from '../utils/format'

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
  const maxValue = expectedShortfall * 1.5
  const ratio = Math.min(varValue / maxValue, 1)
  const color = gaugeColor(ratio)

  const radius = 80
  const circumference = Math.PI * radius
  const dashOffset = circumference * (1 - ratio)

  return (
    <div data-testid="var-gauge" className="flex flex-col items-center">
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
      <div data-testid="var-value" className="text-lg font-bold mt-1">
        {formatMoney(varValue.toFixed(2), 'USD')}
      </div>
      <div data-testid="es-value" className="text-xs text-slate-500 mt-1">
        ES: {formatMoney(expectedShortfall.toFixed(2), 'USD')}
      </div>
    </div>
  )
}
