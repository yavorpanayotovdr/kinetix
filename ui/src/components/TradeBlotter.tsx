import { useMemo, useState } from 'react'
import { Download, Inbox } from 'lucide-react'
import { useTradeHistory } from '../hooks/useTradeHistory'
import { formatMoney, formatQuantity, formatTimestamp } from '../utils/format'
import { Card, EmptyState } from './ui'
import type { TradeHistoryDto } from '../types'

interface TradeBlotterProps {
  portfolioId: string | null
}

function notional(trade: TradeHistoryDto): number {
  return Number(trade.quantity) * Number(trade.price.amount)
}

function exportToCsv(trades: TradeHistoryDto[]) {
  const header = 'Time,Instrument,Side,Qty,Price,Currency,Notional,Status'
  const rows = trades.map((t) => {
    const n = notional(t)
    return `${t.tradedAt},${t.instrumentId},${t.side},${t.quantity},${t.price.amount},${t.price.currency},${n.toFixed(2)},FILLED`
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

export function TradeBlotter({ portfolioId }: TradeBlotterProps) {
  const { trades, loading, error } = useTradeHistory(portfolioId)
  const [instrumentFilter, setInstrumentFilter] = useState('')
  const [sideFilter, setSideFilter] = useState<'' | 'BUY' | 'SELL'>('')

  const filtered = useMemo(() => {
    let result = [...trades]

    if (instrumentFilter) {
      const upper = instrumentFilter.toUpperCase()
      result = result.filter((t) => t.instrumentId.toUpperCase().includes(upper))
    }

    if (sideFilter) {
      result = result.filter((t) => t.side === sideFilter)
    }

    result.sort((a, b) => new Date(b.tradedAt).getTime() - new Date(a.tradedAt).getTime())

    return result
  }, [trades, instrumentFilter, sideFilter])

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
          onChange={(e) => setInstrumentFilter(e.target.value)}
          className="border border-slate-300 rounded-md px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
        />
        <select
          data-testid="filter-side"
          value={sideFilter}
          onChange={(e) => setSideFilter(e.target.value as '' | 'BUY' | 'SELL')}
          className="border border-slate-300 rounded-md px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
        >
          <option value="">All Sides</option>
          <option value="BUY">BUY</option>
          <option value="SELL">SELL</option>
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
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">Side</th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">Qty</th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">Price</th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">Notional</th>
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map((trade) => (
                <tr
                  key={trade.tradeId}
                  data-testid={`trade-row-${trade.tradeId}`}
                  className="hover:bg-slate-50 transition-colors"
                >
                  <td className="px-4 py-2 text-sm text-slate-600">
                    {formatTimestamp(trade.tradedAt)}
                  </td>
                  <td className="px-4 py-2 text-sm font-medium">{trade.instrumentId}</td>
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
                    className="px-4 py-2 text-sm text-right"
                  >
                    {formatMoney(String(notional(trade)), trade.price.currency)}
                  </td>
                  <td className="px-4 py-2 text-sm">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                      FILLED
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
