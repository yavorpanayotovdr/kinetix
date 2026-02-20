import type { PositionDto } from '../types'

const KNOWN_CURRENCIES: Record<string, string> = {
  USD: 'en-US',
  EUR: 'en-IE',
  GBP: 'en-GB',
  JPY: 'ja-JP',
}

export function formatMoney(amount: string, currency: string): string {
  const locale = KNOWN_CURRENCIES[currency]
  if (!locale) {
    return `${amount} ${currency}`
  }
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
  }).format(Number(amount))
}

export function pnlColorClass(amount: string): string {
  const value = Number(amount)
  if (value > 0) return 'text-green-600'
  if (value < 0) return 'text-red-600'
  return 'text-gray-500'
}

interface PositionGridProps {
  positions: PositionDto[]
  connected?: boolean
}

export function PositionGrid({ positions, connected }: PositionGridProps) {
  if (positions.length === 0) {
    return <p className="text-gray-500 p-4">No positions to display.</p>
  }

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
              <td className="px-4 py-2 text-sm text-right">{pos.quantity}</td>
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
