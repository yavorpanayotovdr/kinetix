import { AlertTriangle, CheckCircle, Clock } from 'lucide-react'
import { Button } from './ui/Button'
import type { SodBaselineStatusDto } from '../types'
import { formatTimestamp } from '../utils/format'

interface SodBaselineIndicatorProps {
  status: SodBaselineStatusDto | null
  loading: boolean
  creating: boolean
  resetting: boolean
  onCreateSnapshot: () => void
  onResetBaseline: () => void
}

export function SodBaselineIndicator({
  status,
  loading,
  creating,
  resetting,
  onCreateSnapshot,
  onResetBaseline,
}: SodBaselineIndicatorProps) {
  if (loading || !status) {
    return null
  }

  if (!status.exists) {
    return (
      <div
        data-testid="sod-baseline-warning"
        className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3"
      >
        <AlertTriangle className="h-5 w-5 text-amber-500 shrink-0" />
        <div className="flex-1">
          <p className="text-sm font-medium text-amber-800">
            No SOD baseline for today
          </p>
          <p className="text-xs text-amber-600">
            Set a baseline to enable P&L attribution computation.
          </p>
        </div>
        <Button
          variant="primary"
          size="sm"
          onClick={onCreateSnapshot}
          loading={creating}
          data-testid="sod-create-button"
        >
          Set as SOD Baseline
        </Button>
      </div>
    )
  }

  return (
    <div
      data-testid="sod-baseline-active"
      className="flex items-center gap-3 rounded-lg border border-green-200 bg-green-50 px-4 py-3"
    >
      <CheckCircle className="h-5 w-5 text-green-500 shrink-0" />
      <div className="flex-1">
        <p className="text-sm font-medium text-green-800">
          SOD Baseline Active
        </p>
        <p className="text-xs text-green-600 flex items-center gap-1">
          <Clock className="h-3 w-3" />
          {status.createdAt ? formatTimestamp(status.createdAt) : 'Unknown time'}
          {' — '}
          <span className="font-medium">
            {status.snapshotType === 'AUTO' ? 'Auto' : 'Manual'}
          </span>
        </p>
      </div>
      <Button
        variant="secondary"
        size="sm"
        onClick={onResetBaseline}
        loading={resetting}
        data-testid="sod-reset-button"
      >
        Reset SOD Baseline
      </Button>
    </div>
  )
}
