import { Card } from './ui'
import { formatNum } from '../utils/format'
import type { RunSnapshotDto } from '../types'

interface RunSnapshotCardProps {
  snapshot: RunSnapshotDto
  title?: string
}

export function RunSnapshotCard({ snapshot, title }: RunSnapshotCardProps) {
  return (
    <Card data-testid="run-snapshot-card">
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">
            {title ?? snapshot.label}
          </h3>
          <span className="text-xs text-slate-500 dark:text-slate-400">
            {snapshot.valuationDate}
          </span>
        </div>
        {snapshot.calcType && (
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {snapshot.calcType} / {snapshot.confLevel}
          </p>
        )}
        <div className="grid grid-cols-3 gap-2 text-sm">
          <div>
            <span className="text-xs text-slate-500 dark:text-slate-400 block">VaR</span>
            <span data-testid="snapshot-var" className="font-medium text-slate-700 dark:text-slate-200">
              {snapshot.varValue != null ? formatNum(snapshot.varValue) : '—'}
            </span>
          </div>
          <div>
            <span className="text-xs text-slate-500 dark:text-slate-400 block">ES</span>
            <span data-testid="snapshot-es" className="font-medium text-slate-700 dark:text-slate-200">
              {snapshot.es != null ? formatNum(snapshot.es) : '—'}
            </span>
          </div>
          <div>
            <span className="text-xs text-slate-500 dark:text-slate-400 block">PV</span>
            <span data-testid="snapshot-pv" className="font-medium text-slate-700 dark:text-slate-200">
              {snapshot.pv != null ? formatNum(snapshot.pv) : '—'}
            </span>
          </div>
        </div>
        <div className="grid grid-cols-5 gap-2 text-xs">
          {[
            { label: 'Delta', value: snapshot.delta },
            { label: 'Gamma', value: snapshot.gamma },
            { label: 'Vega', value: snapshot.vega },
            { label: 'Theta', value: snapshot.theta },
            { label: 'Rho', value: snapshot.rho },
          ].map((g) => (
            <div key={g.label}>
              <span className="text-slate-500 dark:text-slate-400 block">{g.label}</span>
              <span className="text-slate-700 dark:text-slate-200">
                {g.value != null ? formatNum(g.value) : '—'}
              </span>
            </div>
          ))}
        </div>
      </div>
    </Card>
  )
}
