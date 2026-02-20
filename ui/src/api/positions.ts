import type { PortfolioDto, PositionDto } from '../types'

export async function fetchPortfolios(): Promise<PortfolioDto[]> {
  const response = await fetch('/api/v1/portfolios')
  if (!response.ok) {
    throw new Error(
      `Failed to fetch portfolios: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function fetchPositions(
  portfolioId: string,
): Promise<PositionDto[]> {
  const response = await fetch(
    `/api/v1/portfolios/${encodeURIComponent(portfolioId)}/positions`,
  )
  if (!response.ok) {
    throw new Error(
      `Failed to fetch positions: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
