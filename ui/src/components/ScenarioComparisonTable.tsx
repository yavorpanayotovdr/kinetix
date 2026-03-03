import { ChevronRight, ChevronDown } from 'lucide-react'
import type { StressTestResultDto } from '../types'
import { formatCurrency } from '../utils/format'

interface ScenarioComparisonTableProps {
  results: StressTestResultDto[]
  selectedScenario: string | null
  onSelectScenario: (scenario: string | null) => void
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
}: ScenarioComparisonTableProps) {
  if (results.length === 0) {
    return (
      <p data-testid="no-results" className="text-sm text-slate-500">
        No stress test results yet. Click &quot;Run All Scenarios&quot; to see the comparison.
      </p>
    )
  }

  return (
    <div data-testid="scenario-comparison-table">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-slate-600 dark:text-slate-400">
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
                <td className="py-1.5 text-slate-400">
                  {isSelected ? (
                    <ChevronDown className="h-4 w-4" />
                  ) : (
                    <ChevronRight className="h-4 w-4" />
                  )}
                </td>
                <td className="py-1.5 font-medium">{r.scenarioName.replace(/_/g, ' ')}</td>
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
