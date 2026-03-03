import { useEffect, useRef } from 'react'
import { X, Plus, Trash2, ArrowDown, ArrowUp } from 'lucide-react'
import type { WhatIfResponseDto, WhatIfImpactDto, PositionRiskDto } from '../types'
import type { TradeFormEntry, ValidationErrors } from '../hooks/useWhatIf'
import { formatNum } from '../utils/format'
import { Button, Card, Input } from './ui'

interface WhatIfPanelProps {
  open: boolean
  onClose: () => void
  trades: TradeFormEntry[]
  onAddTrade: () => void
  onRemoveTrade: (index: number) => void
  onUpdateTrade: (index: number, field: keyof TradeFormEntry, value: string) => void
  onSubmit: () => void
  onReset: () => void
  result: WhatIfResponseDto | null
  impact: WhatIfImpactDto | null
  loading: boolean
  error: string | null
  validationErrors?: ValidationErrors
}

function changeColorClass(value: number): string {
  // For VaR/ES: negative change (reduction) is good = green, positive (increase) is bad = red
  if (value < 0) return 'text-green-600 dark:text-green-400'
  if (value > 0) return 'text-red-600 dark:text-red-400'
  return 'text-slate-500 dark:text-slate-400'
}

function ChangeIcon({ value }: { value: number }) {
  if (value < 0) return <ArrowDown className="inline h-3.5 w-3.5" />
  if (value > 0) return <ArrowUp className="inline h-3.5 w-3.5" />
  return null
}

function greekTotal(
  result: WhatIfResponseDto,
  snapshot: 'base' | 'hypothetical',
  field: 'delta' | 'gamma' | 'vega',
): number {
  const greeks = snapshot === 'base' ? result.baseGreeks : result.hypotheticalGreeks
  if (!greeks) return 0
  return greeks.assetClassGreeks.reduce((sum, g) => sum + Number(g[field]), 0)
}

function greekScalar(
  result: WhatIfResponseDto,
  snapshot: 'base' | 'hypothetical',
  field: 'theta' | 'rho',
): number {
  const greeks = snapshot === 'base' ? result.baseGreeks : result.hypotheticalGreeks
  if (!greeks) return 0
  return Number(greeks[field])
}

function mergePositionRisk(
  base: PositionRiskDto[],
  hypothetical: PositionRiskDto[],
): { instrumentId: string; baseMV: string; hypoMV: string; baseDelta: string | null; hypoDelta: string | null; baseVarContrib: string; hypoVarContrib: string }[] {
  const instrumentIds = new Set([...base.map(p => p.instrumentId), ...hypothetical.map(p => p.instrumentId)])
  return Array.from(instrumentIds).map(id => {
    const b = base.find(p => p.instrumentId === id)
    const h = hypothetical.find(p => p.instrumentId === id)
    return {
      instrumentId: id,
      baseMV: b?.marketValue ?? '0',
      hypoMV: h?.marketValue ?? '0',
      baseDelta: b?.delta ?? null,
      hypoDelta: h?.delta ?? null,
      baseVarContrib: b?.varContribution ?? '0',
      hypoVarContrib: h?.varContribution ?? '0',
    }
  })
}

