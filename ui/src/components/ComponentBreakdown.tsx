import { useMemo } from 'react'
import type { ComponentBreakdownDto } from '../types'
import { formatAssetClassLabel } from '../utils/formatAssetClass'
import { formatMoney } from '../utils/format'

interface ComponentBreakdownProps {
  breakdown: ComponentBreakdownDto[]
  portfolioVaR?: string
}

const ASSET_CLASS_COLORS: Record<string, string> = {
  EQUITY: '#3b82f6',
  FIXED_INCOME: '#22c55e',
  COMMODITY: '#f59e0b',
  FX: '#a855f7',
}

const DEFAULT_COLOR = '#9ca3af'

export function ComponentBreakdown({ breakdown, portfolioVaR }: ComponentBreakdownProps) {
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

  const diversification = useMemo(() => {
    if (!portfolioVaR || breakdown.length === 0) return null
    const sumComponentVaR = breakdown.reduce((sum, c) => sum + Number(c.varContribution), 0)
    const portfolioValue = Number(portfolioVaR)
    const benefit = sumComponentVaR - portfolioValue
    const pct = sumComponentVaR !== 0 ? (benefit / sumComponentVaR) * 100 : 0
    return { benefit, pct }
  }, [breakdown, portfolioVaR])

  return (
    <div>
      <h3 className="text-sm font-semibold text-slate-700 mb-3">Component Breakdown</h3>

      <div className="flex items-center gap-8">
        <div className="space-y-2.5">
          {sorted.map((comp) => (
            <div
              key={comp.assetClass}
              data-testid={`breakdown-${comp.assetClass}`}
              className="flex items-center gap-2"
            >
              <span
                className="inline-block w-2.5 h-2.5 rounded-full flex-shrink-0"
                style={{ backgroundColor: ASSET_CLASS_COLORS[comp.assetClass] || DEFAULT_COLOR }}
              />
              <div className="flex flex-col">
                <span className="text-xs text-slate-500 leading-tight">
                  {formatAssetClassLabel(comp.assetClass)}
                </span>
                <div className="flex items-baseline gap-2">
                  <span className="text-sm font-semibold text-slate-800 tabular-nums leading-tight">
                    {formatMoney(comp.varContribution, 'USD')}
                  </span>
                  <span className="text-xs text-slate-400 tabular-nums leading-tight">
                    {comp.percentageOfTotal}%
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="flex-shrink-0">
          <svg viewBox={`0 0 ${center * 2} ${center * 2}`} width="130" height="130">
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
        </div>
      </div>

      {diversification && (
        <div data-testid="diversification-benefit" className="mt-3 text-xs">
          <span className="text-slate-500">Diversification </span>
          <span data-testid="diversification-amount" className="font-medium text-green-600 tabular-nums">
            -{formatMoney(diversification.benefit.toFixed(2), 'USD')}
          </span>
          <span className="text-slate-400 ml-1 tabular-nums">
            ({diversification.pct.toFixed(2)}%)
          </span>
        </div>
      )}
    </div>
  )
}
