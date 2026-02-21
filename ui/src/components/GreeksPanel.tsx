import type { GreeksResultDto } from '../types'

interface GreeksPanelProps {
  greeksResult: GreeksResultDto | null
  loading: boolean
  error: string | null
  volBump: number
  onVolBumpChange: (bump: number) => void
}

function formatNum(value: string | number, decimals = 2): string {
  const num = typeof value === 'string' ? Number(value) : value
  return num.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
}

export function GreeksPanel({
  greeksResult,
  loading,
  error,
  volBump,
  onVolBumpChange,
}: GreeksPanelProps) {
  if (loading) {
    return (
      <div data-testid="greeks-loading" className="bg-white rounded-lg shadow p-4 mb-4 text-gray-500">
        Calculating Greeks...
      </div>
    )
  }

  if (error) {
    return (
      <div data-testid="greeks-error" className="bg-white rounded-lg shadow p-4 mb-4 text-red-600">
        {error}
      </div>
    )
  }

  if (!greeksResult) {
    return null
  }

  const projectedVaRChange = greeksResult.assetClassGreeks.reduce(
    (sum, g) => sum + Number(g.vega) * volBump,
    0,
  )

  return (
    <div data-testid="greeks-panel" className="bg-white rounded-lg shadow p-4 mb-4">
      <h2 className="text-lg font-semibold text-gray-800 mb-3">Portfolio Greeks</h2>

      {/* Greeks heatmap table */}
      <table data-testid="greeks-heatmap" className="w-full text-sm mb-4">
        <thead>
          <tr className="border-b text-left text-gray-600">
            <th className="py-2">Asset Class</th>
            <th className="py-2 text-right">Delta</th>
            <th className="py-2 text-right">Gamma</th>
            <th className="py-2 text-right">Vega</th>
          </tr>
        </thead>
        <tbody>
          {greeksResult.assetClassGreeks.map((g) => (
            <tr key={g.assetClass} data-testid={`greeks-row-${g.assetClass}`} className="border-b">
              <td className="py-1.5 font-medium">{g.assetClass}</td>
              <td className="py-1.5 text-right">{formatNum(g.delta)}</td>
              <td className="py-1.5 text-right">{formatNum(g.gamma)}</td>
              <td className="py-1.5 text-right">{formatNum(g.vega)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Theta and Rho summary */}
      <div data-testid="greeks-summary" className="flex gap-6 mb-4 text-sm">
        <div>
          <span className="text-gray-600">Theta (time decay): </span>
          <span className="font-medium">{formatNum(greeksResult.theta, 4)}</span>
        </div>
        <div>
          <span className="text-gray-600">Rho (rate sensitivity): </span>
          <span className="font-medium">{formatNum(greeksResult.rho, 4)}</span>
        </div>
      </div>

      {/* What-if slider */}
      <div data-testid="greeks-whatif" className="border-t pt-3">
        <h3 className="text-sm font-semibold text-gray-700 mb-2">What-If Analysis</h3>
        <div className="flex items-center gap-3">
          <label className="text-xs text-gray-600">Vol bump (pp):</label>
          <input
            data-testid="vol-bump-slider"
            type="range"
            min={-5}
            max={5}
            step={0.5}
            value={volBump}
            onChange={(e) => onVolBumpChange(Number(e.target.value))}
            className="flex-1"
          />
          <span className="text-xs w-12 text-right">{volBump > 0 ? '+' : ''}{volBump}pp</span>
        </div>
        <div className="text-xs text-gray-500 mt-1">
          Projected VaR change:{' '}
          <span className={projectedVaRChange >= 0 ? 'text-red-600' : 'text-green-600'}>
            {projectedVaRChange >= 0 ? '+' : ''}{formatNum(projectedVaRChange)}
          </span>
        </div>
      </div>
    </div>
  )
}
