import { useEffect, useState } from 'react'
import { Users, AlertTriangle, RefreshCw, Activity } from 'lucide-react'
import { useCounterpartyRisk } from '../hooks/useCounterpartyRisk'
import type { CounterpartyExposureDto, ExposureAtTenorDto } from '../api/counterpartyRisk'
import { fetchSaCcr } from '../api/saCcr'
import type { SaCcrResultDto } from '../types'
import { formatCurrency } from '../utils/format'
import { Spinner } from './ui'
import { SaCcrPanel } from './SaCcrPanel'

// ---------------------------------------------------------------------------
// PFE profile chart (SVG, no external chart library)
// ---------------------------------------------------------------------------

interface PfeChartProps {
  profile: ExposureAtTenorDto[]
}

function PfeChart({ profile }: PfeChartProps) {
  if (profile.length === 0) {
    return (
      <div
        data-testid="pfe-chart-empty"
        className="flex items-center justify-center h-40 text-sm text-slate-400"
      >
        No PFE profile available. Run PFE computation to generate.
      </div>
    )
  }

  const PADDING = { top: 24, right: 16, bottom: 32, left: 64 }
  const CHART_HEIGHT = 180
  const PLOT_WIDTH = 480

  const maxValue = Math.max(...profile.flatMap((p) => [p.pfe95, p.expectedExposure])) * 1.1 || 1

  const toY = (value: number) =>
    PADDING.top + (1 - value / maxValue) * (CHART_HEIGHT - PADDING.top - PADDING.bottom)

  const xStep = (PLOT_WIDTH - PADDING.left - PADDING.right) / Math.max(profile.length - 1, 1)

  const toX = (i: number) => PADDING.left + i * xStep

  const pfe95Points = profile.map((p, i) => `${toX(i)},${toY(p.pfe95)}`).join(' ')
  const eePoints = profile.map((p, i) => `${toX(i)},${toY(p.expectedExposure)}`).join(' ')

  const gridLines = [0, 0.25, 0.5, 0.75, 1].map((frac) => frac * maxValue)

  return (
    <svg
      data-testid="pfe-chart"
      width="100%"
      viewBox={`0 0 ${PLOT_WIDTH} ${CHART_HEIGHT}`}
      className="overflow-visible"
    >
      {/* Grid lines */}
      {gridLines.map((v, i) => {
        const y = toY(v)
        return (
          <g key={i}>
            <line
              x1={PADDING.left}
              y1={y}
              x2={PLOT_WIDTH - PADDING.right}
              y2={y}
              stroke="#334155"
              strokeDasharray="4 2"
            />
            <text x={PADDING.left - 6} y={y + 4} textAnchor="end" fill="#94a3b8" fontSize={9}>
              {(v / 1_000_000).toFixed(1)}M
            </text>
          </g>
        )
      })}

      {/* X-axis labels */}
      {profile.map((p, i) => (
        <text
          key={i}
          x={toX(i)}
          y={CHART_HEIGHT - 4}
          textAnchor="middle"
          fill="#94a3b8"
          fontSize={9}
        >
          {p.tenor}
        </text>
      ))}

      {/* EE area fill */}
      <polyline
        points={eePoints}
        fill="none"
        stroke="#6366f1"
        strokeWidth={2}
        strokeLinejoin="round"
        strokeDasharray="5 3"
      />

      {/* PFE 95 line */}
      <polyline
        points={pfe95Points}
        fill="none"
        stroke="#f59e0b"
        strokeWidth={2}
        strokeLinejoin="round"
      />

      {/* Dots on PFE 95 */}
      {profile.map((p, i) => (
        <circle key={i} cx={toX(i)} cy={toY(p.pfe95)} r={3} fill="#f59e0b" />
      ))}

      {/* Legend */}
      <g>
        <line x1={PADDING.left} y1={10} x2={PADDING.left + 14} y2={10} stroke="#f59e0b" strokeWidth={2} />
        <text x={PADDING.left + 18} y={14} fill="#94a3b8" fontSize={9}>PFE 95</text>
        <line x1={PADDING.left + 60} y1={10} x2={PADDING.left + 74} y2={10} stroke="#6366f1" strokeWidth={2} strokeDasharray="5 3" />
        <text x={PADDING.left + 78} y={14} fill="#94a3b8" fontSize={9}>EE</text>
      </g>
    </svg>
  )
}

