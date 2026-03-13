import { Star } from 'lucide-react'
import { Badge, Card } from './ui'
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
          <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1.5">
            {title ?? snapshot.label}
            {snapshot.label.includes('Official EOD') && (
              <Badge variant="eod" data-testid="snapshot-eod-badge">
                <Star className="h-3 w-3 mr-0.5 inline" />EOD
              </Badge>
            )}
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
          <div className="min-w-0">
            <span className="text-xs text-slate-500 dark:text-slate-400 block">VaR</span>
            <span data-testid="snapshot-var" className="font-medium text-slate-700 dark:text-slate-200 block truncate" title={snapshot.varValue != null ? String(snapshot.varValue) : undefined}>
              {snapshot.varValue != null ? formatNum(snapshot.varValue) : '—'}
            </span>
          </div>
          <div className="min-w-0">
            <span className="text-xs text-slate-500 dark:text-slate-400 block">ES</span>
            <span data-testid="snapshot-es" className="font-medium text-slate-700 dark:text-slate-200 block truncate" title={snapshot.es != null ? String(snapshot.es) : undefined}>
              {snapshot.es != null ? formatNum(snapshot.es) : '—'}
            </span>
          </div>
          <div className="min-w-0">
            <span className="text-xs text-slate-500 dark:text-slate-400 block">PV</span>
            <span data-testid="snapshot-pv" className="font-medium text-slate-700 dark:text-slate-200 block truncate" title={snapshot.pv != null ? String(snapshot.pv) : undefined}>
              {snapshot.pv != null ? formatNum(snapshot.pv) : '—'}
            </span>
          </div>
        </div>
        <div className="overflow-x-auto">
          <div className="grid grid-cols-5 gap-2 text-xs">
            {[
              { label: 'Delta', value: snapshot.delta },
              { label: 'Gamma', value: snapshot.gamma },
              { label: 'Vega', value: snapshot.vega },
              { label: 'Theta', value: snapshot.theta },
              { label: 'Rho', value: snapshot.rho },
            ].map((g) => (
              <div key={g.label} className="min-w-0">
                <span className="text-slate-500 dark:text-slate-400 block">{g.label}</span>
                <span className="text-slate-700 dark:text-slate-200 block truncate" title={g.value != null ? String(g.value) : undefined}>
                  {g.value != null ? formatNum(g.value) : '—'}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </Card>
  )
}
