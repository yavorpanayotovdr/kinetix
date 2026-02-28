import type { PositionRiskDto, VaRCalculationRequestDto, VaRResultDto } from '../types'

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
  const body = {
    ...request,
    requestedOutputs: request.requestedOutputs ?? ['VAR', 'EXPECTED_SHORTFALL', 'GREEKS'],
  }
  const response = await fetch(
    `/api/v1/risk/var/${encodeURIComponent(portfolioId)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
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

export async function fetchPositionRisk(
  portfolioId: string,
): Promise<PositionRiskDto[]> {
  const response = await fetch(
    `/api/v1/risk/positions/${encodeURIComponent(portfolioId)}`,
  )
  if (response.status === 404) {
    return []
  }
  if (!response.ok) {
    throw new Error(
      `Failed to fetch position risk: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
