import { useMemo, useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import type { PositionRiskDto } from '../types'
import { formatNum } from '../utils/format'
import { Card, Spinner } from './ui'

type SortField =
  | 'marketValue'
  | 'delta'
  | 'gamma'
  | 'vega'
  | 'varContribution'
  | 'esContribution'
  | 'percentageOfTotal'
type SortDirection = 'asc' | 'desc'

interface PositionRiskTableProps {
  data: PositionRiskDto[]
  loading: boolean
}

function numericValue(row: PositionRiskDto, field: SortField, useAbsolute: boolean): number {
  const raw = row[field]
  if (raw == null) return -Infinity
  const num = Number(raw)
  return useAbsolute ? Math.abs(num) : num
}

function pctColorClass(pct: number): string {
  if (pct > 30) return 'text-red-600'
  if (pct > 15) return 'text-amber-600'
  return ''
}

const COLUMNS: { label: string; field: SortField; sortable: true }[] = [
  { label: 'Market Value', field: 'marketValue', sortable: true },
  { label: 'Delta', field: 'delta', sortable: true },
  { label: 'Gamma', field: 'gamma', sortable: true },
  { label: 'Vega', field: 'vega', sortable: true },
  { label: 'VaR Contribution', field: 'varContribution', sortable: true },
  { label: 'ES Contribution', field: 'esContribution', sortable: true },
  { label: '% of Total', field: 'percentageOfTotal', sortable: true },
]

export function PositionRiskTable({ data, loading }: PositionRiskTableProps) {
  const [expanded, setExpanded] = useState(true)
  const [sortField, setSortField] = useState<SortField>('varContribution')
  const [sortDir, setSortDir] = useState<SortDirection>('desc')
  const [useAbsoluteSort, setUseAbsoluteSort] = useState(true)

  const sorted = useMemo(() => {
    return [...data].sort((a, b) => {
      const valA = numericValue(a, sortField, useAbsoluteSort)
      const valB = numericValue(b, sortField, useAbsoluteSort)
      return sortDir === 'desc' ? valB - valA : valA - valB
    })
  }, [data, sortField, sortDir, useAbsoluteSort])

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir((prev) => (prev === 'desc' ? 'asc' : 'desc'))
    } else {
      setSortField(field)
      setSortDir('desc')
      setUseAbsoluteSort(field === 'varContribution')
    }
  }

  const sortIcon = (field: SortField) => {
    if (sortField !== field) return null
    return sortDir === 'desc'
      ? <ChevronDown className="inline h-3 w-3" />
      : <ChevronUp className="inline h-3 w-3" />
  }

  return (
    <Card data-testid="position-risk-section">
      <div className="-mx-4 -my-4">
        <button
          data-testid="position-risk-toggle"
          onClick={() => setExpanded((prev) => !prev)}
          className="w-full flex items-center justify-between px-4 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
        >
          <span>Position Risk Breakdown</span>
          {expanded
            ? <ChevronUp className="h-4 w-4 text-slate-400" />
            : <ChevronDown className="h-4 w-4 text-slate-400" />}
        </button>

        {loading && (
          <div data-testid="position-risk-loading" className="flex items-center justify-center py-8">
            <Spinner />
          </div>
        )}

        {!loading && data.length === 0 && (
          <div data-testid="position-risk-empty" className="text-sm text-slate-400 py-6 text-center">
            No position risk data available.
          </div>
        )}

        {!loading && data.length > 0 && expanded && (
          <div data-testid="position-risk-table" className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <thead>
                <tr className="bg-slate-50">
                  <th className="px-4 py-2 text-left font-semibold text-slate-700">Instrument</th>
                  <th className="px-4 py-2 text-left font-semibold text-slate-700">Asset Class</th>
                  {COLUMNS.map((col) => (
                    <th
                      key={col.field}
                      data-testid={`sort-${col.field}`}
                      className="px-4 py-2 text-right font-semibold text-slate-700 cursor-pointer select-none"
                      onClick={() => handleSort(col.field)}
                    >
                      {col.label} {sortIcon(col.field)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sorted.map((row) => {
                  const pct = Number(row.percentageOfTotal)
                  return (
                    <tr
                      key={row.instrumentId}
                      data-testid={`position-risk-row-${row.instrumentId}`}
                      className="hover:bg-slate-50 transition-colors"
                    >
                      <td className="px-4 py-2 font-medium">{row.instrumentId}</td>
                      <td className="px-4 py-2 text-slate-600">{row.assetClass}</td>
                      <td className="px-4 py-2 text-right tabular-nums">{formatNum(row.marketValue)}</td>
                      <td className="px-4 py-2 text-right tabular-nums">
                        {row.delta != null ? formatNum(row.delta) : '\u2014'}
                      </td>
                      <td className="px-4 py-2 text-right tabular-nums">
                        {row.gamma != null ? formatNum(row.gamma) : '\u2014'}
                      </td>
                      <td className="px-4 py-2 text-right tabular-nums">
                        {row.vega != null ? formatNum(row.vega) : '\u2014'}
                      </td>
                      <td className="px-4 py-2 text-right tabular-nums">{formatNum(row.varContribution)}</td>
                      <td className="px-4 py-2 text-right tabular-nums">{formatNum(row.esContribution)}</td>
                      <td
                        data-testid={`pct-total-${row.instrumentId}`}
                        className={`px-4 py-2 text-right tabular-nums font-medium ${pctColorClass(pct)}`}
                      >
                        {formatNum(row.percentageOfTotal)}%
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Card>
  )
}
