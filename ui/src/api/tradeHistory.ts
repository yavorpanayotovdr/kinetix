import type { TradeHistoryDto } from '../types'

export async function fetchTradeHistory(
  portfolioId: string,
): Promise<TradeHistoryDto[]> {
  const response = await fetch(
    `/api/v1/portfolios/${encodeURIComponent(portfolioId)}/trades`,
  )
  if (!response.ok) {
    throw new Error(
      `Failed to fetch trade history: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
