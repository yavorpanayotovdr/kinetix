import type { AssetClassImpactDto } from '../types'
import { formatCurrency } from '../utils/format'

const ASSET_CLASS_COLORS: Record<string, string> = {
  EQUITY: 'bg-blue-500',
  FIXED_INCOME: 'bg-green-500',
  COMMODITY: 'bg-amber-500',
  FX: 'bg-purple-500',
  DERIVATIVE: 'bg-red-500',
}

interface AssetClassImpactViewProps {
  impacts: AssetClassImpactDto[]
  onAssetClassClick: (assetClass: string) => void
}

export function AssetClassImpactView({ impacts, onAssetClassClick }: AssetClassImpactViewProps) {
  if (impacts.length === 0) {
    return (
      <div data-testid="asset-class-impact-view">
        <p className="text-sm text-slate-500">No asset class data</p>
      </div>
    )
  }

  return (
    <div data-testid="asset-class-impact-view" className="space-y-2">
      <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300 mb-2">Impact by Asset Class</h3>
      {impacts.map((impact) => {
        const baseExp = Number(impact.baseExposure)
        const stressedExp = Number(impact.stressedExposure)
        const maxExp = Math.max(baseExp, stressedExp, 1)
        return (
          <div key={impact.assetClass} className="flex items-center gap-2 text-xs">
            <button
              data-testid={`asset-class-click-${impact.assetClass}`}
              className="w-28 text-left text-slate-600 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:underline cursor-pointer font-medium"
              onClick={() => onAssetClassClick(impact.assetClass)}
            >
              {impact.assetClass}
            </button>
            <div className="flex-1 flex gap-1">
              <div
                className="h-4 bg-slate-300 dark:bg-slate-600 rounded"
                style={{ width: `${(baseExp / maxExp) * 100}%` }}
                title={`Base: ${formatCurrency(baseExp)}`}
              />
              <div
                className={`h-4 rounded ${ASSET_CLASS_COLORS[impact.assetClass] || 'bg-gray-400'}`}
                style={{ width: `${(stressedExp / maxExp) * 100}%` }}
                title={`Stressed: ${formatCurrency(stressedExp)}`}
              />
            </div>
            <span data-testid="asset-class-pnl" className="w-28 text-right text-red-600 dark:text-red-400">
              {formatCurrency(impact.pnlImpact)}
            </span>
          </div>
        )
      })}
    </div>
  )
}
