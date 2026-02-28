import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Info, X } from 'lucide-react'
import type { GreeksResultDto } from '../types'
import { useClickOutside } from '../hooks/useClickOutside'
import { formatNum } from '../utils/format'
import { formatAssetClassLabel } from '../utils/formatAssetClass'
import { formatCompactCurrency } from '../utils/formatCompactCurrency'

type Greek = 'delta' | 'gamma' | 'vega' | 'theta' | 'rho'

const greekDescriptions: Record<Greek, string> = {
  delta: "If the underlying asset(s) move up by $1, the portfolio's value is expected to increase by approximately this amount (and vice versa for a $1 decline).",
  gamma: "For each $1 move in the underlying, delta itself is expected to change by approximately this amount — measures how quickly directional exposure accelerates.",
  vega: "If implied volatility rises by 1 percentage point, the portfolio's value is expected to change by approximately this amount.",
  theta: "The portfolio's value is expected to change by approximately this amount each day purely due to the passage of time, assuming all other factors remain constant.",
  rho: "If interest rates rise by 1 percentage point, the portfolio's value is expected to change by approximately this amount — measures sensitivity to rate movements.",
}

interface RiskSensitivitiesProps {
  greeksResult: GreeksResultDto
  pvValue?: string | null
}

export function RiskSensitivities({ greeksResult, pvValue }: RiskSensitivitiesProps) {
  const [openPopover, setOpenPopover] = useState<Greek | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  const closePopover = useCallback(() => setOpenPopover(null), [])
  useClickOutside(containerRef, closePopover)

  useEffect(() => {
    if (!openPopover) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpenPopover(null)
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [openPopover])

  const togglePopover = (greek: Greek) => {
    setOpenPopover(prev => prev === greek ? null : greek)
  }

  const renderHeader = (label: string, greek: Greek, className: string) => (
    <th className={`${className} relative`}>
      <span className="inline-flex items-center gap-1">
        {label}
        <Info
          data-testid={`greek-info-${greek}`}
          className="h-3 w-3 cursor-pointer text-slate-400 hover:text-slate-600 transition-colors"
          onClick={() => togglePopover(greek)}
        />
      </span>
      {openPopover === greek && (
        <span
          data-testid={`greek-popover-${greek}`}
          className="absolute top-full left-0 mt-1 w-64 rounded bg-slate-800 px-3 py-2 text-xs font-normal text-white text-justify shadow-lg z-10"
        >
          <button data-testid={`greek-popover-${greek}-close`} className="float-right ml-2 text-slate-400 hover:text-white" onClick={closePopover}><X className="h-3 w-3" /></button>
          {greekDescriptions[greek]}
        </span>
      )}
    </th>
  )

  const totals = useMemo(() => {
    let delta = 0
    let gamma = 0
    let vega = 0
    for (const g of greeksResult.assetClassGreeks) {
      delta += Number(g.delta)
      gamma += Number(g.gamma)
      vega += Number(g.vega)
    }
    return { delta: delta.toFixed(2), gamma: gamma.toFixed(2), vega: vega.toFixed(2) }
  }, [greeksResult])

  return (
    <div ref={containerRef} data-testid="risk-sensitivities">
      {pvValue != null && (
        <div data-testid="pv-display" className="text-xs mb-2">
          <span className="text-slate-600">PV: </span>
          <span className="font-medium">{formatCompactCurrency(Number(pvValue))}</span>
        </div>
      )}
      <table data-testid="greeks-heatmap" className="text-xs">
        <thead>
          <tr className="border-b text-left text-slate-600">
            <th className="py-1 pr-5">Asset Class</th>
            {renderHeader('Delta', 'delta', 'py-1 px-4 text-right')}
            {renderHeader('Gamma', 'gamma', 'py-1 px-4 text-right')}
            {renderHeader('Vega', 'vega', 'py-1 px-4 text-right')}
            {renderHeader('Theta', 'theta', 'py-1 px-4 text-right')}
            {renderHeader('Rho', 'rho', 'py-1 pl-4 text-right')}
          </tr>
        </thead>
        <tbody>
          {greeksResult.assetClassGreeks.map((g) => (
            <tr key={g.assetClass} data-testid={`greeks-row-${g.assetClass}`} className="border-b hover:bg-slate-50 transition-colors">
              <td className="py-1 pr-5 font-medium">{formatAssetClassLabel(g.assetClass)}</td>
              <td className="py-1 px-4 text-right">{formatNum(g.delta)}</td>
              <td className="py-1 px-4 text-right">{formatNum(g.gamma)}</td>
              <td className="py-1 px-4 text-right">{formatNum(g.vega)}</td>
              <td className="py-1 px-4 text-right text-slate-400">{'\u2014'}</td>
              <td className="py-1 pl-4 text-right text-slate-400">{'\u2014'}</td>
            </tr>
          ))}
          <tr data-testid="greeks-row-TOTAL" className="border-t border-slate-300 font-semibold">
            <td className="py-1 pr-5">Total</td>
            <td className="py-1 px-4 text-right">{formatNum(totals.delta)}</td>
            <td className="py-1 px-4 text-right">{formatNum(totals.gamma)}</td>
            <td className="py-1 px-4 text-right">{formatNum(totals.vega)}</td>
            <td className="py-1 px-4 text-right">{formatNum(greeksResult.theta)}</td>
            <td className="py-1 pl-4 text-right">{formatNum(greeksResult.rho)}</td>
          </tr>
        </tbody>
      </table>
    </div>
  )
}
