import type { StressTestResultDto } from '../types'

interface StressTestPanelProps {
  scenarios: string[]
  result: StressTestResultDto | null
  loading: boolean
  error: string | null
  selectedScenario: string
  onScenarioChange: (scenario: string) => void
  onRun: () => void
}

const ASSET_CLASS_COLORS: Record<string, string> = {
  EQUITY: 'bg-blue-500',
  FIXED_INCOME: 'bg-green-500',
  COMMODITY: 'bg-amber-500',
  FX: 'bg-purple-500',
  DERIVATIVE: 'bg-red-500',
}

function formatCurrency(value: string | number): string {
  const num = typeof value === 'string' ? Number(value) : value
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(num)
}

export function StressTestPanel({
  scenarios,
  result,
  loading,
  error,
  selectedScenario,
  onScenarioChange,
  onRun,
}: StressTestPanelProps) {
  return (
    <div data-testid="stress-test-panel" className="bg-white rounded-lg shadow p-4 mb-4">
      <h2 className="text-lg font-semibold text-gray-800 mb-3">Stress Testing</h2>

      {/* Scenario selector */}
      <div className="flex items-center gap-3 mb-4">
        <select
          data-testid="scenario-dropdown"
          value={selectedScenario}
          onChange={(e) => onScenarioChange(e.target.value)}
          className="border rounded px-3 py-1.5 text-sm"
        >
          {scenarios.map((s) => (
            <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
          ))}
        </select>
        <button
          data-testid="stress-run-btn"
          onClick={onRun}
          disabled={loading}
          className="px-4 py-1.5 bg-red-500 text-white rounded text-sm hover:bg-red-600 disabled:opacity-50"
        >
          {loading ? 'Running...' : 'Run Stress Test'}
        </button>
      </div>

      {/* Loading */}
      {loading && (
        <div data-testid="stress-loading" className="text-gray-500 text-sm">
          Running stress test...
        </div>
      )}

      {/* Error */}
      {error && (
        <div data-testid="stress-error" className="text-red-600 text-sm">
          {error}
        </div>
      )}

      {/* Results */}
      {result && !loading && (
        <div data-testid="stress-results">
          {/* Summary table */}
          <table data-testid="stress-results-table" className="w-full text-sm mb-4">
            <thead>
              <tr className="border-b text-left text-gray-600">
                <th className="py-2">Metric</th>
                <th className="py-2 text-right">Value</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b">
                <td className="py-1.5">Scenario</td>
                <td className="py-1.5 text-right font-medium">{result.scenarioName}</td>
              </tr>
              <tr className="border-b">
                <td className="py-1.5">Base VaR</td>
                <td className="py-1.5 text-right">{formatCurrency(result.baseVar)}</td>
              </tr>
              <tr className="border-b">
                <td className="py-1.5">Stressed VaR</td>
                <td className="py-1.5 text-right text-red-600 font-medium">{formatCurrency(result.stressedVar)}</td>
              </tr>
              <tr className="border-b">
                <td className="py-1.5">P&L Impact</td>
                <td className="py-1.5 text-right text-red-600 font-medium">{formatCurrency(result.pnlImpact)}</td>
              </tr>
            </tbody>
          </table>

          {/* Asset class impact breakdown */}
          <h3 className="text-sm font-semibold text-gray-700 mb-2">Impact by Asset Class</h3>
          <div data-testid="stress-impact-chart" className="space-y-2">
            {result.assetClassImpacts.map((impact) => {
              const baseExp = Number(impact.baseExposure)
              const stressedExp = Number(impact.stressedExposure)
              const maxExp = Math.max(baseExp, stressedExp, 1)
              return (
                <div key={impact.assetClass} className="flex items-center gap-2 text-xs">
                  <span className="w-24 text-gray-600">{impact.assetClass}</span>
                  <div className="flex-1 flex gap-1">
                    <div
                      className="h-4 bg-gray-300 rounded"
                      style={{ width: `${(baseExp / maxExp) * 100}%` }}
                      title={`Base: ${formatCurrency(baseExp)}`}
                    />
                    <div
                      className={`h-4 rounded ${ASSET_CLASS_COLORS[impact.assetClass] || 'bg-gray-400'}`}
                      style={{ width: `${(stressedExp / maxExp) * 100}%` }}
                      title={`Stressed: ${formatCurrency(stressedExp)}`}
                    />
                  </div>
                  <span className="w-28 text-right text-red-600">{formatCurrency(impact.pnlImpact)}</span>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
