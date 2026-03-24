import type { HierarchyNodeRiskDto } from '../types'

interface RiskBudgetPanelProps {
  node: HierarchyNodeRiskDto
}

export function RiskBudgetPanel({ node }: RiskBudgetPanelProps) {
  const { limitUtilisation } = node

  if (limitUtilisation === null) return null

  const pct = Number(limitUtilisation)
  const displayPct = `${pct.toFixed(1)}%`
  const barWidth = `${Math.min(pct, 100)}%`

  const fillColor = pct >= 100
    ? 'bg-red-500 dark:bg-red-400'
    : pct >= 80
    ? 'bg-amber-400 dark:bg-amber-300'
    : 'bg-emerald-500 dark:bg-emerald-400'

  return (
    <div data-testid="risk-budget-panel" className="p-3 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-surface-800">
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-medium text-slate-600 dark:text-slate-400 uppercase tracking-wide">
          VaR Budget Utilisation
        </span>
        <span className="text-sm font-semibold tabular-nums text-slate-800 dark:text-slate-200">
          {displayPct}
        </span>
      </div>
      <div data-testid="budget-utilisation-bar" className="h-2 w-full rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden">
        <div
          data-testid="budget-bar-fill"
          className={`h-full rounded-full transition-all ${fillColor}`}
          style={{ width: barWidth }}
        />
      </div>
    </div>
  )
}
