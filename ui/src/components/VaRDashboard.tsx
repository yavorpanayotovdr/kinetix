import { useCallback, useEffect, useRef, useState } from 'react'
import { Info, RefreshCw, X } from 'lucide-react'
import type { VaRResultDto, GreeksResultDto, TimeRange } from '../types'
import type { VaRHistoryEntry } from '../hooks/useVaR'
import { useClickOutside } from '../hooks/useClickOutside'
import { VaRGauge } from './VaRGauge'
import { RiskSensitivities } from './RiskSensitivities'
import { ComponentBreakdown } from './ComponentBreakdown'
import { VaRTrendChart } from './VaRTrendChart'
import { TimeRangeSelector } from './TimeRangeSelector'
import { Card, Button, Spinner } from './ui'

const calculationTypeDescriptions: Record<string, string> = {
  PARAMETRIC: 'Variance-covariance method — assumes returns are normally distributed and estimates VaR from the portfolio\'s mean and standard deviation.',
  HISTORICAL: 'Historical simulation — uses actual past returns to estimate potential losses without assuming a specific distribution.',
  MONTE_CARLO: 'Monte Carlo simulation — generates thousands of random return scenarios to estimate the distribution of potential losses.',
}

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
  greeksResult?: GreeksResultDto | null
}

export function VaRDashboard({ varResult, filteredHistory, loading, refreshing = false, error, onRefresh, timeRange, setTimeRange, zoomIn, resetZoom, zoomDepth, greeksResult }: VaRDashboardProps) {
  const [tooltipOpen, setTooltipOpen] = useState(false)
  const calcTypeRef = useRef<HTMLSpanElement>(null)

  const closeTooltip = useCallback(() => setTooltipOpen(false), [])
  useClickOutside(calcTypeRef, closeTooltip)

  useEffect(() => {
    if (!tooltipOpen) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setTooltipOpen(false)
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [tooltipOpen])

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

  const description = calculationTypeDescriptions[varResult.calculationType]
  const toggleTooltip = () => setTooltipOpen(prev => !prev)

  const varValue = Number(varResult.varValue)
  const expectedShortfall = Number(varResult.expectedShortfall)

  return (
    <Card data-testid="var-dashboard" className="mb-4">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <VaRGauge
          varValue={varValue}
          expectedShortfall={expectedShortfall}
          confidenceLevel={varResult.confidenceLevel}
        />

        <div data-testid="var-sensitivities" className="md:col-span-2 flex flex-col items-center justify-center">
          {greeksResult ? (
            <RiskSensitivities greeksResult={greeksResult} pvValue={varResult.pvValue} />
          ) : (
            <div data-testid="sensitivities-placeholder" className="text-sm text-slate-400 text-center">No greeks data</div>
          )}
        </div>

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
        <span ref={calcTypeRef} className="relative">
          <span data-testid="calc-type-label" className="inline-flex items-center gap-1">
            {varResult.calculationType}
            <Info data-testid="calc-type-info" className="h-3 w-3 cursor-pointer text-slate-400 hover:text-slate-600 transition-colors" onClick={toggleTooltip} />
          </span>
          {tooltipOpen && description && (
            <span data-testid="calc-type-tooltip" className="absolute bottom-full left-0 mb-1 w-64 rounded bg-slate-800 px-3 py-2 text-xs text-white text-justify shadow-lg z-10">
              <button data-testid="calc-type-tooltip-close" className="float-right ml-2 text-slate-400 hover:text-white" onClick={closeTooltip}><X className="h-3 w-3" /></button>
              {description}
            </span>
          )}
          {' '}&middot;{' '}
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
          {refreshing ? 'Refreshing...' : 'Refresh'}
        </Button>
      </div>
    </Card>
  )
}
