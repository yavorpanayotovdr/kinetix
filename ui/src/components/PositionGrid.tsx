import type { PositionDto } from '../types'
import { formatMoney, formatQuantity, pnlColorClass } from '../utils/format'

interface PositionGridProps {
  positions: PositionDto[]
  connected?: boolean
}

export function PositionGrid({ positions, connected }: PositionGridProps) {
  if (positions.length === 0) {
    return <p className="text-gray-500 p-4">No positions to display.</p>
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
        <div data-testid="connection-status" className="mb-2 text-sm">
          {connected ? (
            <span className="text-green-600 font-medium">Live</span>
          ) : (
            <span className="text-red-600 font-medium">Disconnected</span>
          )}
        </div>
      )}

      <div data-testid="portfolio-summary" className="grid grid-cols-3 gap-3 mb-4">
        <div className="bg-white rounded-lg shadow p-3 text-center">
          <div className="text-xs text-gray-500">Positions</div>
          <div className="text-lg font-bold text-gray-800">{positions.length}</div>
        </div>
        <div className="bg-white rounded-lg shadow p-3 text-center">
          <div className="text-xs text-gray-500">Market Value</div>
          <div className="text-lg font-bold text-gray-800">
            {formatMoney(String(totalMarketValue), currency)}
          </div>
        </div>
        <div className="bg-white rounded-lg shadow p-3 text-center">
          <div className="text-xs text-gray-500">Unrealized P&amp;L</div>
          <div className={`text-lg font-bold ${pnlColorClass(String(totalPnl))}`}>
            {formatMoney(String(totalPnl), currency)}
          </div>
        </div>
      </div>

      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-2 text-left text-sm font-semibold text-gray-700">
              Instrument
            </th>
            <th className="px-4 py-2 text-left text-sm font-semibold text-gray-700">
              Asset Class
            </th>
            <th className="px-4 py-2 text-right text-sm font-semibold text-gray-700">
              Quantity
            </th>
            <th className="px-4 py-2 text-right text-sm font-semibold text-gray-700">
              Avg Cost
            </th>
            <th className="px-4 py-2 text-right text-sm font-semibold text-gray-700">
              Market Price
            </th>
            <th className="px-4 py-2 text-right text-sm font-semibold text-gray-700">
              Market Value
            </th>
            <th className="px-4 py-2 text-right text-sm font-semibold text-gray-700">
              Unrealized P&L
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {positions.map((pos) => (
            <tr key={pos.instrumentId} data-testid={`position-row-${pos.instrumentId}`}>
              <td className="px-4 py-2 text-sm">{pos.instrumentId}</td>
              <td className="px-4 py-2 text-sm">{pos.assetClass}</td>
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
  )
}