// ---------------------------------------------------------------------------
// Counterparty list row
// ---------------------------------------------------------------------------

interface CounterpartyRowProps {
  exposure: CounterpartyExposureDto
  isSelected: boolean
  onSelect: () => void
}

function CounterpartyRow({ exposure, isSelected, onSelect }: CounterpartyRowProps) {
  const hasHighExposure = exposure.currentNetExposure > 5_000_000
  const hasCva = exposure.cva !== null

  return (
    <tr
      data-testid={`counterparty-row-${exposure.counterpartyId}`}
      onClick={onSelect}
      className={`cursor-pointer border-b border-slate-700 transition-colors ${
        isSelected
          ? 'bg-indigo-900/30'
          : 'hover:bg-slate-700/40'
      }`}
    >
      <td className="px-4 py-2.5 text-sm font-mono text-slate-200">
        <div className="flex items-center gap-2">
          {hasHighExposure && (
            <AlertTriangle
              data-testid={`wwf-flag-${exposure.counterpartyId}`}
              className="h-3.5 w-3.5 text-amber-400 flex-shrink-0"
              aria-label="High exposure"
            />
          )}
          {exposure.counterpartyId}
        </div>
      </td>
      <td className="px-4 py-2.5 text-sm text-right font-mono text-slate-200">
        {formatCurrency(exposure.currentNetExposure)}
      </td>
      <td className="px-4 py-2.5 text-sm text-right font-mono text-amber-300">
        {formatCurrency(exposure.peakPfe)}
      </td>
      <td className="px-4 py-2.5 text-sm text-right font-mono">
        {hasCva ? (
          <span className={exposure.cvaEstimated ? 'text-slate-400 italic' : 'text-indigo-300'}>
            {formatCurrency(exposure.cva!)}
            {exposure.cvaEstimated && ' *'}
          </span>
        ) : (
          <span className="text-slate-500">—</span>
        )}
      </td>
      <td className="px-4 py-2.5 text-sm text-center">
        {hasHighExposure ? (
          <span
            data-testid={`wwf-badge-${exposure.counterpartyId}`}
            className="inline-flex items-center gap-1 px-1.5 py-0.5 bg-amber-900/40 text-amber-400 text-xs rounded"
          >
            <AlertTriangle className="h-3 w-3" />
            High
          </span>
        ) : (
          <span className="text-slate-500 text-xs">Normal</span>
        )}
      </td>
    </tr>
  )
}

// ---------------------------------------------------------------------------
// Detail panel for a selected counterparty
// ---------------------------------------------------------------------------

interface DetailPanelProps {
  exposure: CounterpartyExposureDto
  computing: boolean
  onComputePFE: () => void
  onComputeCVA: () => void
}

