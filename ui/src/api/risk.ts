import type { VaRCalculationRequestDto, VaRResultDto } from '../types'

export async function fetchVaR(
  portfolioId: string,
): Promise<VaRResultDto | null> {
  const response = await fetch(
    `/api/v1/risk/var/${encodeURIComponent(portfolioId)}`,
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to fetch VaR: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function triggerVaRCalculation(
  portfolioId: string,
  request: VaRCalculationRequestDto = {},
): Promise<VaRResultDto | null> {
  const response = await fetch(
    `/api/v1/risk/var/${encodeURIComponent(portfolioId)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    },
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to trigger VaR calculation: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
