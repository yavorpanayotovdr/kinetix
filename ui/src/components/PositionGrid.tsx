import { useEffect, useMemo, useRef, useState } from 'react'
import { ChevronDown, ChevronUp, ChevronLeft, ChevronRight, Wifi, WifiOff, Inbox, Settings, Download } from 'lucide-react'
import type { PositionDto, PositionRiskDto } from '../types'
import { formatMoney, formatNum, formatQuantity, pnlColorClass } from '../utils/format'
import { formatCompactCurrency } from '../utils/formatCompactCurrency'
import { exportToCsv } from '../utils/exportCsv'
import { Card, EmptyState } from './ui'

type SortField = 'delta' | 'gamma' | 'vega' | 'var-pct'
type SortDirection = 'asc' | 'desc'

interface PositionGridProps {
  positions: PositionDto[]
  connected?: boolean
  positionRisk?: PositionRiskDto[]
}

function riskValue(risk: PositionRiskDto | undefined, field: SortField): number {
  if (!risk) return -Infinity
  switch (field) {
    case 'delta': return risk.delta != null ? Number(risk.delta) : -Infinity
    case 'gamma': return risk.gamma != null ? Number(risk.gamma) : -Infinity
    case 'vega': return risk.vega != null ? Number(risk.vega) : -Infinity
    case 'var-pct': return Number(risk.percentageOfTotal)
  }
}

const PAGE_SIZE = 50

const STORAGE_KEY = 'kinetix:column-visibility'

interface ColumnDef {
  key: string
  label: string
  align: 'left' | 'right'
}

const POSITION_COLUMNS: ColumnDef[] = [
  { key: 'instrument', label: 'Instrument', align: 'left' },
  { key: 'assetClass', label: 'Asset Class', align: 'left' },
  { key: 'quantity', label: 'Quantity', align: 'right' },
  { key: 'avgCost', label: 'Avg Cost', align: 'right' },
  { key: 'marketPrice', label: 'Market Price', align: 'right' },
  { key: 'marketValue', label: 'Market Value', align: 'right' },
  { key: 'unrealizedPnl', label: 'Unrealized P&L', align: 'right' },
]

function loadColumnVisibility(): Record<string, boolean> {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) return JSON.parse(stored)
  } catch { /* ignore */ }
  return {}
}