function DetailPanel({ exposure, computing, onComputePFE, onComputeCVA }: DetailPanelProps) {
  return (
    <div data-testid="counterparty-detail-panel" className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-slate-200">
          {exposure.counterpartyId}
        </h3>
        <div className="flex items-center gap-2">
          <button
            data-testid="compute-pfe-button"
            onClick={onComputePFE}
            disabled={computing}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-md transition-colors"
          >
            {computing ? <Spinner size="sm" /> : <Activity className="h-3.5 w-3.5" />}
            Compute PFE
          </button>
          <button
            data-testid="compute-cva-button"
            onClick={onComputeCVA}
            disabled={computing || exposure.pfeProfile.length === 0}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-amber-600 hover:bg-amber-500 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-md transition-colors"
          >
            {computing ? <Spinner size="sm" /> : <Activity className="h-3.5 w-3.5" />}
            Compute CVA
          </button>
        </div>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-3 gap-3">
        <div className="rounded-lg bg-slate-800 p-3">
          <div className="text-xs text-slate-400 mb-1">Net Exposure</div>
          <div
            data-testid="detail-net-exposure"
            className="text-lg font-mono font-semibold text-slate-100"
          >
            {formatCurrency(exposure.currentNetExposure)}
          </div>
          <div className="text-xs text-slate-500 mt-0.5">{exposure.currency}</div>
        </div>
        <div className="rounded-lg bg-slate-800 p-3">
          <div className="text-xs text-slate-400 mb-1">Peak PFE</div>
          <div
            data-testid="detail-peak-pfe"
            className="text-lg font-mono font-semibold text-amber-300"
          >
            {formatCurrency(exposure.peakPfe)}
          </div>
          <div className="text-xs text-slate-500 mt-0.5">95th percentile</div>
        </div>
        <div className="rounded-lg bg-slate-800 p-3">
          <div className="text-xs text-slate-400 mb-1">CVA</div>
          <div
            data-testid="detail-cva"
            className="text-lg font-mono font-semibold text-indigo-300"
          >
            {exposure.cva !== null ? (
              <>
                {formatCurrency(exposure.cva)}
                {exposure.cvaEstimated && (
                  <span className="text-xs text-slate-400 ml-1">(est.)</span>
                )}
              </>
            ) : (
              <span className="text-slate-500 text-base">Not computed</span>
            )}
          </div>
          <div className="text-xs text-slate-500 mt-0.5">Credit Valuation Adj.</div>
        </div>
      </div>

      {/* PFE Chart */}
      <div className="rounded-lg bg-slate-800 p-4">
        <h4 className="text-sm font-medium text-slate-300 mb-3">PFE Profile</h4>
        <PfeChart profile={exposure.pfeProfile} />
      </div>

      {/* Calculation timestamp */}
      <div className="text-xs text-slate-500">
        Last calculated:{' '}
        {new Date(exposure.calculatedAt).toLocaleString(undefined, {
          dateStyle: 'medium',
          timeStyle: 'short',
        })}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Main dashboard
// ---------------------------------------------------------------------------

export function CounterpartyRiskDashboard() {
  const {
    exposures,
    selected,
    loading,
    computing,
    error,
    selectCounterparty,
    computePFE,
    computeCVA,
    refresh,
  } = useCounterpartyRisk()

  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [saCcrResult, setSaCcrResult] = useState<SaCcrResultDto | null>(null)
  const [saCcrLoading, setSaCcrLoading] = useState(false)
  const [saCcrError, setSaCcrError] = useState<string | null>(null)
  const [prevSaCcrId, setPrevSaCcrId] = useState<string | null>(null)

  if (selectedId !== prevSaCcrId) {
    setPrevSaCcrId(selectedId)
    if (!selectedId) {
      setSaCcrResult(null)
      setSaCcrLoading(false)
      setSaCcrError(null)
    } else {
      setSaCcrLoading(true)
      setSaCcrError(null)
    }
  }

  useEffect(() => {
    if (!selectedId) return
    let cancelled = false
    fetchSaCcr(selectedId)
      .then((data) => { if (!cancelled) setSaCcrResult(data) })
      .catch((err: unknown) => { if (!cancelled) setSaCcrError(err instanceof Error ? err.message : String(err)) })
      .finally(() => { if (!cancelled) setSaCcrLoading(false) })
    return () => { cancelled = true }
  }, [selectedId])

  const handleSelect = (id: string) => {
    setSelectedId(id)
    void selectCounterparty(id)
  }

  return (
    <div data-testid="counterparty-risk-dashboard" className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Users className="h-5 w-5 text-indigo-400" />
          <h2 className="text-base font-semibold text-slate-200">Counterparty Risk</h2>
          <span className="text-xs text-slate-500">
            {exposures.length} counterpart{exposures.length === 1 ? 'y' : 'ies'}
          </span>
        </div>
        <button
          data-testid="refresh-exposures-button"
          onClick={refresh}
          disabled={loading}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-slate-300 border border-slate-600 rounded-md hover:bg-slate-700 disabled:opacity-50 transition-colors"
          aria-label="Refresh counterparty exposures"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {/* Error */}
      {error && (
        <div
          data-testid="counterparty-error"
          className="rounded-lg bg-red-950 border border-red-800 px-4 py-3 text-sm text-red-300"
          role="alert"
        >
          {error}
        </div>
      )}

      {/* Loading */}
      {loading && exposures.length === 0 && (
        <div className="flex items-center justify-center py-12 text-slate-400 gap-2">
          <Spinner />
          Loading counterparty exposures...
        </div>
      )}

      {/* Empty state */}
      {!loading && exposures.length === 0 && !error && (
        <div
          data-testid="counterparty-empty-state"
          className="flex flex-col items-center justify-center py-16 text-slate-400 gap-2"
        >
          <Users className="h-10 w-10 text-slate-600" />
          <p className="text-sm">No counterparty exposures found.</p>
          <p className="text-xs text-slate-500">Book trades and run PFE computation to populate.</p>
        </div>
      )}

      {/* Content */}
      {exposures.length > 0 && (
        <>
        <div className="grid grid-cols-1 xl:grid-cols-5 gap-4">
          {/* Counterparty list */}
          <div className="xl:col-span-2">
            <div className="rounded-lg bg-slate-800/60 border border-slate-700 overflow-hidden">
              <table className="w-full" aria-label="Counterparty exposures">
                <thead>
                  <tr className="border-b border-slate-700">
                    <th className="px-4 py-2.5 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">
                      Counterparty
                    </th>
                    <th className="px-4 py-2.5 text-right text-xs font-medium text-slate-400 uppercase tracking-wider">
                      Net Exposure
                    </th>
                    <th className="px-4 py-2.5 text-right text-xs font-medium text-slate-400 uppercase tracking-wider">
                      Peak PFE
                    </th>
                    <th className="px-4 py-2.5 text-right text-xs font-medium text-slate-400 uppercase tracking-wider">
                      CVA
                    </th>
                    <th className="px-4 py-2.5 text-center text-xs font-medium text-slate-400 uppercase tracking-wider">
                      WWR
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {exposures.map((e) => (
                    <CounterpartyRow
                      key={e.counterpartyId}
                      exposure={e}
                      isSelected={selectedId === e.counterpartyId}
                      onSelect={() => handleSelect(e.counterpartyId)}
                    />
                  ))}
                </tbody>
              </table>
              {selected?.cvaEstimated && (
                <div className="px-4 py-2 text-xs text-slate-500 border-t border-slate-700">
                  * CVA marked as estimated (no CDS spread available)
                </div>
              )}
            </div>
          </div>

          {/* Detail panel */}
          <div className="xl:col-span-3">
            {selected ? (
              <div className="rounded-lg bg-slate-800/60 border border-slate-700 p-4">
                <DetailPanel
                  exposure={selected}
                  computing={computing}
                  onComputePFE={() => void computePFE(selected.counterpartyId)}
                  onComputeCVA={() => void computeCVA(selected.counterpartyId)}
                />
              </div>
            ) : (
              <div
                data-testid="detail-panel-placeholder"
                className="rounded-lg bg-slate-800/30 border border-slate-700/50 h-full flex items-center justify-center min-h-48 text-slate-500 text-sm"
              >
                Select a counterparty to view details
              </div>
            )}
          </div>
        </div>

        {selectedId && (
          <div className="mt-4">
            <SaCcrPanel result={saCcrResult} loading={saCcrLoading} error={saCcrError} />
          </div>
        )}
        </>
      )}
    </div>
  )
}
