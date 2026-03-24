import type { LiquidityRiskResultDto } from '../types'

export async function fetchLatestLiquidityRisk(
  bookId: string,
): Promise<LiquidityRiskResultDto | null> {
  const response = await fetch(
    `/api/v1/books/${encodeURIComponent(bookId)}/liquidity-risk/latest`,
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to fetch liquidity risk: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function triggerLiquidityRiskCalculation(
  bookId: string,
  baseVar: number,
): Promise<LiquidityRiskResultDto | null> {
  const response = await fetch(
    `/api/v1/books/${encodeURIComponent(bookId)}/liquidity-risk`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ baseVar }),
    },
  )
  if (response.status === 204) {
    return null
  }
  if (!response.ok) {
    let message: string
    try {
      const body = await response.json()
      message = body.message || `${response.status} ${response.statusText}`
    } catch {
      message = `${response.status} ${response.statusText}`
    }
    throw new Error(message)
  }
  return response.json()
}
