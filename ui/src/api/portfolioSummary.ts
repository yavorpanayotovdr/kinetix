import type { PortfolioAggregationDto } from '../types'

export async function fetchPortfolioSummary(
  portfolioId: string,
  baseCurrency: string,
): Promise<PortfolioAggregationDto> {
  const response = await fetch(
    `/api/v1/portfolios/${encodeURIComponent(portfolioId)}/summary?baseCurrency=${encodeURIComponent(baseCurrency)}`,
  )
  if (!response.ok) {
    throw new Error(
      `Failed to fetch portfolio summary: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
