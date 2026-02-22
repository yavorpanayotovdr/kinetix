import { Wifi, WifiOff, Inbox } from 'lucide-react'
import type { PositionDto } from '../types'
import { formatMoney, formatQuantity, pnlColorClass } from '../utils/format'
import { Card, EmptyState } from './ui'

interface PositionGridProps {
  positions: PositionDto[]
  connected?: boolean
}

export function PositionGrid({ positions, connected }: PositionGridProps) {
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

  return (
    <div>
      {connected !== undefined && (
        <div data-testid="connection-status" className="mb-3 text-sm flex items-center gap-1.5">
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

      <div data-testid="portfolio-summary" className="grid grid-cols-3 gap-3 mb-4">
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
      </div>

      <Card>
        <div className="-mx-4 -my-4 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">
                  Instrument
                </th>
                <th className="px-4 py-2 text-left text-sm font-semibold text-slate-700">
                  Asset Class
                </th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">
                  Quantity
                </th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">
                  Avg Cost
                </th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">
                  Market Price
                </th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">
                  Market Value
                </th>
                <th className="px-4 py-2 text-right text-sm font-semibold text-slate-700">
                  Unrealized P&L
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {positions.map((pos) => (
                <tr key={pos.instrumentId} data-testid={`position-row-${pos.instrumentId}`} className="hover:bg-slate-50 transition-colors">
                  <td className="px-4 py-2 text-sm font-medium">{pos.instrumentId}</td>
                  <td className="px-4 py-2 text-sm text-slate-600">{pos.assetClass}</td>
                  <td className="px-4 py-2 text-sm text-right">{formatQuantity(pos.quantity)}</td>
                  <td className="px-4 py-2 text-sm text-right">
                    {formatMoney(pos.averageCost.amount, pos.averageCost.currency)}
                  </td>
                  <td className="px-4 py-2 text-sm text-right">
                    {formatMoney(pos.marketPrice.amount, pos.marketPrice.currency)}
                  </td>
                  <td className="px-4 py-2 text-sm text-right">
                    {formatMoney(pos.marketValue.amount, pos.marketValue.currency)}
                  </td>
                  <td
                    data-testid={`pnl-${pos.instrumentId}`}
                    className={`px-4 py-2 text-sm text-right ${pnlColorClass(pos.unrealizedPnl.amount)}`}
                  >
                    {formatMoney(
                      pos.unrealizedPnl.amount,
                      pos.unrealizedPnl.currency,
                    )}
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