export function PositionGrid({ positions, connected, positionRisk }: PositionGridProps) {
  const [sortField, setSortField] = useState<SortField | null>(null)
  const [sortDir, setSortDir] = useState<SortDirection>('desc')
  const [currentPage, setCurrentPage] = useState(1)
  const [columnVisibility, setColumnVisibility] = useState<Record<string, boolean>>(loadColumnVisibility)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const settingsRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (settingsRef.current && !settingsRef.current.contains(e.target as Node)) {
        setSettingsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const isColumnVisible = (key: string) => columnVisibility[key] !== false

  const toggleColumn = (key: string) => {
    const next = { ...columnVisibility, [key]: !isColumnVisible(key) }
    setColumnVisibility(next)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  }

  const hasRisk = positionRisk != null && positionRisk.length > 0

  const riskByInstrument = useMemo(() => {
    if (!positionRisk) return new Map<string, PositionRiskDto>()
    return new Map(positionRisk.map((r) => [r.instrumentId, r]))
  }, [positionRisk])

  const sortedPositions = useMemo(() => {
    if (!sortField || !hasRisk) return positions
    return [...positions].sort((a, b) => {
      const riskA = riskByInstrument.get(a.instrumentId)
      const riskB = riskByInstrument.get(b.instrumentId)
      const valA = riskValue(riskA, sortField)
      const valB = riskValue(riskB, sortField)
      return sortDir === 'desc' ? valB - valA : valA - valB
    })
  }, [positions, sortField, sortDir, hasRisk, riskByInstrument])

  const totalPages = Math.ceil(sortedPositions.length / PAGE_SIZE)
  const showPagination = totalPages > 1
  const paginatedPositions = useMemo(() => {
    const start = (currentPage - 1) * PAGE_SIZE
    return sortedPositions.slice(start, start + PAGE_SIZE)
  }, [sortedPositions, currentPage])

  if (positions.length === 0) {
    return (
      <Card>
        <EmptyState
          icon={<Inbox className="h-10 w-10" />}
          title="No positions to display."
        />
      </Card>
    )
  }

  const totalMarketValue = positions.reduce(
    (sum, pos) => sum + Number(pos.marketValue.amount),
    0,
  )
  const totalPnl = positions.reduce(
    (sum, pos) => sum + Number(pos.unrealizedPnl.amount),
    0,
  )
  const currency = positions[0].marketValue.currency

  const totalDelta = hasRisk
    ? positionRisk.reduce((sum, r) => sum + (r.delta != null ? Number(r.delta) : 0), 0)
    : null
  const totalVar = hasRisk
    ? positionRisk.reduce((sum, r) => sum + Number(r.varContribution), 0)
    : null

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir((prev) => (prev === 'desc' ? 'asc' : 'desc'))
    } else {
      setSortField(field)
      setSortDir('desc')
    }
  }

  const sortIcon = (field: SortField) => {
    if (sortField !== field) return null
    return sortDir === 'desc'
      ? <ChevronDown className="inline h-3 w-3" />
      : <ChevronUp className="inline h-3 w-3" />
  }

  const visiblePositionCols = POSITION_COLUMNS.filter((c) => isColumnVisible(c.key))
  const positionColCount = visiblePositionCols.length
  const riskColCount = 4

  const handleExportCsv = () => {
    const headers = visiblePositionCols.map((c) => c.label)
    if (hasRisk) headers.push('Delta', 'Gamma', 'Vega', 'VaR Contrib %')

    const rows = sortedPositions.map((pos) => {
      const risk = riskByInstrument.get(pos.instrumentId)
      const cellValues: Record<string, string> = {
        instrument: pos.instrumentId,
        assetClass: pos.assetClass,
        quantity: pos.quantity,
        avgCost: pos.averageCost.amount,
        marketPrice: pos.marketPrice.amount,
        marketValue: pos.marketValue.amount,
        unrealizedPnl: pos.unrealizedPnl.amount,
      }
      const row = visiblePositionCols.map((c) => cellValues[c.key])
      if (hasRisk) {
        row.push(
          risk?.delta ?? '',
          risk?.gamma ?? '',
          risk?.vega ?? '',
          risk ? `${risk.percentageOfTotal}%` : '',
        )
      }
      return row
    })

    exportToCsv('positions.csv', headers, rows)
  }

  return (
    <div>
      {connected !== undefined && (
        <div data-testid="connection-status" aria-live="polite" className="mb-3 text-sm flex items-center gap-1.5">
          {connected ? (
            <>
              <Wifi className="h-4 w-4 text-green-600" />
              <span className="text-green-600 font-medium">Live</span>
            </>
          ) : (
            <>
              <WifiOff className="h-4 w-4 text-red-600" />
              <span className="text-red-600 font-medium">Disconnected</span>
            </>
          )}
        </div>
      )}

      <div data-testid="portfolio-summary" className={`grid gap-3 mb-4 ${hasRisk ? 'grid-cols-5' : 'grid-cols-3'}`}>
        <Card>
          <div className="text-center -my-1">
            <div className="text-xs text-slate-500">Positions</div>
            <div className="text-lg font-bold text-slate-800">{positions.length}</div>
          </div>
        </Card>
        <Card>
          <div className="text-center -my-1">
            <div className="text-xs text-slate-500">Market Value</div>
            <div className="text-lg font-bold text-slate-800">
              {formatMoney(String(totalMarketValue), currency)}
            </div>
          </div>
        </Card>
        <Card>
          <div className="text-center -my-1">
            <div className="text-xs text-slate-500">Unrealized P&amp;L</div>
            <div className={`text-lg font-bold ${pnlColorClass(String(totalPnl))}`}>
              {formatMoney(String(totalPnl), currency)}
            </div>
          </div>
        </Card>
        {hasRisk && totalDelta != null && (
          <Card>
            <div data-testid="summary-portfolio-delta" className="text-center -my-1">
              <div className="text-xs text-slate-500">Portfolio Delta</div>
              <div className="text-lg font-bold text-slate-800">
                {formatCompactCurrency(totalDelta)}
              </div>
            </div>
          </Card>
        )}
        {hasRisk && totalVar != null && (
          <Card>
            <div data-testid="summary-portfolio-var" className="text-center -my-1">
              <div className="text-xs text-slate-500">Portfolio VaR</div>
              <div className="text-lg font-bold text-slate-800">
                {formatCompactCurrency(totalVar)}
              </div>
            </div>
          </Card>
        )}
      </div>

      <div className="flex justify-end gap-2 mb-2">
        <button
          data-testid="csv-export-button"
          onClick={handleExportCsv}
          className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-sm font-medium text-slate-600 border border-slate-300 rounded-md hover:bg-slate-50 transition-colors"
        >
          <Download className="h-4 w-4" />
          Export CSV
        </button>
        <div ref={settingsRef} className="relative">
          <button
            data-testid="column-settings-button"
            onClick={() => setSettingsOpen((v) => !v)}
            className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-sm font-medium text-slate-600 border border-slate-300 rounded-md hover:bg-slate-50 transition-colors"
          >
            <Settings className="h-4 w-4" />
            Columns
          </button>
          {settingsOpen && (
            <div data-testid="column-settings-dropdown" className="absolute right-0 mt-1 w-48 bg-white border border-slate-200 rounded-lg shadow-lg z-10 py-1">
              {POSITION_COLUMNS.map((col) => (
                <label
                  key={col.key}
                  className="flex items-center gap-2 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50 cursor-pointer"
                >
                  <input
                    type="checkbox"
                    data-testid={`column-toggle-${col.key}`}
                    checked={isColumnVisible(col.key)}
                    onChange={() => toggleColumn(col.key)}
                    className="rounded border-slate-300"
                  />
                  {col.label}
                </label>
              ))}
            </div>
          )}
        </div>
      </div>

      <Card>
        <div className="-mx-4 -my-4 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 dark:divide-surface-700">
            <thead>
              {hasRisk && (
                <tr>
                  <th
                    data-testid="header-group-position"
                    colSpan={positionColCount}
                    className="px-4 py-1.5 text-left text-xs font-semibold text-slate-500 bg-slate-50 border-b border-slate-200"
                  >
                    Position Details
                  </th>
                  <th
                    data-testid="header-group-risk"
                    colSpan={riskColCount}
                    className="px-4 py-1.5 text-left text-xs font-semibold text-indigo-600 bg-indigo-50 border-b border-slate-200"
                  >
                    Risk Metrics
                  </th>
                </tr>
              )}
              <tr className="bg-slate-50 dark:bg-surface-800">
                {visiblePositionCols.map((col) => (
                  <th
                    key={col.key}
                    className={`px-4 py-2 text-${col.align} text-sm font-semibold text-slate-700`}
                  >
                    {col.label}
                  </th>
                ))}
                {hasRisk && (
                  <>
                    <th
                      data-testid="sort-delta"
                      className="px-4 py-2 text-right text-sm font-semibold text-indigo-700 bg-indigo-50/50 cursor-pointer select-none"
                      onClick={() => handleSort('delta')}
                    >
                      Delta {sortIcon('delta')}
                    </th>
                    <th
                      data-testid="sort-gamma"
                      className="px-4 py-2 text-right text-sm font-semibold text-indigo-700 bg-indigo-50/50 cursor-pointer select-none"
                      onClick={() => handleSort('gamma')}
                    >
                      Gamma {sortIcon('gamma')}
                    </th>
                    <th
                      data-testid="sort-vega"
                      className="px-4 py-2 text-right text-sm font-semibold text-indigo-700 bg-indigo-50/50 cursor-pointer select-none"
                      onClick={() => handleSort('vega')}
                    >
                      Vega {sortIcon('vega')}
                    </th>
                    <th
                      data-testid="sort-var-pct"
                      className="px-4 py-2 text-right text-sm font-semibold text-indigo-700 bg-indigo-50/50 cursor-pointer select-none"
                      onClick={() => handleSort('var-pct')}
                    >
                      VaR Contrib % {sortIcon('var-pct')}
                    </th>
                  </>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-surface-700">
              {paginatedPositions.map((pos) => {
                const risk = riskByInstrument.get(pos.instrumentId)
                const cellMap: Record<string, React.ReactNode> = {
                  instrument: <td key="instrument" className="px-4 py-2 text-sm font-medium">{pos.instrumentId}</td>,
                  assetClass: <td key="assetClass" className="px-4 py-2 text-sm text-slate-600">{pos.assetClass}</td>,
                  quantity: <td key="quantity" className="px-4 py-2 text-sm text-right">{formatQuantity(pos.quantity)}</td>,
                  avgCost: <td key="avgCost" className="px-4 py-2 text-sm text-right">{formatMoney(pos.averageCost.amount, pos.averageCost.currency)}</td>,
                  marketPrice: <td key="marketPrice" className="px-4 py-2 text-sm text-right">{formatMoney(pos.marketPrice.amount, pos.marketPrice.currency)}</td>,
                  marketValue: <td key="marketValue" className="px-4 py-2 text-sm text-right">{formatMoney(pos.marketValue.amount, pos.marketValue.currency)}</td>,
                  unrealizedPnl: (
                    <td
                      key="unrealizedPnl"
                      data-testid={`pnl-${pos.instrumentId}`}
                      className={`px-4 py-2 text-sm text-right ${pnlColorClass(pos.unrealizedPnl.amount)}`}
                    >
                      {formatMoney(pos.unrealizedPnl.amount, pos.unrealizedPnl.currency)}
                    </td>
                  ),
                }
                return (
                  <tr key={pos.instrumentId} data-testid={`position-row-${pos.instrumentId}`} className="hover:bg-slate-50 dark:hover:bg-surface-700 transition-colors">
                    {visiblePositionCols.map((col) => cellMap[col.key])}
                    {hasRisk && (
                      <>
                        <td
                          data-testid={`delta-${pos.instrumentId}`}
                          className="px-4 py-2 text-sm text-right bg-indigo-50/30"
                        >
                          {risk?.delta != null ? formatNum(risk.delta) : '\u2014'}
                        </td>
                        <td
                          data-testid={`gamma-${pos.instrumentId}`}
                          className="px-4 py-2 text-sm text-right bg-indigo-50/30"
                        >
                          {risk?.gamma != null ? formatNum(risk.gamma) : '\u2014'}
                        </td>
                        <td
                          data-testid={`vega-${pos.instrumentId}`}
                          className="px-4 py-2 text-sm text-right bg-indigo-50/30"
                        >
                          {risk?.vega != null ? formatNum(risk.vega) : '\u2014'}
                        </td>
                        <td
                          data-testid={`var-pct-${pos.instrumentId}`}
                          className="px-4 py-2 text-sm text-right font-medium bg-indigo-50/30"
                        >
                          {risk ? `${formatNum(risk.percentageOfTotal)}%` : '\u2014'}
                        </td>
                      </>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>

      {showPagination && (
        <div data-testid="pagination-controls" className="flex items-center justify-center gap-3 mt-3">
          <button
            data-testid="pagination-prev"
            disabled={currentPage === 1}
            onClick={() => setCurrentPage((p) => p - 1)}
            className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <ChevronLeft className="h-4 w-4" />
            Previous
          </button>
          <span data-testid="pagination-info" className="text-sm text-slate-600">
            Page {currentPage} of {totalPages}
          </span>
          <button
            data-testid="pagination-next"
            disabled={currentPage === totalPages}
            onClick={() => setCurrentPage((p) => p + 1)}
            className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Next
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      )}
    </div>
  )
}
