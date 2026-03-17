import type { PositionRiskDto, VaRCalculationRequestDto, VaRResultDto } from '../types'

export async function fetchVaR(
  bookId: string,
  valuationDate?: string | null,
): Promise<VaRResultDto | null> {
  let url = `/api/v1/risk/var/${encodeURIComponent(bookId)}`
  if (valuationDate) {
    url += `?valuationDate=${encodeURIComponent(valuationDate)}`
  }
  const response = await fetch(url)
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
  bookId: string,
  request: VaRCalculationRequestDto = {},
): Promise<VaRResultDto | null> {
  const body = {
    ...request,
    requestedOutputs: request.requestedOutputs ?? ['VAR', 'EXPECTED_SHORTFALL', 'GREEKS', 'PV'],
  }
  const response = await fetch(
    `/api/v1/risk/var/${encodeURIComponent(bookId)}`,
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
    let message: string
    try {
      const body = await response.json()
      message = body.message || `${response.status} ${response.statusText}`
    } catch {
      message = `${response.status} ${response.statusText}`
    }
    const error = new Error(message) as Error & { status: number }
    error.status = response.status
    throw error
  }
  return response.json()
}

export async function fetchPositionRisk(
  bookId: string,
  valuationDate?: string | null,
): Promise<PositionRiskDto[]> {
  let url = `/api/v1/risk/positions/${encodeURIComponent(bookId)}`
  if (valuationDate) {
    url += `?valuationDate=${encodeURIComponent(valuationDate)}`
  }
  const response = await fetch(url)
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
