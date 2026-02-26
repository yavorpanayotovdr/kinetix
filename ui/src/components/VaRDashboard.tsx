import { RefreshCw } from 'lucide-react'
import type { VaRResultDto, TimeRange } from '../types'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import { VaRGauge } from './VaRGauge'
import { ComponentBreakdown } from './ComponentBreakdown'
import { VaRTrendChart } from './VaRTrendChart'
import { TimeRangeSelector } from './TimeRangeSelector'
import { Card, Button, Spinner } from './ui'

interface VaRDashboardProps {
  varResult: VaRResultDto | null
  filteredHistory: VaRHistoryEntry[]
  loading: boolean
  refreshing?: boolean
  error: string | null
  onRefresh: () => void
  timeRange: TimeRange
  setTimeRange: (range: TimeRange) => void
  zoomIn: (range: TimeRange) => void
  resetZoom: () => void
  zoomDepth: number
}

export function VaRDashboard({ varResult, filteredHistory, loading, refreshing = false, error, onRefresh, timeRange, setTimeRange, zoomIn, resetZoom, zoomDepth }: VaRDashboardProps) {
  if (loading) {
    return (
      <Card data-testid="var-loading" className="mb-4">
        <div className="flex items-center gap-2 text-slate-500">
          <Spinner size="sm" />
          Loading VaR data...
        </div>
      </Card>
    )
  }

  if (error) {
    return (
      <Card data-testid="var-error" className="mb-4">
        <p className="text-red-600">{error}</p>
      </Card>
    )
  }

  if (!varResult) {
    return (
      <Card data-testid="var-empty" className="mb-4">
        <p className="text-slate-500">No VaR data available.</p>
      </Card>
    )
  }

  const varValue = Number(varResult.varValue)
  const expectedShortfall = Number(varResult.expectedShortfall)

  return (
    <Card data-testid="var-dashboard" className="mb-4">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <VaRGauge
          varValue={varValue}
          expectedShortfall={expectedShortfall}
          confidenceLevel={varResult.confidenceLevel}
        />

        <div data-testid="var-breakdown" className="flex flex-col justify-center">
          <ComponentBreakdown breakdown={varResult.componentBreakdown} />
        </div>
      </div>

      <div className="mt-4">
        <TimeRangeSelector value={timeRange} onChange={setTimeRange} />
        <VaRTrendChart
          history={filteredHistory}
          timeRange={timeRange}
          onZoom={zoomIn}
          zoomDepth={zoomDepth}
          onResetZoom={resetZoom}
        />
      </div>

      <div className="flex items-center justify-between mt-4 pt-3 border-t border-slate-100 text-xs text-slate-500">
        <span>
          {varResult.calculationType} &middot;{' '}
          {new Date(varResult.calculatedAt).toLocaleString()}
        </span>
        <Button
          data-testid="var-recalculate"
          variant="primary"
          size="sm"
          icon={<RefreshCw className={`h-3 w-3${refreshing ? ' animate-spin' : ''}`} />}
          onClick={onRefresh}
          disabled={refreshing}
        >
          {refreshing ? 'Recalculating...' : 'Recalculate'}
        </Button>
      </div>
    </Card>
  )
}
