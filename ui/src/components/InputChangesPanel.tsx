import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { Card } from './ui'
import { MagnitudeIndicator } from './MagnitudeIndicator'
import { formatNum } from '../utils/format'
import type { InputChangesSummaryDto, ParameterDiffDto } from '../types'

interface InputChangesPanelProps {
  inputChanges: InputChangesSummaryDto | null
  parameterDiffs: ParameterDiffDto[]
}

function ChangeTypeBadge({ changeType }: { changeType: string }) {
  switch (changeType) {
    case 'ADDED':
    case 'BECAME_AVAILABLE':
      return (
        <span className="inline-block px-1.5 py-0.5 text-xs font-medium rounded bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400">
          {changeType.replace('_', ' ')}
        </span>
      )
    case 'REMOVED':
    case 'BECAME_MISSING':
      return (
        <span className="inline-block px-1.5 py-0.5 text-xs font-medium rounded bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400">
          {changeType.replace('_', ' ')}
        </span>
      )
    default:
      return (
        <span className="inline-block px-1.5 py-0.5 text-xs font-medium rounded bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400">
          {changeType.replace(/_/g, ' ')}
        </span>
      )
  }
}

export function InputChangesPanel({ inputChanges, parameterDiffs }: InputChangesPanelProps) {
  const [expanded, setExpanded] = useState(false)

  if (inputChanges === null) {
    return (
      <Card data-testid="input-changes-unavailable">
        <p className="text-sm text-slate-500 dark:text-slate-400">
          Input change data not available for this comparison
        </p>
      </Card>
    )
  }

  const totalChanges =
    inputChanges.positionChanges.length +
    inputChanges.marketDataChanges.length +
    (inputChanges.modelVersionChanged ? 1 : 0) +
    parameterDiffs.length

  const inputsIdentical = totalChanges === 0

  const showModelParamsSection =
    inputChanges.modelVersionChanged || parameterDiffs.length > 0

  return (
    <Card data-testid="input-changes-panel">
      <button
        type="button"
        className="w-full flex items-center gap-2 text-left"
        onClick={() => setExpanded((prev) => !prev)}
        aria-expanded={expanded}
        aria-controls="input-changes-body"
      >
        {expanded ? (
          <ChevronDown className="h-4 w-4 text-slate-400 shrink-0" />
        ) : (
          <ChevronRight className="h-4 w-4 text-slate-400 shrink-0" />
        )}
        <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
          Input Changes
        </h3>
        {!inputsIdentical && (
          <span
            data-testid="input-changes-count"
            className="inline-flex items-center justify-center px-1.5 py-0.5 text-xs font-medium rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400"
          >
            {totalChanges}
          </span>
        )}
        {inputsIdentical && (
          <span
            data-testid="inputs-identical"
            className="inline-flex items-center gap-1 text-xs font-medium text-green-600 dark:text-green-400"
          >
            <svg
              className="h-3.5 w-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
            Inputs identical
          </span>
        )}
      </button>

      {expanded && (
        <div id="input-changes-body" className="mt-4 space-y-4">
          <div
            data-testid="diagnostic-disclaimer"
            className="text-xs text-slate-500 dark:text-slate-400 bg-slate-50 dark:bg-surface-700/50 border border-slate-200 dark:border-surface-600 rounded px-3 py-2"
          >
            Input change indicators are diagnostic estimates, not exact attribution.
          </div>

          {showModelParamsSection && (
            <div data-testid="model-params-section" className="space-y-2">
              <h4 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Model &amp; Parameters
              </h4>
              {inputChanges.modelVersionChanged && (
                <p
                  data-testid="model-version-change"
                  className="text-sm text-slate-700 dark:text-slate-200"
                >
                  Model Version:{' '}
                  <span className="text-slate-500 dark:text-slate-400">
                    {inputChanges.baseModelVersion}
                  </span>
                  <span className="mx-1" aria-hidden="true">
                    &rarr;
                  </span>
                  <span>{inputChanges.targetModelVersion}</span>
                </p>
              )}
              {parameterDiffs.length > 0 && (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-xs text-slate-500 dark:text-slate-400">
                        <th className="text-left py-1 font-medium">Parameter</th>
                        <th className="text-left py-1 font-medium">Base</th>
                        <th className="text-left py-1 font-medium">Target</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 dark:divide-surface-700">
                      {parameterDiffs.map((p) => (
                        <tr key={p.paramName}>
                          <td className="py-1.5 text-slate-700 dark:text-slate-200 font-medium">
                            {p.paramName}
                          </td>
                          <td className="py-1.5 text-slate-500 dark:text-slate-400">
                            {p.baseValue ?? '\u2014'}
                          </td>
                          <td className="py-1.5 text-slate-700 dark:text-slate-200">
                            {p.targetValue ?? '\u2014'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {inputChanges.positionChanges.length > 0 && (
            <div data-testid="position-changes-section" className="space-y-2">
              <h4 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Position Changes
              </h4>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-xs text-slate-500 dark:text-slate-400">
                      <th className="text-left py-1 font-medium">Instrument</th>
                      <th className="text-left py-1 font-medium">Asset Class</th>
                      <th className="text-left py-1 font-medium">Change</th>
                      <th className="text-right py-1 font-medium">Qty Delta</th>
                      <th className="text-right py-1 font-medium">Price Delta</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-surface-700">
                    {inputChanges.positionChanges.map((pc) => (
                      <tr key={pc.instrumentId}>
                        <td className="py-1.5 text-slate-700 dark:text-slate-200 font-medium">
                          {pc.instrumentId}
                        </td>
                        <td className="py-1.5 text-slate-600 dark:text-slate-300">
                          {pc.assetClass}
                        </td>
                        <td className="py-1.5">
                          <ChangeTypeBadge changeType={pc.changeType} />
                        </td>
                        <td className="py-1.5 text-right text-slate-700 dark:text-slate-200">
                          {pc.quantityDelta !== null ? formatNum(pc.quantityDelta) : '\u2014'}
                        </td>
                        <td className="py-1.5 text-right text-slate-700 dark:text-slate-200">
                          {pc.priceDelta !== null ? formatNum(pc.priceDelta) : '\u2014'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {inputChanges.marketDataChanges.length > 0 && (
            <div data-testid="market-data-changes-section" className="space-y-2">
              <h4 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                Market Data Changes
              </h4>
              <div className="space-y-1">
                {inputChanges.marketDataChanges.map((md, idx) => (
                  <div
                    key={`${md.dataType}-${md.instrumentId}-${idx}`}
                    className="flex items-center gap-3 text-sm py-1"
                  >
                    <span className="text-slate-600 dark:text-slate-300 font-medium w-28 shrink-0">
                      {md.dataType}
                    </span>
                    <span className="text-slate-700 dark:text-slate-200 w-28 shrink-0">
                      {md.instrumentId}
                    </span>
                    <ChangeTypeBadge changeType={md.changeType} />
                    {md.magnitude && <MagnitudeIndicator magnitude={md.magnitude} />}
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </Card>
  )
}
