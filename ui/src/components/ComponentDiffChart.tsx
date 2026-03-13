import { useRef } from 'react'
import { Card } from './ui'
import { formatNum } from '../utils/format'
import type { ComponentDiffDto } from '../types'

interface ComponentDiffChartProps {
  diffs: ComponentDiffDto[]
}

const BAR_COLORS = {
  base: {
    bg: 'bg-blue-400 dark:bg-blue-500',
    text: 'text-blue-600 dark:text-blue-400',
  },
  target: {
    bg: 'bg-indigo-400 dark:bg-indigo-500',
    text: 'text-indigo-600 dark:text-indigo-400',
  },
}

export function ComponentDiffChart({ diffs }: ComponentDiffChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)

  if (diffs.length === 0) return null

  const maxVal = Math.max(
    ...diffs.flatMap((d) => [
      Math.abs(Number(d.baseContribution)),
      Math.abs(Number(d.targetContribution)),
    ]),
    1,
  )

  return (
    <Card data-testid="component-diff-chart">
      <div className="space-y-2">
        <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
          Component Breakdown
        </h3>
        <div className="flex gap-4 text-xs mb-2" aria-hidden="true">
          <span className={BAR_COLORS.base.text}>Base</span>
          <span className={BAR_COLORS.target.text}>Target</span>
        </div>
        <div
          ref={containerRef}
          className="min-w-0 space-y-3"
          role="img"
          aria-label="Component breakdown comparison chart"
        >
          {diffs.map((d) => {
            const basePct = (Math.abs(Number(d.baseContribution)) / maxVal) * 100
            const targetPct = (Math.abs(Number(d.targetContribution)) / maxVal) * 100

            return (
              <div key={d.assetClass} className="space-y-1">
                <div className="text-xs text-slate-600 dark:text-slate-300 truncate">
                  {d.assetClass}
                </div>
                {/* Base bar */}
                <div className="flex items-center gap-2">
                  <div className="flex-1 min-w-0">
                    <div
                      className={`h-5 rounded ${BAR_COLORS.base.bg}`}
                      style={{ width: `${basePct}%` }}
                      aria-label={`Base ${d.assetClass}: ${formatNum(d.baseContribution)}`}
                    />
                  </div>
                  <span className="text-xs text-slate-500 dark:text-slate-400 w-16 text-right flex-shrink-0 tabular-nums">
                    {formatNum(d.baseContribution)}
                  </span>
                </div>
                {/* Target bar */}
                <div className="flex items-center gap-2">
                  <div className="flex-1 min-w-0">
                    <div
                      className={`h-5 rounded ${BAR_COLORS.target.bg}`}
                      style={{ width: `${targetPct}%` }}
                      aria-label={`Target ${d.assetClass}: ${formatNum(d.targetContribution)}`}
                    />
                  </div>
                  <span className="text-xs text-slate-500 dark:text-slate-400 w-16 text-right flex-shrink-0 tabular-nums">
                    {formatNum(d.targetContribution)}
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </Card>
  )
}
