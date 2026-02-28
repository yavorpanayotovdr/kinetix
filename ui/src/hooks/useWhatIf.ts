import { useCallback, useMemo, useState } from 'react'
import { runWhatIfAnalysis } from '../api/whatIf'
import type { HypotheticalTradeDto, WhatIfImpactDto, WhatIfResponseDto } from '../types'

export interface TradeFormEntry {
  instrumentId: string
  assetClass: string
  side: string
  quantity: string
  priceAmount: string
  priceCurrency: string
}

function emptyTrade(): TradeFormEntry {
  return {
    instrumentId: '',
    assetClass: 'EQUITY',
    side: 'BUY',
    quantity: '',
    priceAmount: '',
    priceCurrency: 'USD',
  }
}

function sumGreek(
  result: WhatIfResponseDto | null,
  snapshot: 'base' | 'hypothetical',
  field: 'delta' | 'gamma' | 'vega',
): number {
  if (!result) return 0
  const greeks = snapshot === 'base' ? result.baseGreeks : result.hypotheticalGreeks
  if (!greeks) return 0
  return greeks.assetClassGreeks.reduce((sum, g) => sum + Number(g[field]), 0)
}

export interface UseWhatIfResult {
  trades: TradeFormEntry[]
  addTrade: () => void
  removeTrade: (index: number) => void
  updateTrade: (index: number, field: keyof TradeFormEntry, value: string) => void
  submit: () => Promise<void>
  reset: () => void
  result: WhatIfResponseDto | null
  impact: WhatIfImpactDto | null
  loading: boolean
  error: string | null
}

export function useWhatIf(portfolioId: string | null): UseWhatIfResult {
  const [trades, setTrades] = useState<TradeFormEntry[]>([emptyTrade()])
  const [result, setResult] = useState<WhatIfResponseDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const addTrade = useCallback(() => {
    setTrades((prev) => [...prev, emptyTrade()])
  }, [])

  const removeTrade = useCallback((index: number) => {
    setTrades((prev) => {
      if (prev.length <= 1) return prev
      return prev.filter((_, i) => i !== index)
    })
  }, [])

  const updateTrade = useCallback(
    (index: number, field: keyof TradeFormEntry, value: string) => {
      setTrades((prev) =>
        prev.map((trade, i) => (i === index ? { ...trade, [field]: value } : trade)),
      )
    },
    [],
  )

  const submit = useCallback(async () => {
    if (!portfolioId) return

    setLoading(true)
    setError(null)

    try {
      const hypotheticalTrades: HypotheticalTradeDto[] = trades.map((t) => ({
        instrumentId: t.instrumentId,
        assetClass: t.assetClass,
        side: t.side,
        quantity: t.quantity,
        priceAmount: t.priceAmount,
        priceCurrency: t.priceCurrency,
      }))

      const data = await runWhatIfAnalysis(portfolioId, { hypotheticalTrades })
      setResult(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setResult(null)
    } finally {
      setLoading(false)
    }
  }, [portfolioId, trades])

  const reset = useCallback(() => {
    setTrades([emptyTrade()])
    setResult(null)
    setError(null)
  }, [])

  const impact = useMemo<WhatIfImpactDto | null>(() => {
    if (!result) return null

    const baseDelta = sumGreek(result, 'base', 'delta')
    const hypotheticalDelta = sumGreek(result, 'hypothetical', 'delta')
    const baseGamma = sumGreek(result, 'base', 'gamma')
    const hypotheticalGamma = sumGreek(result, 'hypothetical', 'gamma')
    const baseVega = sumGreek(result, 'base', 'vega')
    const hypotheticalVega = sumGreek(result, 'hypothetical', 'vega')

    return {
      varChange: result.varChange,
      esChange: result.esChange,
      deltaChange: hypotheticalDelta - baseDelta,
      gammaChange: hypotheticalGamma - baseGamma,
      vegaChange: hypotheticalVega - baseVega,
    }
  }, [result])

  return { trades, addTrade, removeTrade, updateTrade, submit, reset, result, impact, loading, error }
}
