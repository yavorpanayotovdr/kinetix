import { useState, useMemo } from 'react'
import { Card } from './ui'
import { formatNum } from '../utils/format'
import { changeColorClass } from '../utils/changeIndicators'
import type { PositionDiffDto } from '../types'

interface PositionDiffTableProps {
  diffs: PositionDiffDto[]
  threshold: number
  onThresholdChange: (threshold: number) => void
}

function ChangeBadge({ changeType }: { changeType: string }) {
  switch (changeType) {
    case 'NEW':
      return (
        <span className="inline-block px-1.5 py-0.5 text-xs font-medium rounded bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400">
          NEW
        </span>
      )
    case 'REMOVED':
      return (
        <span className="inline-block px-1.5 py-0.5 text-xs font-medium rounded bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400">
          REMOVED
        </span>
      )
    case 'MODIFIED':
      return (
        <span className="inline-block px-1.5 py-0.5 text-xs font-medium rounded bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400">
          MODIFIED
        </span>
      )
    default:
      return (
        <span className="inline-block px-1.5 py-0.5 text-xs font-medium rounded bg-slate-100 dark:bg-surface-700 text-slate-500 dark:text-slate-400">
          —
        </span>
      )
  }
}

type SortField = 'instrumentId' | 'marketValueChange' | 'varContributionChange'

export function PositionDiffTable({ diffs, threshold, onThresholdChange }: PositionDiffTableProps) {
  const [sortField, setSortField] = useState<SortField>('marketValueChange')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc')

  const filtered = useMemo(() => {
    const result = diffs.filter(
      (d) => Math.abs(Number(d.marketValueChange)) >= threshold,
    )
    return result.sort((a, b) => {
      const av = sortField === 'instrumentId' ? a.instrumentId : Number(a[sortField])
      const bv = sortField === 'instrumentId' ? b.instrumentId : Number(b[sortField])
      if (av < bv) return sortDir === 'asc' ? -1 : 1
      if (av > bv) return sortDir === 'asc' ? 1 : -1
      return 0
    })
  }, [diffs, threshold, sortField, sortDir])

  const toggleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortField(field)
      setSortDir('desc')
    }
  }

  const sortIndicator = (field: SortField) => {
    if (sortField !== field) return null
    return sortDir === 'asc' ? ' ↑' : ' ↓'
  }

  return (
    <Card data-testid="position-diff-table">
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
            Position Changes
          </h3>
          <div className="flex items-center gap-2">
            <label
              htmlFor="position-diff-threshold"
              className="text-xs text-slate-500 dark:text-slate-400"
            >
              Min change:
            </label>
            <input
              id="position-diff-threshold"
              data-testid="threshold-slider"
              type="range"
              min="0"
              max="100000"
              step="1000"
              value={threshold}
              onChange={(e) => onThresholdChange(Number(e.target.value))}
              className="w-24"
              aria-label="Minimum market value change threshold"
            />
            <span className="text-xs text-slate-500 dark:text-slate-400 w-16 text-right">
              {formatNum(threshold)}
            </span>
          </div>
        </div>
        {filtered.length === 0 ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No position changes above threshold
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-xs text-slate-500 dark:text-slate-400">
                  <th
                    className="text-left py-1 font-medium cursor-pointer hover:text-slate-700 dark:hover:text-slate-300 select-none"
                    onClick={() => toggleSort('instrumentId')}
                    aria-sort={sortField === 'instrumentId' ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}
                  >
                    Instrument{sortIndicator('instrumentId')}
                  </th>
                  <th className="text-left py-1 font-medium">Type</th>
                  <th className="text-right py-1 font-medium">Base MV</th>
                  <th className="text-right py-1 font-medium">Target MV</th>
                  <th
                    className="text-right py-1 font-medium cursor-pointer hover:text-slate-700 dark:hover:text-slate-300 select-none"
                    onClick={() => toggleSort('marketValueChange')}
                    aria-sort={sortField === 'marketValueChange' ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}
                  >
                    MV Change{sortIndicator('marketValueChange')}
                  </th>
                  <th
                    className="text-right py-1 font-medium cursor-pointer hover:text-slate-700 dark:hover:text-slate-300 select-none"
                    onClick={() => toggleSort('varContributionChange')}
                    aria-sort={sortField === 'varContributionChange' ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none'}
                  >
                    VaR Contrib Chg{sortIndicator('varContributionChange')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-surface-700">
                {filtered.map((d) => (
                  <tr key={d.instrumentId}>
                    <td className="py-1.5 text-slate-700 dark:text-slate-200 font-medium">
                      {d.instrumentId}
                    </td>
                    <td className="py-1.5">
                      <ChangeBadge changeType={d.changeType} />
                    </td>
                    <td className="py-1.5 text-right text-slate-700 dark:text-slate-200">
                      {formatNum(d.baseMarketValue)}
                    </td>
                    <td className="py-1.5 text-right text-slate-700 dark:text-slate-200">
                      {formatNum(d.targetMarketValue)}
                    </td>
                    <td
                      className={`py-1.5 text-right font-medium ${changeColorClass(Number(d.marketValueChange))}`}
                    >
                      {formatNum(d.marketValueChange)}
                    </td>
                    <td
                      className={`py-1.5 text-right font-medium ${changeColorClass(Number(d.varContributionChange))}`}
                    >
                      {formatNum(d.varContributionChange)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Card>
  )
}
