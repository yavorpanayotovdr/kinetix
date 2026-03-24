import type { HedgeRecommendationDto, HedgeSuggestRequestDto } from '../types'

export async function suggestHedge(
  bookId: string,
  request: HedgeSuggestRequestDto,
): Promise<HedgeRecommendationDto> {
  const response = await fetch(
    `/api/v1/risk/hedge-suggest/${encodeURIComponent(bookId)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    },
  )
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(`Failed to suggest hedge: ${response.status} ${text}`)
  }
  return response.json()
}

export async function fetchLatestHedgeRecommendations(
  bookId: string,
  limit = 10,
): Promise<HedgeRecommendationDto[]> {
  const response = await fetch(
    `/api/v1/risk/hedge-suggest/${encodeURIComponent(bookId)}?limit=${limit}`,
  )
  if (!response.ok) {
    throw new Error(`Failed to fetch hedge recommendations: ${response.status}`)
  }
  return response.json()
}

export async function fetchHedgeRecommendation(
  bookId: string,
  id: string,
): Promise<HedgeRecommendationDto | null> {
  const response = await fetch(
    `/api/v1/risk/hedge-suggest/${encodeURIComponent(bookId)}/${encodeURIComponent(id)}`,
  )
  if (response.status === 404) return null
  if (!response.ok) {
    throw new Error(`Failed to fetch hedge recommendation: ${response.status}`)
  }
  return response.json()
}
