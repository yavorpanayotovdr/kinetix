import React, { useMemo, useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import type { PositionRiskDto } from '../types'
import { formatNum } from '../utils/format'
import { formatAssetClassLabel } from '../utils/formatAssetClass'
import { Card, Spinner } from './ui'

type SortField =
  | 'marketValue'
  | 'delta'
  | 'gamma'
  | 'vega'
  | 'theta'
  | 'rho'
  | 'varContribution'
  | 'esContribution'
  | 'percentageOfTotal'
type SortDirection = 'asc' | 'desc'

interface PositionRiskTableProps {
  data: PositionRiskDto[]
  loading: boolean
  error?: string | null
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
  { label: 'Theta', field: 'theta', sortable: true },
  { label: 'Rho', field: 'rho', sortable: true },
  { label: 'VaR Contribution', field: 'varContribution', sortable: true },
  { label: 'ES Contribution', field: 'esContribution', sortable: true },
  { label: '% of Total', field: 'percentageOfTotal', sortable: true },
]

export function PositionRiskTable({ data, loading, error }: PositionRiskTableProps) {
  const [expanded, setExpanded] = useState(true)
  const [sortField, setSortField] = useState<SortField>('varContribution')
  const [sortDir, setSortDir] = useState<SortDirection>('desc')
  const [useAbsoluteSort, setUseAbsoluteSort] = useState(true)
  const [expandedRow, setExpandedRow] = useState<string | null>(null)

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

        {!loading && error && (
          <div data-testid="position-risk-error" className="text-sm text-red-600 py-6 text-center px-4">
            {error}
          </div>
        )}

        {!loading && !error && data.length === 0 && (
          <div data-testid="position-risk-empty" className="text-sm text-slate-400 py-6 text-center">
            No position risk data available.
          </div>
        )}

        {!loading && !error && data.length > 0 && expanded && (
          <div data-testid="position-risk-table" className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-slate-500 border-b border-slate-200">
                  <th className="py-2 pr-3 pl-4">Instrument</th>
                  <th className="py-2 pr-3">Asset Class</th>
                  {COLUMNS.map((col) => (
                    <th
                      key={col.field}
                      data-testid={`sort-${col.field}`}
                      className="py-2 pr-3 text-right cursor-pointer select-none"
                      onClick={() => handleSort(col.field)}
                    >
                      {col.label} {sortIcon(col.field)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {sorted.map((row) => {
                  const pct = Number(row.percentageOfTotal)
                  const isExpanded = expandedRow === row.instrumentId
                  return (
                    <React.Fragment key={row.instrumentId}>
                      <tr
                        data-testid={`position-risk-row-${row.instrumentId}`}
                        className={`hover:bg-slate-50 transition-colors border-b border-slate-100 cursor-pointer ${isExpanded ? 'bg-slate-50' : ''}`}
                        onClick={() => setExpandedRow(isExpanded ? null : row.instrumentId)}
                      >
                        <td className="py-2 pr-3 pl-4 font-medium">{row.instrumentId}</td>
                        <td className="py-2 pr-3 text-slate-600">{formatAssetClassLabel(row.assetClass)}</td>
                        <td className="py-2 pr-3 text-right font-mono">{formatNum(row.marketValue)}</td>
                        <td className="py-2 pr-3 text-right font-mono">
                          {row.delta != null ? formatNum(row.delta) : '\u2014'}
                        </td>
                        <td className="py-2 pr-3 text-right font-mono">
                          {row.gamma != null ? formatNum(row.gamma) : '\u2014'}
                        </td>
                        <td className="py-2 pr-3 text-right font-mono">
                          {row.vega != null ? formatNum(row.vega) : '\u2014'}
                        </td>
                        <td className="py-2 pr-3 text-right font-mono">
                          {row.theta != null ? formatNum(row.theta) : '\u2014'}
                        </td>
                        <td className="py-2 pr-3 text-right font-mono">
                          {row.rho != null ? formatNum(row.rho) : '\u2014'}
                        </td>
                        <td className="py-2 pr-3 text-right font-mono">{formatNum(row.varContribution)}</td>
                        <td className="py-2 pr-3 text-right font-mono">{formatNum(row.esContribution)}</td>
                        <td
                          data-testid={`pct-total-${row.instrumentId}`}
                          className={`py-2 pr-3 text-right font-mono font-medium ${pctColorClass(pct)}`}
                        >
                          {formatNum(row.percentageOfTotal)}%
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr data-testid={`position-risk-detail-${row.instrumentId}`}>
                          <td colSpan={11} className="bg-slate-50 px-4 py-3 border-b border-slate-200">
                            <div className="grid grid-cols-4 gap-4 text-xs">
                              <div>
                                <span className="text-slate-500">Market Value</span>
                                <p className="font-mono font-medium">{formatNum(row.marketValue)}</p>
                              </div>
                              <div>
                                <span className="text-slate-500">VaR Contribution</span>
                                <p className="font-mono font-medium">{formatNum(row.varContribution)}</p>
                              </div>
                              <div>
                                <span className="text-slate-500">ES Contribution</span>
                                <p className="font-mono font-medium">{formatNum(row.esContribution)}</p>
                              </div>
                              <div>
                                <span className="text-slate-500">% of Total</span>
                                <p className="font-mono font-medium">{formatNum(row.percentageOfTotal)}%</p>
                              </div>
                              <div>
                                <span className="text-slate-500">Delta</span>
                                <p className="font-mono font-medium">{row.delta != null ? formatNum(row.delta) : '\u2014'}</p>
                              </div>
                              <div>
                                <span className="text-slate-500">Gamma</span>
                                <p className="font-mono font-medium">{row.gamma != null ? formatNum(row.gamma) : '\u2014'}</p>
                              </div>
                              <div>
                                <span className="text-slate-500">Vega</span>
                                <p className="font-mono font-medium">{row.vega != null ? formatNum(row.vega) : '\u2014'}</p>
                              </div>
                              <div>
                                <span className="text-slate-500">Theta</span>
                                <p className="font-mono font-medium">{row.theta != null ? formatNum(row.theta) : '\u2014'}</p>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
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
