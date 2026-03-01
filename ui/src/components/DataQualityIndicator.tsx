import { useState } from 'react'
import { Activity } from 'lucide-react'
import type { DataQualityStatus } from '../types'

interface DataQualityIndicatorProps {
  status: DataQualityStatus | null
  loading: boolean
}

export function DataQualityIndicator({ status, loading }: DataQualityIndicatorProps) {
  const [open, setOpen] = useState(false)

  if (loading) {
    return (
      <div data-testid="data-quality-loading" className="text-slate-400 text-sm">
        <Activity className="h-4 w-4 animate-pulse" />
      </div>
    )
  }

  if (!status) return null

  const colorClass =
    status.overall === 'CRITICAL'
      ? 'text-red-500'
      : status.overall === 'WARNING'
        ? 'text-amber-500'
        : 'text-green-500'

  const statusTestId =
    status.overall === 'CRITICAL'
      ? 'dq-status-critical'
      : status.overall === 'WARNING'
        ? 'dq-status-warning'
        : 'dq-status-ok'

  return (
    <div className="relative" data-testid="data-quality-indicator" onClick={() => setOpen((prev) => !prev)}>
      <button
        className={`p-1.5 rounded-md hover:bg-surface-800 transition-colors ${colorClass}`}
        aria-label="Data quality status"
      >
        <span data-testid={statusTestId} className={`inline-block h-2.5 w-2.5 rounded-full ${
          status.overall === 'CRITICAL'
            ? 'bg-red-500'
            : status.overall === 'WARNING'
              ? 'bg-amber-500'
              : 'bg-green-500'
        }`} />
      </button>

      {open && (
        <div
          className="absolute right-0 top-full mt-2 w-80 bg-surface-800 border border-surface-700 rounded-lg shadow-xl z-50 p-3"
          data-testid="data-quality-dropdown"
        >
          <div className="text-sm font-medium text-white mb-2">Data Quality</div>
          <div className="space-y-2">
            {status.checks.map((check) => (
              <div
                key={check.name}
                className="flex items-start gap-2 text-sm"
              >
                <span className={`mt-0.5 inline-block h-2 w-2 rounded-full flex-shrink-0 ${
                  check.status === 'CRITICAL'
                    ? 'bg-red-500'
                    : check.status === 'WARNING'
                      ? 'bg-amber-500'
                      : 'bg-green-500'
                }`} />
                <div>
                  <div className="text-white font-medium">{check.name}</div>
                  <div className="text-slate-400 text-xs">{check.message}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
