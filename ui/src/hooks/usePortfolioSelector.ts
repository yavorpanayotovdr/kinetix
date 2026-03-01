import { useCallback, useEffect, useState } from 'react'
import { fetchPortfolios, fetchPositions } from '../api/positions'
import type { PositionDto } from '../types'

export const ALL_PORTFOLIOS = '__ALL__'

export interface PortfolioOption {
  value: string
  label: string
}

export interface UsePortfolioSelectorResult {
  portfolioOptions: PortfolioOption[]
  selectedPortfolioId: string | null
  isAllSelected: boolean
  allPortfolioIds: string[]
  positions: PositionDto[]
  aggregatedPositions: PositionDto[]
  selectPortfolio: (id: string) => void
  loading: boolean
  error: string | null
}

export function usePortfolioSelector(): UsePortfolioSelectorResult {
  const [portfolioIds, setPortfolioIds] = useState<string[]>([])
  const [selectedPortfolioId, setSelectedPortfolioId] = useState<string | null>(null)
  const [positions, setPositions] = useState<PositionDto[]>([])
  const [aggregatedPositions, setAggregatedPositions] = useState<PositionDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const portfolioList = await fetchPortfolios()
        if (cancelled) return

        const ids = portfolioList.map((p) => p.portfolioId).sort()
        setPortfolioIds(ids)

        if (ids.length === 0) {
          setLoading(false)
          return
        }

        const firstPortfolioId = ids[0]
        setSelectedPortfolioId(firstPortfolioId)

        const positionData = await fetchPositions(firstPortfolioId)
        if (cancelled) return

        setPositions(positionData)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err.message : String(err))
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    load()

    return () => {
      cancelled = true
    }
  }, [])

  const isAllSelected = selectedPortfolioId === ALL_PORTFOLIOS

  const portfolioOptions: PortfolioOption[] = [
    ...(portfolioIds.length > 1
      ? [{ value: ALL_PORTFOLIOS, label: 'All Portfolios' }]
      : []),
    ...portfolioIds.map((id) => ({ value: id, label: id })),
  ]

  const selectPortfolio = useCallback(async (id: string) => {
    setSelectedPortfolioId(id)
    setLoading(true)
    setError(null)

    try {
      if (id === ALL_PORTFOLIOS) {
        const allPositions: PositionDto[] = []
        for (const portfolioId of portfolioIds) {
          const posData = await fetchPositions(portfolioId)
          allPositions.push(...posData)
        }
        setPositions(allPositions)
        setAggregatedPositions(aggregatePositions(allPositions))
      } else {
        const posData = await fetchPositions(id)
        setPositions(posData)
        setAggregatedPositions([])
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [portfolioIds])

  return {
    portfolioOptions,
    selectedPortfolioId,
    isAllSelected,
    allPortfolioIds: portfolioIds,
    positions,
    aggregatedPositions,
    selectPortfolio,
    loading,
    error,
  }
}

function aggregatePositions(positions: PositionDto[]): PositionDto[] {
  const byInstrument = new Map<string, PositionDto[]>()

  for (const pos of positions) {
    const existing = byInstrument.get(pos.instrumentId) ?? []
    existing.push(pos)
    byInstrument.set(pos.instrumentId, existing)
  }

  return Array.from(byInstrument.entries()).map(([instrumentId, group]) => {
    const totalQuantity = group.reduce((sum, p) => sum + Number(p.quantity), 0)
    const totalMarketValue = group.reduce(
      (sum, p) => sum + Number(p.marketValue.amount),
      0,
    )
    const totalUnrealizedPnl = group.reduce(
      (sum, p) => sum + Number(p.unrealizedPnl.amount),
      0,
    )
    const currency = group[0].marketPrice.currency

    return {
      portfolioId: ALL_PORTFOLIOS,
      instrumentId,
      assetClass: group[0].assetClass,
      quantity: String(totalQuantity),
      averageCost: group[0].averageCost,
      marketPrice: group[0].marketPrice,
      marketValue: { amount: totalMarketValue.toFixed(2), currency },
      unrealizedPnl: { amount: totalUnrealizedPnl.toFixed(2), currency },
    }
  })
}
