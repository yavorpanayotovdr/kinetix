import { ArrowDown, ArrowUp } from 'lucide-react'
import { Card } from './ui'
import { formatNum } from '../utils/format'
import { changeColorClass } from '../utils/changeIndicators'
import type { BookDiffDto } from '../types'

interface RunDiffSummaryProps {
  diff: BookDiffDto
}

function ChangeIcon({ value }: { value: number }) {
  if (value < 0) return <ArrowDown className="inline h-3.5 w-3.5" aria-hidden="true" />
  if (value > 0) return <ArrowUp className="inline h-3.5 w-3.5" aria-hidden="true" />
  return null
}

export function RunDiffSummary({ diff }: RunDiffSummaryProps) {
  const metrics = [
    { label: 'VaR', change: diff.varChange, pct: diff.varChangePercent },
    { label: 'ES', change: diff.esChange, pct: diff.esChangePercent },
    { label: 'PV', change: diff.pvChange, pct: null },
    { label: 'Delta', change: diff.deltaChange, pct: null },
    { label: 'Gamma', change: diff.gammaChange, pct: null },
    { label: 'Vega', change: diff.vegaChange, pct: null },
    { label: 'Theta', change: diff.thetaChange, pct: null },
    { label: 'Rho', change: diff.rhoChange, pct: null },
  ]

  return (
    <Card data-testid="run-diff-summary">
      <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">
        Changes
      </h3>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-xs text-slate-500 dark:text-slate-400">
            <th className="text-left py-1 font-medium">Metric</th>
            <th className="text-right py-1 font-medium">Change</th>
            <th className="text-right py-1 font-medium">%</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 dark:divide-surface-700">
          {metrics.map((m) => {
            const val = Number(m.change)
            return (
              <tr key={m.label}>
                <td className="py-1.5 text-slate-700 dark:text-slate-200 font-medium">{m.label}</td>
                <td
                  data-testid={`diff-${m.label.toLowerCase()}`}
                  className={`py-1.5 text-right font-medium ${changeColorClass(val)}`}
                >
                  <ChangeIcon value={val} /> {formatNum(m.change)}
                </td>
                <td className="py-1.5 text-right text-slate-500 dark:text-slate-400">
                  {m.pct != null ? `${formatNum(m.pct)}%` : '—'}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </Card>
  )
}
