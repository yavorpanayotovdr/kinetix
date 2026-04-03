import { useMemo, useState } from 'react'
import { Download, Inbox } from 'lucide-react'
import { useTradeHistory } from '../hooks/useTradeHistory'
import { formatMoney, formatQuantity, formatTimestamp } from '../utils/format'
import { formatCompactCurrency } from '../utils/formatCompactCurrency'
import { Card, EmptyState } from './ui'
import type { TradeHistoryDto } from '../types'
import { InstrumentTypeBadge } from './InstrumentTypeBadge'
import { INSTRUMENT_TYPE_COLORS } from '../utils/instrumentTypes'

interface TradeBlotterProps {
  bookId: string | null
}

function notional(trade: TradeHistoryDto): number {
  return Number(trade.quantity) * Number(trade.price.amount)
}

function exportToCsv(trades: TradeHistoryDto[]) {
  const header = 'Time,Instrument,Name,Type,Side,Qty,Price,Currency,Notional,Status'
  const rows = trades.map((t) => {
    const n = notional(t)
    const name = (t.displayName || t.instrumentId).replace(/,/g, ' ')
    return `${t.tradedAt},${t.instrumentId},${name},${t.instrumentType || ''},${t.side},${t.quantity},${t.price.amount},${t.price.currency},${n.toFixed(2)},FILLED`
  })
  const csv = [header, ...rows].join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'trades.csv'
  a.click()
  URL.revokeObjectURL(url)
}

const INSTRUMENT_TYPE_OPTIONS = Object.keys(INSTRUMENT_TYPE_COLORS)

