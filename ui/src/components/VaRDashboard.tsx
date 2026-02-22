import { RefreshCw } from 'lucide-react'
import type { VaRResultDto } from '../types'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import { VaRGauge } from './VaRGauge'
import { Card, Button, Spinner } from './ui'

interface VaRDashboardProps {
  varResult: VaRResultDto | null
  history: VaRHistoryEntry[]
  loading: boolean
  error: string | null
  onRefresh: () => void
}

const ASSET_CLASS_COLORS: Record<string, string> = {
  EQUITY: 'bg-blue-500',
  FIXED_INCOME: 'bg-green-500',
  COMMODITY: 'bg-amber-500',
  FX: 'bg-purple-500',
}

function TrendChart({ history }: { history: VaRHistoryEntry[] }) {
  if (history.length < 2) {
    return (
      <div data-testid="var-trend" className="flex items-center justify-center h-32 text-sm text-slate-400">
        Collecting data...
      </div>
    )
  }

  const values = history.map((e) => e.varValue)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1

  const width = 300
  const height = 100
  const padding = 10

  const points = history
    .map((entry, i) => {
      const x = padding + (i / (history.length - 1)) * (width - 2 * padding)
      const y = padding + (1 - (entry.varValue - min) / range) * (height - 2 * padding)
      return `${x},${y}`
    })
    .join(' ')

  return (
    <div data-testid="var-trend">
      <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-32">
        <polyline
          points={points}
          fill="none"
          stroke="#6366f1"
          strokeWidth="2"
          strokeLinejoin="round"
        />
      </svg>
    </div>
  )
}

export function VaRDashboard({ varResult, history, loading, error, onRefresh }: VaRDashboardProps) {
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
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <VaRGauge
          varValue={varValue}
          expectedShortfall={expectedShortfall}
          confidenceLevel={varResult.confidenceLevel}
        />

        <div data-testid="var-breakdown" className="flex flex-col justify-center">
          <h3 className="text-sm font-semibold text-slate-700 mb-2">Component Breakdown</h3>
          <div className="flex h-4 rounded overflow-hidden mb-2">
            {varResult.componentBreakdown.map((comp) => (
              <div
                key={comp.assetClass}
                data-testid={`breakdown-${comp.assetClass}`}
                className={`${ASSET_CLASS_COLORS[comp.assetClass] || 'bg-gray-400'}`}
                style={{ width: `${comp.percentageOfTotal}%` }}
              />
            ))}
          </div>
          <div className="flex flex-wrap gap-2 text-xs">
            {varResult.componentBreakdown.map((comp) => (
              <span key={comp.assetClass} className="flex items-center gap-1">
                <span
                  className={`inline-block w-2 h-2 rounded ${ASSET_CLASS_COLORS[comp.assetClass] || 'bg-gray-400'}`}
                />
                {comp.assetClass} ({comp.percentageOfTotal}%)
              </span>
            ))}
          </div>
        </div>

        <div className="flex flex-col justify-center">
          <h3 className="text-sm font-semibold text-slate-700 mb-2">VaR Trend</h3>
          <TrendChart history={history} />
        </div>
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
          icon={<RefreshCw className="h-3 w-3" />}
          onClick={onRefresh}
        >
          Recalculate
        </Button>
      </div>
    </Card>
  )
}
