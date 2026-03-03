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

export type ValidationErrors = Record<number, Record<string, string>>

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

function validate(trades: TradeFormEntry[]): ValidationErrors {
  const errors: ValidationErrors = {}
  trades.forEach((trade, index) => {
    const fieldErrors: Record<string, string> = {}
    if (!trade.instrumentId.trim()) {
      fieldErrors.instrumentId = 'Instrument is required'
    }
    const qty = Number(trade.quantity)
    if (!trade.quantity.trim() || isNaN(qty) || qty <= 0) {
      fieldErrors.quantity = 'Quantity must be a positive number'
    }
    const price = Number(trade.priceAmount)
    if (!trade.priceAmount.trim() || isNaN(price) || price < 0) {
      fieldErrors.priceAmount = 'Price must be a non-negative number'
    }
    if (Object.keys(fieldErrors).length > 0) {
      errors[index] = fieldErrors
    }
  })
  return errors
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
  validationErrors: ValidationErrors
}

export function useWhatIf(portfolioId: string | null): UseWhatIfResult {
  const [trades, setTrades] = useState<TradeFormEntry[]>([emptyTrade()])
  const [result, setResult] = useState<WhatIfResponseDto | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [validationErrors, setValidationErrors] = useState<ValidationErrors>({})

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

    const errors = validate(trades)
    setValidationErrors(errors)
    if (Object.keys(errors).length > 0) return

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
    setValidationErrors({})
  }, [])

  const impact = useMemo<WhatIfImpactDto | null>(() => {
    if (!result) return null

    const baseDelta = sumGreek(result, 'base', 'delta')
    const hypotheticalDelta = sumGreek(result, 'hypothetical', 'delta')
    const baseGamma = sumGreek(result, 'base', 'gamma')
    const hypotheticalGamma = sumGreek(result, 'hypothetical', 'gamma')
    const baseVega = sumGreek(result, 'base', 'vega')
    const hypotheticalVega = sumGreek(result, 'hypothetical', 'vega')

    const baseTheta = result.baseGreeks ? Number(result.baseGreeks.theta) : 0
    const hypotheticalTheta = result.hypotheticalGreeks ? Number(result.hypotheticalGreeks.theta) : 0
    const baseRho = result.baseGreeks ? Number(result.baseGreeks.rho) : 0
    const hypotheticalRho = result.hypotheticalGreeks ? Number(result.hypotheticalGreeks.rho) : 0

    return {
      varChange: result.varChange,
      esChange: result.esChange,
      deltaChange: hypotheticalDelta - baseDelta,
      gammaChange: hypotheticalGamma - baseGamma,
      vegaChange: hypotheticalVega - baseVega,
      thetaChange: hypotheticalTheta - baseTheta,
      rhoChange: hypotheticalRho - baseRho,
    }
  }, [result])

  return { trades, addTrade, removeTrade, updateTrade, submit, reset, result, impact, loading, error, validationErrors }
}
