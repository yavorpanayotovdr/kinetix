import type { StressTestResultDto } from '../types'
import { formatCurrency } from '../utils/format'
import { Card } from './ui'

const SCENARIO_COLORS = [
  'bg-indigo-500',
  'bg-amber-500',
  'bg-emerald-500',
]

interface ScenarioComparisonViewProps {
  scenarios: StressTestResultDto[]
}

function formatMultiplier(baseVar: string, stressedVar: string): string {
  const base = Number(baseVar)
  const stressed = Number(stressedVar)
  if (base === 0) return '-'
  return `${(stressed / base).toFixed(1)}x`
}

export function ScenarioComparisonView({ scenarios }: ScenarioComparisonViewProps) {
  if (scenarios.length < 2) {
    return (
      <Card>
        <p className="text-sm text-slate-500">
          Select 2-3 scenarios to compare side by side.
        </p>
      </Card>
    )
  }

  const display = scenarios.slice(0, 3)

  const metrics = [
    { label: 'Base VaR', getValue: (s: StressTestResultDto) => formatCurrency(s.baseVar) },
    { label: 'Stressed VaR', getValue: (s: StressTestResultDto) => formatCurrency(s.stressedVar) },
    { label: 'P&L Impact', getValue: (s: StressTestResultDto) => formatCurrency(s.pnlImpact), isLoss: true },
    { label: 'VaR Multiplier', getValue: (s: StressTestResultDto) => formatMultiplier(s.baseVar, s.stressedVar) },
  ]

  // Collect unique asset classes across all selected scenarios
  const assetClasses = Array.from(
    new Set(display.flatMap((s) => s.assetClassImpacts.map((a) => a.assetClass))),
  )

  // Find max absolute exposure for bar scaling
  const maxExposure = Math.max(
    ...display.flatMap((s) =>
      s.assetClassImpacts.flatMap((a) => [
        Math.abs(Number(a.baseExposure)),
        Math.abs(Number(a.stressedExposure)),
      ]),
    ),
    1,
  )

  return (
    <div data-testid="comparison-view" className="space-y-4 mt-4">
      <Card>
        <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">
          Scenario Comparison
        </h3>
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left text-slate-600 dark:text-slate-400">
              <th className="py-2">Metric</th>
              {display.map((s, i) => (
                <th key={s.scenarioName} className="py-2 text-right">
                  <span className="flex items-center justify-end gap-1.5">
                    <span className={`inline-block w-2 h-2 rounded-full ${SCENARIO_COLORS[i]}`} />
                    {s.scenarioName.replace(/_/g, ' ')}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {metrics.map((m) => (
              <tr key={m.label} className="border-b">
                <td className="py-1.5 text-slate-700 dark:text-slate-300 font-medium">{m.label}</td>
                {display.map((s) => {
                  const val = m.getValue(s)
                  const pnl = Number(s.pnlImpact)
                  return (
                    <td
                      key={s.scenarioName}
                      className={`py-1.5 text-right font-medium ${
                        m.isLoss && pnl < 0 ? 'text-red-600 dark:text-red-400' : ''
                      }`}
                    >
                      {val}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card>
        <h3 className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">
          Asset Class Impact Comparison
        </h3>
        <div className="space-y-3">
          {assetClasses.map((ac) => (
            <div key={ac}>
              <p className="text-xs font-medium text-slate-600 dark:text-slate-400 mb-1">{ac}</p>
              <div className="space-y-1">
                {display.map((s, i) => {
                  const impact = s.assetClassImpacts.find((a) => a.assetClass === ac)
                  if (!impact) return null
                  const baseWidth = (Math.abs(Number(impact.baseExposure)) / maxExposure) * 100
                  const stressedWidth = (Math.abs(Number(impact.stressedExposure)) / maxExposure) * 100
                  return (
                    <div key={s.scenarioName} className="flex items-center gap-2">
                      <span className="text-[10px] text-slate-500 w-24 truncate">
                        {s.scenarioName.replace(/_/g, ' ')}
                      </span>
                      <div className="flex-1 flex items-center gap-1">
                        <div
                          className={`h-2 rounded ${SCENARIO_COLORS[i]} opacity-40`}
                          style={{ width: `${baseWidth}%` }}
                          title={`Base: ${formatCurrency(impact.baseExposure)}`}
                        />
                        <div
                          className={`h-2 rounded ${SCENARIO_COLORS[i]}`}
                          style={{ width: `${stressedWidth}%` }}
                          title={`Stressed: ${formatCurrency(impact.stressedExposure)}`}
                        />
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  )
}