export function WhatIfPanel({
  open,
  onClose,
  trades,
  onAddTrade,
  onRemoveTrade,
  onUpdateTrade,
  onSubmit,
  onReset,
  result,
  impact,
  loading,
  error,
  validationErrors = {},
}: WhatIfPanelProps) {
  const firstInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!open) return

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onClose()
      }
    }

    document.addEventListener('keydown', handleKeyDown)

    // Auto-focus first input on open
    setTimeout(() => firstInputRef.current?.focus(), 0)

    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open) return null

  return (
    <>
    {/* Backdrop */}
    <div
      data-testid="whatif-backdrop"
      className="fixed inset-0 z-40 bg-black/30"
      onClick={onClose}
    />
    <div
      data-testid="whatif-panel"
      role="dialog"
      aria-modal="true"
      aria-labelledby="whatif-title"
      className="fixed top-0 right-0 h-full w-[420px] bg-white dark:bg-surface-800 border-l border-slate-200 dark:border-surface-700 shadow-xl z-50 flex flex-col transition-transform duration-300"
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 dark:border-surface-700 bg-slate-50 dark:bg-surface-900">
        <h2 id="whatif-title" className="text-sm font-bold text-slate-800 dark:text-slate-200">What-If Analysis</h2>
        <button
          data-testid="whatif-close"
          aria-label="Close what-if panel"
          onClick={onClose}
          className="p-1 rounded hover:bg-slate-200 dark:hover:bg-surface-700 transition-colors"
        >
          <X className="h-4 w-4 text-slate-500 dark:text-slate-400" />
        </button>
      </div>

      {/* Scrollable content */}
      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
        {/* Trade forms */}
        {trades.map((trade, index) => (
          <Card key={index} className="relative">
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">
                  Trade {index + 1}
                </span>
                {trades.length > 1 && (
                  <button
                    data-testid={`whatif-remove-trade-${index}`}
                    aria-label={`Remove trade ${index + 1}`}
                    onClick={() => onRemoveTrade(index)}
                    className="p-1 rounded hover:bg-red-50 dark:hover:bg-red-900/20 text-slate-400 hover:text-red-500 transition-colors"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>

              <div>
                <label className="block text-xs text-slate-500 dark:text-slate-400 mb-1">Instrument</label>
                <Input
                  ref={index === 0 ? firstInputRef : undefined}
                  data-testid={`whatif-instrument-${index}`}
                  value={trade.instrumentId}
                  onChange={(e) => onUpdateTrade(index, 'instrumentId', e.target.value)}
                  placeholder="e.g. SPY"
                  className={`w-full ${validationErrors[index]?.instrumentId ? 'border-red-500' : ''}`}
                />
                {validationErrors[index]?.instrumentId && (
                  <p className="text-xs text-red-600 mt-0.5">{validationErrors[index].instrumentId}</p>
                )}
              </div>

              <div>
                <label className="block text-xs text-slate-500 dark:text-slate-400 mb-1">Asset Class</label>
                <select
                  data-testid={`whatif-asset-class-${index}`}
                  value={trade.assetClass}
                  onChange={(e) => onUpdateTrade(index, 'assetClass', e.target.value)}
                  className="w-full border border-slate-300 dark:border-surface-600 rounded-md px-3 py-1.5 text-sm bg-white dark:bg-surface-700 dark:text-slate-200 focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
                >
                  <option value="EQUITY">Equity</option>
                  <option value="DERIVATIVE">Derivative</option>
                  <option value="FIXED_INCOME">Fixed Income</option>
                  <option value="COMMODITY">Commodity</option>
                  <option value="FX">FX</option>
                </select>
              </div>

              <div>
                <label className="block text-xs text-slate-500 dark:text-slate-400 mb-1">Direction</label>
                <div data-testid={`whatif-side-${index}`} className="flex gap-1">
                  <button
                    data-testid={`whatif-side-buy-${index}`}
                    aria-pressed={trade.side === 'BUY'}
                    onClick={() => onUpdateTrade(index, 'side', 'BUY')}
                    className={`flex-1 py-1.5 text-xs font-medium rounded-md border transition-colors ${
                      trade.side === 'BUY'
                        ? 'bg-green-50 dark:bg-green-900/30 border-green-300 dark:border-green-700 text-green-700 dark:text-green-400'
                        : 'border-slate-300 dark:border-surface-600 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-surface-700'
                    }`}
                  >
                    Buy
                  </button>
                  <button
                    data-testid={`whatif-side-sell-${index}`}
                    aria-pressed={trade.side === 'SELL'}
                    onClick={() => onUpdateTrade(index, 'side', 'SELL')}
                    className={`flex-1 py-1.5 text-xs font-medium rounded-md border transition-colors ${
                      trade.side === 'SELL'
                        ? 'bg-red-50 dark:bg-red-900/30 border-red-300 dark:border-red-700 text-red-700 dark:text-red-400'
                        : 'border-slate-300 dark:border-surface-600 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-surface-700'
                    }`}
                  >
                    Sell
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-slate-500 dark:text-slate-400 mb-1">Quantity</label>
                  <Input
                    data-testid={`whatif-quantity-${index}`}
                    type="number"
                    value={trade.quantity}
                    onChange={(e) => onUpdateTrade(index, 'quantity', e.target.value)}
                    placeholder="100"
                    className={`w-full ${validationErrors[index]?.quantity ? 'border-red-500' : ''}`}
                  />
                  {validationErrors[index]?.quantity && (
                    <p className="text-xs text-red-600 mt-0.5">{validationErrors[index].quantity}</p>
                  )}
                </div>
                <div>
                  <label className="block text-xs text-slate-500 dark:text-slate-400 mb-1">Price</label>
                  <Input
                    data-testid={`whatif-price-${index}`}
                    type="number"
                    value={trade.priceAmount}
                    onChange={(e) => onUpdateTrade(index, 'priceAmount', e.target.value)}
                    placeholder="450.00"
                    className={`w-full ${validationErrors[index]?.priceAmount ? 'border-red-500' : ''}`}
                  />
                  {validationErrors[index]?.priceAmount && (
                    <p className="text-xs text-red-600 mt-0.5">{validationErrors[index].priceAmount}</p>
                  )}
                </div>
              </div>
              {trade.quantity && trade.priceAmount && !isNaN(Number(trade.quantity)) && !isNaN(Number(trade.priceAmount)) && (
                <p data-testid={`whatif-notional-${index}`} className="text-xs text-slate-400 mt-1">
                  Notional: {formatNum(Number(trade.quantity) * Number(trade.priceAmount))}
                </p>
              )}
            </div>
          </Card>
        ))}

        {/* Add trade button */}
        <button
          data-testid="whatif-add-trade"
          aria-label="Add another hypothetical trade"
          onClick={onAddTrade}
          className="flex items-center gap-1.5 text-sm text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium transition-colors"
        >
          <Plus className="h-4 w-4" />
          Add another trade
        </button>

        {/* Error message */}
        {error && (
          <div data-testid="whatif-error" className="text-sm text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 rounded-md px-3 py-2">
            {error}
          </div>
        )}

        {/* Results live region */}
        <div data-testid="whatif-results-live" aria-live="polite">

        {/* Comparison table */}
        {result && impact && (
          <Card data-testid="whatif-comparison">
            <div className="space-y-3">
              <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Before / After Comparison
              </h3>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-xs text-slate-500 dark:text-slate-400">
                    <th className="text-left py-1 font-medium">Metric</th>
                    <th className="text-right py-1 font-medium">Current</th>
                    <th className="text-right py-1 font-medium">After</th>
                    <th className="text-right py-1 font-medium">Change</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-surface-700">
                  {/* VaR */}
                  <tr>
                    <td className="py-1.5 text-slate-700 font-medium">VaR</td>
                    <td data-testid="whatif-var-base" className="py-1.5 text-right text-slate-700">
                      {formatNum(result.baseVaR)}
                    </td>
                    <td data-testid="whatif-var-after" className="py-1.5 text-right text-slate-700">
                      {formatNum(result.hypotheticalVaR)}
                    </td>
                    <td
                      data-testid="whatif-var-change"
                      className={`py-1.5 text-right font-medium ${changeColorClass(Number(impact.varChange))}`}
                    >
                      <ChangeIcon value={Number(impact.varChange)} />
                      {' '}{formatNum(impact.varChange)}
                    </td>
                  </tr>

                  {/* ES */}
                  <tr>
                    <td className="py-1.5 text-slate-700 font-medium">ES</td>
                    <td data-testid="whatif-es-base" className="py-1.5 text-right text-slate-700">
                      {formatNum(result.baseExpectedShortfall)}
                    </td>
                    <td data-testid="whatif-es-after" className="py-1.5 text-right text-slate-700">
                      {formatNum(result.hypotheticalExpectedShortfall)}
                    </td>
                    <td
                      data-testid="whatif-es-change"
                      className={`py-1.5 text-right font-medium ${changeColorClass(Number(impact.esChange))}`}
                    >
                      <ChangeIcon value={Number(impact.esChange)} />
                      {' '}{formatNum(impact.esChange)}
                    </td>
                  </tr>

                  {/* Delta */}
                  <tr>
                    <td className="py-1.5 text-slate-700 font-medium">Delta</td>
                    <td data-testid="whatif-delta-base" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekTotal(result, 'base', 'delta'))}
                    </td>
                    <td data-testid="whatif-delta-after" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekTotal(result, 'hypothetical', 'delta'))}
                    </td>
                    <td
                      data-testid="whatif-delta-change"
                      className={`py-1.5 text-right font-medium ${changeColorClass(impact.deltaChange)}`}
                    >
                      <ChangeIcon value={impact.deltaChange} />
                      {' '}{formatNum(impact.deltaChange)}
                    </td>
                  </tr>

                  {/* Gamma */}
                  <tr>
                    <td className="py-1.5 text-slate-700 font-medium">Gamma</td>
                    <td data-testid="whatif-gamma-base" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekTotal(result, 'base', 'gamma'))}
                    </td>
                    <td data-testid="whatif-gamma-after" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekTotal(result, 'hypothetical', 'gamma'))}
                    </td>
                    <td
                      data-testid="whatif-gamma-change"
                      className={`py-1.5 text-right font-medium ${changeColorClass(impact.gammaChange)}`}
                    >
                      <ChangeIcon value={impact.gammaChange} />
                      {' '}{formatNum(impact.gammaChange)}
                    </td>
                  </tr>

                  {/* Vega */}
                  <tr>
                    <td className="py-1.5 text-slate-700 font-medium">Vega</td>
                    <td data-testid="whatif-vega-base" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekTotal(result, 'base', 'vega'))}
                    </td>
                    <td data-testid="whatif-vega-after" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekTotal(result, 'hypothetical', 'vega'))}
                    </td>
                    <td
                      data-testid="whatif-vega-change"
                      className={`py-1.5 text-right font-medium ${changeColorClass(impact.vegaChange)}`}
                    >
                      <ChangeIcon value={impact.vegaChange} />
                      {' '}{formatNum(impact.vegaChange)}
                    </td>
                  </tr>

                  {/* Theta */}
                  <tr>
                    <td className="py-1.5 text-slate-700 font-medium">Theta</td>
                    <td data-testid="whatif-theta-base" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekScalar(result, 'base', 'theta'))}
                    </td>
                    <td data-testid="whatif-theta-after" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekScalar(result, 'hypothetical', 'theta'))}
                    </td>
                    <td
                      data-testid="whatif-theta-change"
                      className={`py-1.5 text-right font-medium ${changeColorClass(impact.thetaChange)}`}
                    >
                      <ChangeIcon value={impact.thetaChange} />
                      {' '}{formatNum(impact.thetaChange)}
                    </td>
                  </tr>

                  {/* Rho */}
                  <tr>
                    <td className="py-1.5 text-slate-700 font-medium">Rho</td>
                    <td data-testid="whatif-rho-base" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekScalar(result, 'base', 'rho'))}
                    </td>
                    <td data-testid="whatif-rho-after" className="py-1.5 text-right text-slate-700">
                      {formatNum(greekScalar(result, 'hypothetical', 'rho'))}
                    </td>
                    <td
                      data-testid="whatif-rho-change"
                      className={`py-1.5 text-right font-medium ${changeColorClass(impact.rhoChange)}`}
                    >
                      <ChangeIcon value={impact.rhoChange} />
                      {' '}{formatNum(impact.rhoChange)}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        )}

        {/* Position risk breakdown */}
        {result && impact && (result.basePositionRisk.length > 0 || result.hypotheticalPositionRisk.length > 0) && (
          <Card data-testid="whatif-position-breakdown">
            <div className="space-y-3">
              <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Position Risk Breakdown
              </h3>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-xs text-slate-500 dark:text-slate-400">
                    <th className="text-left py-1 font-medium">Instrument</th>
                    <th className="text-right py-1 font-medium">MV Before</th>
                    <th className="text-right py-1 font-medium">MV After</th>
                    <th className="text-right py-1 font-medium">VaR Contrib</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-surface-700">
                  {mergePositionRisk(result.basePositionRisk, result.hypotheticalPositionRisk).map((row) => (
                    <tr key={row.instrumentId}>
                      <td className="py-1.5 text-slate-700 font-medium">{row.instrumentId}</td>
                      <td className="py-1.5 text-right text-slate-700">{formatNum(row.baseMV)}</td>
                      <td className="py-1.5 text-right text-slate-700">{formatNum(row.hypoMV)}</td>
                      <td className={`py-1.5 text-right font-medium ${changeColorClass(Number(row.hypoVarContrib) - Number(row.baseVarContrib))}`}>
                        {formatNum(Number(row.hypoVarContrib) - Number(row.baseVarContrib))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}

        </div>{/* end aria-live */}
      </div>

      {/* Footer with action buttons */}
      <div className="px-4 py-3 border-t border-slate-200 dark:border-surface-700 bg-slate-50 dark:bg-surface-900 flex gap-2">
        <Button
          data-testid="whatif-run"
          variant="primary"
          onClick={onSubmit}
          loading={loading}
          disabled={loading}
          className="flex-1"
        >
          Run Analysis
        </Button>
        {result && (
          <Button
            data-testid="whatif-reset"
            variant="secondary"
            onClick={onReset}
          >
            Reset
          </Button>
        )}
      </div>
    </div>
    </>
  )
}