export function TradeBlotter({ bookId }: TradeBlotterProps) {
  const { trades, loading, error } = useTradeHistory(bookId)
  const [instrumentFilter, setInstrumentFilter] = useState('')
  const [sideFilter, setSideFilter] = useState<'' | 'BUY' | 'SELL'>('')
  const [instrumentTypeFilter, setInstrumentTypeFilter] = useState('')
  const [page, setPage] = useState(0)
  const PAGE_SIZE = 50

  const filtered = useMemo(() => {
    let result = [...trades]

    if (instrumentFilter) {
      const upper = instrumentFilter.toUpperCase()
      result = result.filter((t) => t.instrumentId.toUpperCase().includes(upper))
    }

    if (sideFilter) {
      result = result.filter((t) => t.side === sideFilter)
    }

    if (instrumentTypeFilter) {
      result = result.filter((t) => t.instrumentType === instrumentTypeFilter)
    }

    result.sort((a, b) => new Date(b.tradedAt).getTime() - new Date(a.tradedAt).getTime())

    return result
  }, [trades, instrumentFilter, sideFilter, instrumentTypeFilter])

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages - 1)
  const paginatedTrades = filtered.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE)

  const handleInstrumentFilter = (value: string) => {
    setInstrumentFilter(value)
    setPage(0)
  }

  const handleSideFilter = (value: '' | 'BUY' | 'SELL') => {
    setSideFilter(value)
    setPage(0)
  }

  const handleInstrumentTypeFilter = (value: string) => {
    setInstrumentTypeFilter(value)
    setPage(0)
  }

  if (loading) {
    return <p className="text-gray-500">Loading trades...</p>
  }

  if (error) {
    return <p className="text-red-600">{error}</p>
  }

  if (trades.length === 0) {
    return (
      <Card>
        <EmptyState
          icon={<Inbox className="h-10 w-10" />}
          title="No trades to display."
        />
      </Card>
    )
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-4">
        <input
          data-testid="filter-instrument"
          type="text"
          placeholder="Filter by instrument..."
          value={instrumentFilter}
          onChange={(e) => handleInstrumentFilter(e.target.value)}
          className="border border-slate-300 rounded-md px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
        />
        <select
          data-testid="filter-side"
          value={sideFilter}
          onChange={(e) => handleSideFilter(e.target.value as '' | 'BUY' | 'SELL')}
          className="border border-slate-300 rounded-md px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
        >
          <option value="">All Sides</option>
          <option value="BUY">BUY</option>
          <option value="SELL">SELL</option>
        </select>
        <select
          data-testid="filter-instrument-type"
          value={instrumentTypeFilter}
          onChange={(e) => handleInstrumentTypeFilter(e.target.value)}
          className="border border-slate-300 rounded-md px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
        >
          <option value="">All Types</option>
          {INSTRUMENT_TYPE_OPTIONS.map((type) => (
            <option key={type} value={type}>{type.replace(/_/g, ' ')}</option>
          ))}
        </select>
        <div className="flex-1" />
        <button
          data-testid="csv-export-button"
          onClick={() => exportToCsv(filtered)}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-slate-600 border border-slate-300 rounded-md hover:bg-slate-50 transition-colors"
        >
          <Download className="h-4 w-4" />
          Export CSV
        </button>
      </div>

      <Card>
        <div className="-mx-4 -my-4 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead>
              <tr className="bg-slate-50">
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">Time</th>
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">Instrument</th>
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">Name</th>
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">Type</th>
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">Side</th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">Qty</th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">Price</th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">Notional</th>
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {paginatedTrades.length === 0 && filtered.length === 0 && trades.length > 0 ? (
                <tr>
                  <td colSpan={9} className="px-4 py-8 text-center">
                    <EmptyState title="No trades match your filters." />
                  </td>
                </tr>
              ) : (
                paginatedTrades.map((trade) => (
                  <tr
                    key={trade.tradeId}
                    data-testid={`trade-row-${trade.tradeId}`}
                    className="hover:bg-slate-50 transition-colors"
                  >
                    <td className="px-4 py-2 text-sm text-slate-600">
                      {formatTimestamp(trade.tradedAt)}
                    </td>
                    <td className="px-4 py-2 text-sm font-medium">{trade.instrumentId}</td>
                    <td className="px-4 py-2 text-sm text-slate-600">{trade.displayName || trade.instrumentId}</td>
                    <td className="px-4 py-2 text-sm">{trade.instrumentType ? <InstrumentTypeBadge instrumentType={trade.instrumentType} /> : '—'}</td>
                    <td
                      data-testid={`trade-side-${trade.tradeId}`}
                      className={`px-4 py-2 text-sm font-medium ${
                        trade.side === 'BUY' ? 'text-green-600' : 'text-red-600'
                      }`}
                    >
                      {trade.side}
                    </td>
                    <td className="px-4 py-2 text-sm text-right">{formatQuantity(trade.quantity)}</td>
                    <td className="px-4 py-2 text-sm text-right">
                      {formatMoney(trade.price.amount, trade.price.currency)}
                    </td>
                    <td
                      data-testid={`trade-notional-${trade.tradeId}`}
                      className="px-4 py-2 text-sm text-right whitespace-nowrap"
                      title={formatMoney(String(notional(trade)), trade.price.currency)}
                    >
                      {formatCompactCurrency(notional(trade))}
                    </td>
                    <td className="px-4 py-2 text-sm">
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                        FILLED
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-3 text-sm text-slate-600">
          <span>
            Showing {safePage * PAGE_SIZE + 1}–{Math.min((safePage + 1) * PAGE_SIZE, filtered.length)} of {filtered.length}
          </span>
          <div className="flex items-center gap-2">
            <button
              disabled={safePage === 0}
              onClick={() => setPage((p) => p - 1)}
              className="px-3 py-1 rounded border border-slate-300 disabled:opacity-40 hover:bg-slate-50 transition-colors"
            >
              Previous
            </button>
            <span>Page {safePage + 1} of {totalPages}</span>
            <button
              disabled={safePage >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
              className="px-3 py-1 rounded border border-slate-300 disabled:opacity-40 hover:bg-slate-50 transition-colors"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
