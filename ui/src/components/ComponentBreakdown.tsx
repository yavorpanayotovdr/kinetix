import type { ComponentBreakdownDto } from '../types'
import { formatMoney } from '../utils/format'

interface ComponentBreakdownProps {
  breakdown: ComponentBreakdownDto[]
}

const ASSET_CLASS_COLORS: Record<string, string> = {
  EQUITY: '#3b82f6',
  FIXED_INCOME: '#22c55e',
  COMMODITY: '#f59e0b',
  FX: '#a855f7',
}

const DEFAULT_COLOR = '#9ca3af'

function formatAssetClassLabel(assetClass: string): string {
  return assetClass
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ')
}

export function ComponentBreakdown({ breakdown }: ComponentBreakdownProps) {
  const sorted = [...breakdown].sort(
    (a, b) => Number(b.percentageOfTotal) - Number(a.percentageOfTotal),
  )

  const radius = 40
  const center = 60
  const strokeWidth = 20
  const circumference = 2 * Math.PI * radius

  const segments = sorted.reduce<{ comp: ComponentBreakdownDto; offset: number }[]>(
    (acc, comp) => {
      const prevOffset = acc.length > 0 ? acc[acc.length - 1].offset + Number(acc[acc.length - 1].comp.percentageOfTotal) / 100 : 0
      return [...acc, { comp, offset: prevOffset }]
    },
    [],
  )

  return (
    <div>
      <h3 className="text-sm font-semibold text-slate-700 mb-2">Component Breakdown</h3>

      <div className="flex flex-col items-center">
        <svg viewBox={`0 0 ${center * 2} ${center * 2}`} width="120" height="120">
          {segments.map(({ comp, offset }) => {
            const pct = Number(comp.percentageOfTotal) / 100
            const dashLength = pct * circumference

            return (
              <circle
                key={comp.assetClass}
                data-testid={`breakdown-segment-${comp.assetClass}`}
                cx={center}
                cy={center}
                r={radius}
                fill="none"
                stroke={ASSET_CLASS_COLORS[comp.assetClass] || DEFAULT_COLOR}
                strokeWidth={strokeWidth}
                strokeDasharray={`${dashLength} ${circumference - dashLength}`}
                strokeDashoffset={-offset * circumference}
                transform={`rotate(-90 ${center} ${center})`}
              />
            )
          })}
        </svg>

        <div className="w-full mt-3 space-y-1 text-xs">
          {sorted.map((comp) => (
            <div
              key={comp.assetClass}
              data-testid={`breakdown-${comp.assetClass}`}
              className="flex items-center gap-2"
            >
              <span
                className="inline-block w-2 h-2 rounded-full flex-shrink-0"
                style={{ backgroundColor: ASSET_CLASS_COLORS[comp.assetClass] || DEFAULT_COLOR }}
              />
              <span className="flex-1 text-slate-600">{formatAssetClassLabel(comp.assetClass)}</span>
              <span className="text-slate-700 font-medium tabular-nums">
                {formatMoney(comp.varContribution, 'USD')}
              </span>
              <span className="text-slate-500 tabular-nums w-14 text-right">
                {comp.percentageOfTotal}%
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
