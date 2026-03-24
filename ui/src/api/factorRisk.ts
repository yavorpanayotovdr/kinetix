import type { FactorRiskDto } from '../types'

export async function fetchLatestFactorRisk(
  bookId: string,
): Promise<FactorRiskDto | null> {
  const response = await fetch(
    `/api/v1/books/${encodeURIComponent(bookId)}/factor-risk/latest`,
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to fetch factor risk: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function fetchFactorRiskHistory(
  bookId: string,
  limit: number = 30,
): Promise<FactorRiskDto[]> {
  const response = await fetch(
    `/api/v1/books/${encodeURIComponent(bookId)}/factor-risk?limit=${limit}`,
  )
  if (!response.ok) {
    throw new Error(
      `Failed to fetch factor risk history: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
