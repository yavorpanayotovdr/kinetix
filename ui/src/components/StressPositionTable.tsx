import { X } from 'lucide-react'
import type { PositionStressImpactDto } from '../types'
import { formatCurrency } from '../utils/format'

interface StressPositionTableProps {
  positions: PositionStressImpactDto[]
  assetClassFilter?: string
  onClearFilter?: () => void
}

export function StressPositionTable({ positions, assetClassFilter, onClearFilter }: StressPositionTableProps) {
  if (positions.length === 0) {
    return (
      <div data-testid="stress-position-table">
        <p data-testid="no-positions" className="text-sm text-slate-500">No position-level data available.</p>
      </div>
    )
  }

  const filtered = assetClassFilter
    ? positions.filter((p) => p.assetClass === assetClassFilter)
    : positions

  const sorted = [...filtered].sort((a, b) => Math.abs(Number(b.pnlImpact)) - Math.abs(Number(a.pnlImpact)))

  const totalPnl = sorted.reduce((sum, p) => sum + Number(p.pnlImpact), 0)

  return (
    <div data-testid="stress-position-table">
      {assetClassFilter && (
        <div className="flex items-center gap-2 mb-3">
          <span
            data-testid="filter-pill"
            className="inline-flex items-center gap-1 px-2.5 py-1 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 rounded-full text-xs font-medium"
          >
            {assetClassFilter}
            <button
              data-testid="clear-filter"
              onClick={onClearFilter}
              className="hover:text-indigo-900 dark:hover:text-indigo-100"
              aria-label={`Clear ${assetClassFilter} filter`}
            >
              <X className="h-3 w-3" />
            </button>
          </span>
        </div>
      )}

      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-slate-600 dark:text-slate-400">
            <th className="py-2">Instrument</th>
            <th className="py-2">Asset Class</th>
            <th className="py-2 text-right">Base MV</th>
            <th className="py-2 text-right">Stressed MV</th>
            <th className="py-2 text-right">P&amp;L Impact</th>
            <th className="py-2 text-right">% of Total</th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((p) => (
            <tr
              key={p.instrumentId}
              data-testid="position-row"
              className="border-b hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
            >
              <td className="py-1.5 font-medium">{p.instrumentId}</td>
              <td className="py-1.5 text-slate-600 dark:text-slate-400">{p.assetClass}</td>
              <td className="py-1.5 text-right">{formatCurrency(p.baseMarketValue)}</td>
              <td className="py-1.5 text-right">{formatCurrency(p.stressedMarketValue)}</td>
              <td className="py-1.5 text-right font-medium text-red-600 dark:text-red-400">
                {formatCurrency(p.pnlImpact)}
              </td>
              <td data-testid="pct-of-total" className="py-1.5 text-right text-slate-600 dark:text-slate-400">
                {Number(p.percentageOfTotal).toFixed(1)}%
              </td>
            </tr>
          ))}
          <tr data-testid="total-row" className="border-t-2 font-semibold">
            <td className="py-2">Total</td>
            <td></td>
            <td></td>
            <td></td>
            <td className="py-2 text-right text-red-600 dark:text-red-400">{formatCurrency(totalPnl)}</td>
            <td></td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}
