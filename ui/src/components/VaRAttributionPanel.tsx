import { AlertTriangle } from 'lucide-react'
import { Card, Button, Spinner } from './ui'
import { formatNum } from '../utils/format'
import { changeColorClass } from '../utils/changeIndicators'
import { MagnitudeIndicator } from './MagnitudeIndicator'
import type { VaRAttributionDto } from '../types'

interface VaRAttributionPanelProps {
  attribution: VaRAttributionDto | null
  loading: boolean
  onRequest: () => void
}

export function VaRAttributionPanel({ attribution, loading, onRequest }: VaRAttributionPanelProps) {
  if (!attribution && !loading) {
    return (
      <Card data-testid="var-attribution-panel">
        <div className="text-center py-4">
          <p className="text-sm text-slate-500 dark:text-slate-400 mb-3">
            VaR attribution decomposes the change into position, volatility, correlation, and time
            decay effects.
          </p>
          <Button
            data-testid="request-attribution"
            variant="secondary"
            onClick={onRequest}
          >
            Request Attribution
          </Button>
        </div>
      </Card>
    )
  }

  if (loading) {
    return (
      <Card data-testid="var-attribution-panel">
        <div className="flex items-center justify-center py-6" aria-live="polite" aria-busy="true">
          <Spinner size="sm" />
          <span className="ml-2 text-sm text-slate-500 dark:text-slate-400">
            Computing attribution...
          </span>
        </div>
      </Card>
    )
  }

  if (!attribution) return null

  const magnitudes = attribution.effectMagnitudes ?? {}
  const effects: { label: string; value: string | null; bold: boolean; magnitudeKey?: string }[] = [
    { label: 'Total Change', value: attribution.totalChange, bold: true },
    { label: 'Position Effect', value: attribution.positionEffect, bold: false, magnitudeKey: 'position' },
    { label: 'Volatility Effect', value: attribution.volEffect, bold: false, magnitudeKey: 'vol' },
    { label: 'Correlation Effect', value: attribution.corrEffect, bold: false, magnitudeKey: 'corr' },
    { label: 'Time Decay', value: attribution.timeDecayEffect, bold: false, magnitudeKey: 'timeDecay' },
    { label: 'Unexplained', value: attribution.unexplained, bold: false, magnitudeKey: 'unexplained' },
  ]

  const computedEffects = effects.filter((e) => e.value !== null)
  const maxAbs = Math.max(...computedEffects.map((e) => Math.abs(Number(e.value))), 1)

  return (
    <Card data-testid="var-attribution-panel">
      <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">
        VaR Change Attribution
      </h3>
      <div className="space-y-2" role="list" aria-label="VaR attribution effects">
        {effects.map((e) => {
          if (e.value === null) {
            return (
              <div key={e.label} className="flex items-center gap-2" role="listitem">
                <span className="text-xs w-28 shrink-0 text-slate-600 dark:text-slate-300">
                  {e.label}
                </span>
                <div className="flex-1 h-5 relative" aria-hidden="true" />
                <span
                  data-testid={`attr-${e.label.toLowerCase().replace(/\s+/g, '-')}`}
                  className="text-xs w-20 text-right shrink-0 text-slate-400 dark:text-slate-500 italic"
                  title="Not yet computed — absorbed into unexplained"
                >
                  N/A
                </span>
              </div>
            )
          }

          const numVal = Number(e.value)
          const barWidth = (Math.abs(numVal) / maxAbs) * 100
          const isPositive = numVal >= 0

          return (
            <div key={e.label} className="flex items-center gap-2" role="listitem">
              <span
                className={`text-xs w-28 shrink-0 ${
                  e.bold
                    ? 'font-semibold text-slate-800 dark:text-slate-200'
                    : 'text-slate-600 dark:text-slate-300'
                }`}
              >
                {e.label}
              </span>
              <div className="flex-1 h-5 relative" aria-hidden="true">
                <div
                  className={`absolute top-0 h-full rounded ${
                    isPositive
                      ? 'bg-red-300 dark:bg-red-700'
                      : 'bg-green-300 dark:bg-green-700'
                  }`}
                  style={{ width: `${barWidth}%` }}
                />
              </div>
              <span
                data-testid={`attr-${e.label.toLowerCase().replace(/\s+/g, '-')}`}
                className={`text-xs w-20 text-right font-medium shrink-0 ${changeColorClass(numVal)}`}
              >
                {formatNum(e.value)}
              </span>
              {e.magnitudeKey && magnitudes[e.magnitudeKey] && (
                <span className="shrink-0">
                  <MagnitudeIndicator magnitude={magnitudes[e.magnitudeKey] as 'LARGE' | 'MEDIUM' | 'SMALL'} />
                </span>
              )}
            </div>
          )
        })}
      </div>
      {attribution.caveats && attribution.caveats.length > 0 && (
        <div data-testid="attribution-caveats" className="mt-3 space-y-1">
          {attribution.caveats.map((caveat, i) => (
            <div
              key={i}
              className="flex items-start gap-1.5 text-xs text-amber-600 dark:text-amber-400"
            >
              <AlertTriangle className="h-3 w-3 mt-0.5 shrink-0" aria-hidden="true" />
              <span className="italic">{caveat}</span>
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}
