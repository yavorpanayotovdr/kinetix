import { useState } from 'react'
import type { StressTestResultDto } from '../types'
import { AssetClassImpactView } from './AssetClassImpactView'
import { StressPositionTable } from './StressPositionTable'

type DetailView = 'asset-class' | 'positions'

interface ScenarioDetailPanelProps {
  result: StressTestResultDto | null
}

export function ScenarioDetailPanel({ result }: ScenarioDetailPanelProps) {
  const [view, setView] = useState<DetailView>('asset-class')
  const [assetClassFilter, setAssetClassFilter] = useState<string | undefined>(undefined)

  if (!result) return null

  const handleAssetClassClick = (assetClass: string) => {
    setAssetClassFilter(assetClass)
    setView('positions')
  }

  const handleClearFilter = () => {
    setAssetClassFilter(undefined)
  }

  return (
    <div data-testid="detail-panel" aria-live="polite" className="mt-4 border-t pt-4">
      <div className="flex items-center gap-1 mb-4">
        <button
          data-testid="view-toggle-asset-class"
          className={`px-3 py-1.5 text-sm font-medium rounded-l-md border transition-colors ${
            view === 'asset-class'
              ? 'bg-indigo-600 text-white border-indigo-600'
              : 'bg-white dark:bg-surface-800 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-700'
          }`}
          onClick={() => setView('asset-class')}
        >
          Asset Class
        </button>
        <button
          data-testid="view-toggle-positions"
          className={`px-3 py-1.5 text-sm font-medium rounded-r-md border transition-colors ${
            view === 'positions'
              ? 'bg-indigo-600 text-white border-indigo-600'
              : 'bg-white dark:bg-surface-800 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-700'
          }`}
          onClick={() => setView('positions')}
        >
          Positions
        </button>
      </div>

      {view === 'asset-class' && (
        <AssetClassImpactView
          impacts={result.assetClassImpacts}
          onAssetClassClick={handleAssetClassClick}
        />
      )}

      {view === 'positions' && (
        <StressPositionTable
          positions={result.positionImpacts}
          assetClassFilter={assetClassFilter}
          onClearFilter={handleClearFilter}
        />
      )}
    </div>
  )
}
