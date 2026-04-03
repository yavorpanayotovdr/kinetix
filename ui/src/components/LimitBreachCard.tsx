import { AlertTriangle } from 'lucide-react'
import type { StressLimitBreachDto } from '../types'
import { formatCurrency } from '../utils/format'
import { formatCompactCurrency } from '../utils/formatCompactCurrency'

interface LimitBreachCardProps {
  breaches: StressLimitBreachDto[]
}

const SEVERITY_STYLES: Record<string, string> = {
  OK: 'bg-green-100 text-green-800',
  WARNING: 'bg-yellow-100 text-yellow-800',
  BREACHED: 'bg-red-100 text-red-800',
}

function utilizationPct(limitValue: string, stressedValue: string): number {
  const limit = Number(limitValue)
  if (limit === 0) return 0
  return Math.round((Number(stressedValue) / limit) * 100)
}

function barColor(severity: string): string {
  if (severity === 'BREACHED') return 'bg-red-500'
  if (severity === 'WARNING') return 'bg-amber-500'
  return 'bg-green-500'
}

export function LimitBreachCard({ breaches }: LimitBreachCardProps) {
  if (breaches.length === 0) {
    return (
      <div data-testid="limit-breach-card">
        <p data-testid="no-breaches" className="text-sm text-green-600 dark:text-green-400">
          No limit breaches under this scenario.
        </p>
      </div>
    )
  }

  return (
    <div data-testid="limit-breach-card" className="mt-4">
      <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300 mb-2 flex items-center gap-1.5">
        <AlertTriangle className="h-4 w-4 text-amber-500" />
        Limit Breaches
      </h3>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-slate-600 dark:text-slate-400">
            <th className="py-2">Limit Type</th>
            <th className="py-2">Level</th>
            <th className="py-2 text-right">Limit Value</th>
            <th className="py-2 text-right">Stressed Value</th>
            <th className="py-2 w-32">Utilization</th>
            <th className="py-2">Status</th>
          </tr>
        </thead>
        <tbody>
          {breaches.map((breach, i) => {
            const pct = utilizationPct(breach.limitValue, breach.stressedValue)
            return (
              <tr
                key={`${breach.limitType}-${breach.limitLevel}-${i}`}
                data-testid="breach-row"
                className="border-b hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
              >
                <td className="py-1.5 font-medium">{breach.limitType}</td>
                <td className="py-1.5 text-slate-600 dark:text-slate-400">{breach.limitLevel}</td>
                <td className="py-1.5 text-right" title={formatCurrency(breach.limitValue)}>{formatCompactCurrency(Number(breach.limitValue))}</td>
                <td className="py-1.5 text-right font-medium" title={formatCurrency(breach.stressedValue)}>{formatCompactCurrency(Number(breach.stressedValue))}</td>
                <td className="py-1.5">
                  <div
                    className="h-2 w-full bg-slate-200 dark:bg-slate-700 rounded overflow-hidden"
                    aria-label={`Utilization: ${pct}%`}
                  >
                    <div
                      data-testid={`utilization-bar-${i}`}
                      className={`h-2 rounded ${barColor(breach.breachSeverity)}`}
                      style={{ width: `${Math.min(pct, 100)}%` }}
                    />
                  </div>
                </td>
                <td className="py-1.5">
                  <span
                    data-testid={`severity-badge-${i}`}
                    className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${SEVERITY_STYLES[breach.breachSeverity] || SEVERITY_STYLES.OK}`}
                  >
                    {breach.breachSeverity}
                  </span>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
