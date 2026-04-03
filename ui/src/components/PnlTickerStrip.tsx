import { AlertTriangle } from 'lucide-react'
import type { IntradayPnlSnapshotDto } from '../types'
import { formatNum, formatTimeOnly, pnlColorClass } from '../utils/format'

interface PnlTickerStripProps {
  bookId: string | null
  latest: IntradayPnlSnapshotDto | null
  connected: boolean
}

export function PnlTickerStrip({ latest, connected }: PnlTickerStripProps) {
  if (!latest) return null

  return (
    <div
      data-testid="pnl-ticker-strip"
      className="flex items-center gap-6 px-4 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-sm"
    >
      <div className="flex items-center gap-1.5">
        <span
          data-testid="ticker-connection-status"
          className={`h-2 w-2 rounded-full ${connected ? 'bg-green-500' : 'bg-red-500'}`}
          aria-label={connected ? 'Connected' : 'Disconnected'}
        />
        <span className="text-slate-400 text-xs">{connected ? 'Live' : 'Disconnected'}</span>
      </div>

      <div className="flex items-baseline gap-1">
        <span className="text-xs text-slate-500 uppercase tracking-wide">Total</span>
        <span
          data-testid="ticker-total-pnl"
          className={`font-mono font-semibold tabular-nums ${pnlColorClass(latest.totalPnl)}`}
        >
          {formatNum(latest.totalPnl)}
        </span>
        <span className="text-xs text-slate-400">{latest.baseCurrency}</span>
      </div>

      <div className="flex items-baseline gap-1">
        <span className="text-xs text-slate-500">Realised</span>
        <span
          data-testid="ticker-realised-pnl"
          className={`font-mono tabular-nums ${pnlColorClass(latest.realisedPnl)}`}
        >
          {formatNum(latest.realisedPnl)}
        </span>
      </div>

      <div className="flex items-baseline gap-1">
        <span className="text-xs text-slate-500">Unrealised</span>
        <span
          data-testid="ticker-unrealised-pnl"
          className={`font-mono tabular-nums ${pnlColorClass(latest.unrealisedPnl)}`}
        >
          {formatNum(latest.unrealisedPnl)}
        </span>
      </div>

      <div className="flex items-baseline gap-1">
        <span className="text-xs text-slate-500">HWM</span>
        <span
          data-testid="ticker-hwm"
          className="font-mono tabular-nums text-slate-700 dark:text-slate-300"
        >
          {formatNum(latest.highWaterMark)}
        </span>
      </div>

      <div className="flex items-center gap-1 text-xs text-slate-400">
        <span data-testid="ticker-trigger">{latest.trigger}</span>
      </div>

      {latest.missingFxRates && latest.missingFxRates.length > 0 && (
        <div
          data-testid="ticker-missing-fx-rates"
          className="flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400"
          title={`Missing FX rates: ${latest.missingFxRates.join(', ')}`}
        >
          <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
          <span>FX: {latest.missingFxRates.join(', ')}</span>
        </div>
      )}

      <div className="ml-auto text-xs text-slate-400">
        <span data-testid="ticker-snapshot-time">{formatTimeOnly(latest.snapshotAt)}</span>
      </div>
    </div>
  )
}
