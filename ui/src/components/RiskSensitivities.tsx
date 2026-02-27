import type { GreeksResultDto } from '../types'
import { formatNum } from '../utils/format'

interface RiskSensitivitiesProps {
  greeksResult: GreeksResultDto
}

export function RiskSensitivities({ greeksResult }: RiskSensitivitiesProps) {
  return (
    <div data-testid="risk-sensitivities">
      <table data-testid="greeks-heatmap" className="w-full text-xs mb-2">
        <thead>
          <tr className="border-b text-left text-slate-600">
            <th className="py-1">Asset Class</th>
            <th className="py-1 text-right">Delta</th>
            <th className="py-1 text-right">Gamma</th>
            <th className="py-1 text-right">Vega</th>
          </tr>
        </thead>
        <tbody>
          {greeksResult.assetClassGreeks.map((g) => (
            <tr key={g.assetClass} data-testid={`greeks-row-${g.assetClass}`} className="border-b hover:bg-slate-50 transition-colors">
              <td className="py-1 font-medium">{g.assetClass}</td>
              <td className="py-1 text-right">{formatNum(g.delta)}</td>
              <td className="py-1 text-right">{formatNum(g.gamma)}</td>
              <td className="py-1 text-right">{formatNum(g.vega)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div data-testid="greeks-summary" className="flex gap-4 text-xs">
        <div>
          <span className="text-slate-600">Theta (time decay): </span>
          <span className="font-medium">{formatNum(greeksResult.theta, 4)}</span>
        </div>
        <div>
          <span className="text-slate-600">Rho (rate sensitivity): </span>
          <span className="font-medium">{formatNum(greeksResult.rho, 4)}</span>
        </div>
      </div>
    </div>
  )
}
