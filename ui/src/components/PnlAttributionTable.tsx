import { Fragment, useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import type { PnlAttributionDto, PositionPnlAttributionDto } from '../types'
import { formatNum, pnlColorClass } from '../utils/format'

interface PnlAttributionTableProps {
  data: PnlAttributionDto
}

interface FactorDef {
  key: string
  label: string
  portfolioField: keyof PnlAttributionDto
  positionField: keyof PositionPnlAttributionDto
}

const FACTORS: FactorDef[] = [
  { key: 'delta', label: 'Delta', portfolioField: 'deltaPnl', positionField: 'deltaPnl' },
  { key: 'gamma', label: 'Gamma', portfolioField: 'gammaPnl', positionField: 'gammaPnl' },
  { key: 'vega', label: 'Vega', portfolioField: 'vegaPnl', positionField: 'vegaPnl' },
  { key: 'theta', label: 'Theta', portfolioField: 'thetaPnl', positionField: 'thetaPnl' },
  { key: 'rho', label: 'Rho', portfolioField: 'rhoPnl', positionField: 'rhoPnl' },
  { key: 'unexplained', label: 'Unexplained', portfolioField: 'unexplainedPnl', positionField: 'unexplainedPnl' },
]

const FACTOR_COLORS: Record<string, string> = {
  delta: '#3b82f6',
  gamma: '#8b5cf6',
  vega: '#a855f7',
  theta: '#f59e0b',
  rho: '#22c55e',
  unexplained: '#9ca3af',
}

export function PnlAttributionTable({ data }: PnlAttributionTableProps) {
  const [expandedFactor, setExpandedFactor] = useState<string | null>(null)

  const totalPnl = Number(data.totalPnl)

  const toggleFactor = (key: string) => {
    setExpandedFactor((prev) => (prev === key ? null : key))
  }

  return (
    <div data-testid="attribution-table">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200">
            <th className="text-left py-2 px-3 font-medium text-slate-500 w-8" />
            <th className="text-left py-2 px-3 font-medium text-slate-500">Factor</th>
            <th className="text-right py-2 px-3 font-medium text-slate-500">Amount</th>
            <th className="text-right py-2 px-3 font-medium text-slate-500">% of Total</th>
          </tr>
        </thead>
        <tbody>
          {FACTORS.map((factor) => {
            const amount = String(data[factor.portfolioField])
            const amountNum = Number(amount)
            const pctOfTotal = totalPnl !== 0 ? ((amountNum / totalPnl) * 100) : 0
            const isExpanded = expandedFactor === factor.key

            return (
              <Fragment key={factor.key}>
                <tr
                  data-testid={`factor-row-${factor.key}`}
                  onClick={() => toggleFactor(factor.key)}
                  className="border-b border-slate-100 cursor-pointer hover:bg-slate-50 transition-colors"
                >
                  <td className="py-2 px-3">
                    {isExpanded ? (
                      <ChevronDown className="h-4 w-4 text-slate-400" />
                    ) : (
                      <ChevronRight className="h-4 w-4 text-slate-400" />
                    )}
                  </td>
                  <td className="py-2 px-3">
                    <div className="flex items-center gap-2">
                      <span
                        className="inline-block w-3 h-3 rounded-sm"
                        style={{ backgroundColor: FACTOR_COLORS[factor.key] }}
                      />
                      <span className="font-medium text-slate-700">{factor.label}</span>
                    </div>
                  </td>
                  <td className="py-2 px-3 text-right">
                    <span
                      data-testid={`factor-amount-${factor.key}`}
                      className={`font-mono tabular-nums ${pnlColorClass(amount)}`}
                    >
                      {formatNum(amountNum)}
                    </span>
                  </td>
                  <td className="py-2 px-3 text-right font-mono tabular-nums text-slate-600">
                    {pctOfTotal.toFixed(1)}%
                  </td>
                </tr>

                {isExpanded && data.positionAttributions.map((pos) => {
                  const posAmount = String(pos[factor.positionField])
                  const posAmountNum = Number(posAmount)

                  return (
                    <tr
                      key={`${factor.key}-${pos.instrumentId}`}
                      data-testid={`position-detail-${factor.key}-${pos.instrumentId}`}
                      className="bg-slate-50 border-b border-slate-100"
                    >
                      <td className="py-1.5 px-3" />
                      <td className="py-1.5 px-3 pl-10 text-slate-500">
                        {pos.instrumentId}
                        <span className="ml-2 text-xs text-slate-400">{pos.assetClass}</span>
                      </td>
                      <td className={`py-1.5 px-3 text-right font-mono tabular-nums ${pnlColorClass(posAmount)}`}>
                        {formatNum(posAmountNum)}
                      </td>
                      <td className="py-1.5 px-3 text-right font-mono tabular-nums text-slate-400">
                        {totalPnl !== 0 ? ((posAmountNum / totalPnl) * 100).toFixed(1) : '0.0'}%
                      </td>
                    </tr>
                  )
                })}
              </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
