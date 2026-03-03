import { ChevronRight, ChevronDown } from 'lucide-react'
import type { StressScenarioDto, StressTestResultDto } from '../types'
import { formatCurrency } from '../utils/format'
import { ScenarioTooltip } from './ScenarioTooltip'

interface ScenarioComparisonTableProps {
  results: StressTestResultDto[]
  selectedScenario: string | null
  onSelectScenario: (scenario: string | null) => void
  checkedScenarios?: Set<string>
  onToggleCheck?: (scenario: string) => void
  scenarioMetadata?: StressScenarioDto[]
}

function formatMultiplier(baseVar: string, stressedVar: string): string {
  const base = Number(baseVar)
  const stressed = Number(stressedVar)
  if (base === 0) return '-'
  return `${(stressed / base).toFixed(1)}x`
}

export function ScenarioComparisonTable({
  results,
  selectedScenario,
  onSelectScenario,
  checkedScenarios,
  onToggleCheck,
  scenarioMetadata,
}: ScenarioComparisonTableProps) {
  if (results.length === 0) {
    return (
      <p data-testid="no-results" className="text-sm text-slate-500">
        No stress test results yet. Click &quot;Run All Scenarios&quot; to see the comparison.
      </p>
    )
  }

  const showCheckboxes = !!onToggleCheck

  return (
    <div data-testid="scenario-comparison-table">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-slate-600 dark:text-slate-400">
            {showCheckboxes && <th className="py-2 w-8"></th>}
            <th className="py-2 w-8"></th>
            <th className="py-2">Scenario</th>
            <th className="py-2 text-right">Base VaR</th>
            <th className="py-2 text-right">Stressed VaR</th>
            <th className="py-2 text-right">VaR Multiplier</th>
            <th className="py-2 text-right">P&amp;L Impact</th>
          </tr>
        </thead>
        <tbody>
          {results.map((r) => {
            const isSelected = selectedScenario === r.scenarioName
            const isChecked = checkedScenarios?.has(r.scenarioName) ?? false
            const pnlValue = Number(r.pnlImpact)
            const isLoss = pnlValue < 0
            return (
              <tr
                key={r.scenarioName}
                data-testid="scenario-row"
                className={`border-b cursor-pointer transition-colors ${
                  isSelected
                    ? 'bg-indigo-50 dark:bg-indigo-900/20 border-l-2 border-l-indigo-500'
                    : 'hover:bg-slate-50 dark:hover:bg-slate-800'
                }`}
                onClick={() => onSelectScenario(isSelected ? null : r.scenarioName)}
              >
                {showCheckboxes && (
                  <td className="py-1.5">
                    <input
                      type="checkbox"
                      data-testid={`scenario-check-${r.scenarioName}`}
                      aria-label={`Compare ${r.scenarioName.replace(/_/g, ' ')}`}
                      checked={isChecked}
                      onChange={(e) => {
                        e.stopPropagation()
                        onToggleCheck?.(r.scenarioName)
                      }}
                      onClick={(e) => e.stopPropagation()}
                      className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                    />
                  </td>
                )}
                <td className="py-1.5 text-slate-400">
                  {isSelected ? (
                    <ChevronDown className="h-4 w-4" />
                  ) : (
                    <ChevronRight className="h-4 w-4" />
                  )}
                </td>
                <td className="py-1.5 font-medium">
                  {(() => {
                    const meta = scenarioMetadata?.find(
                      (s) => s.name === r.scenarioName || s.name.replace(/_/g, ' ') === r.scenarioName.replace(/_/g, ' '),
                    )
                    return meta ? (
                      <ScenarioTooltip
                        scenarioName={r.scenarioName}
                        description={meta.description}
                        shocks={meta.shocks}
                        lastRunAt={r.calculatedAt}
                        status={meta.status}
                        approvedBy={meta.approvedBy}
                      />
                    ) : (
                      r.scenarioName.replace(/_/g, ' ')
                    )
                  })()}
                </td>
                <td className="py-1.5 text-right">{formatCurrency(r.baseVar)}</td>
                <td className="py-1.5 text-right font-medium text-red-600 dark:text-red-400">
                  {formatCurrency(r.stressedVar)}
                </td>
                <td data-testid="var-multiplier" className="py-1.5 text-right font-medium">
                  {formatMultiplier(r.baseVar, r.stressedVar)}
                </td>
                <td
                  data-testid="pnl-impact"
                  className={`py-1.5 text-right font-medium ${isLoss ? 'text-red-600 dark:text-red-400' : ''}`}
                >
                  {formatCurrency(r.pnlImpact)}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
