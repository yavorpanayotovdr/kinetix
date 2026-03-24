import type { MarketRegimeDto, MarketRegimeHistoryDto } from '../types'

export async function fetchCurrentRegime(): Promise<MarketRegimeDto> {
  const response = await fetch('/api/v1/risk/regime/current')
  if (!response.ok) {
    throw new Error(`Failed to fetch current regime: ${response.status} ${response.statusText}`)
  }
  return response.json()
}

export async function fetchRegimeHistory(limit = 50): Promise<MarketRegimeHistoryDto> {
  const response = await fetch(`/api/v1/risk/regime/history?limit=${limit}`)
  if (!response.ok) {
    throw new Error(`Failed to fetch regime history: ${response.status} ${response.statusText}`)
  }
  return response.json()
}
