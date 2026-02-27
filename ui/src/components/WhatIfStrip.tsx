import type { GreeksResultDto } from '../types'
import { formatNum } from '../utils/format'

interface WhatIfStripProps {
  greeksResult: GreeksResultDto
  volBump: number
  onVolBumpChange: (bump: number) => void
}

export function WhatIfStrip({ greeksResult, volBump, onVolBumpChange }: WhatIfStripProps) {
  const projectedVaRChange = greeksResult.assetClassGreeks.reduce(
    (sum, g) => sum + Number(g.vega) * volBump,
    0,
  )

  return (
    <div data-testid="greeks-whatif" className="border-t border-slate-100 pt-3">
      <h3 className="text-sm font-semibold text-slate-700 mb-2">What-If Analysis</h3>
      <div className="flex items-center gap-3">
        <label className="text-xs text-slate-600">Vol bump (pp):</label>
        <input
          data-testid="vol-bump-slider"
          type="range"
          min={-5}
          max={5}
          step={0.5}
          value={volBump}
          onChange={(e) => onVolBumpChange(Number(e.target.value))}
          className="flex-1 accent-primary-500"
        />
        <span className="text-xs w-12 text-right">{volBump > 0 ? '+' : ''}{volBump}pp</span>
      </div>
      <div className="text-xs text-slate-500 mt-1">
        Projected VaR change:{' '}
        <span className={projectedVaRChange >= 0 ? 'text-red-600' : 'text-green-600'}>
          {projectedVaRChange >= 0 ? '+' : ''}{formatNum(projectedVaRChange)}
        </span>
      </div>
    </div>
  )
}
