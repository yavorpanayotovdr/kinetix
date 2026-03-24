export interface ExposureAtTenorDto {
  tenor: string
  tenorYears: number
  expectedExposure: number
  pfe95: number
  pfe99: number
}

export interface CounterpartyExposureDto {
  counterpartyId: string
  calculatedAt: string
  currentNetExposure: number
  peakPfe: number
  cva: number | null
  cvaEstimated: boolean
  currency: string
  pfeProfile: ExposureAtTenorDto[]
}

export async function fetchAllCounterpartyExposures(): Promise<CounterpartyExposureDto[]> {
  const response = await fetch('/api/v1/counterparty-risk')
  if (!response.ok) {
    throw new Error(`Failed to fetch counterparty exposures: ${response.status} ${response.statusText}`)
  }
  return response.json()
}

export async function fetchCounterpartyExposure(
  counterpartyId: string,
): Promise<CounterpartyExposureDto | null> {
  const response = await fetch(`/api/v1/counterparty-risk/${encodeURIComponent(counterpartyId)}`)
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(`Failed to fetch counterparty exposure: ${response.status} ${response.statusText}`)
  }
  return response.json()
}

export async function fetchCounterpartyExposureHistory(
  counterpartyId: string,
  limit = 90,
): Promise<CounterpartyExposureDto[]> {
  const response = await fetch(
    `/api/v1/counterparty-risk/${encodeURIComponent(counterpartyId)}/history?limit=${limit}`,
  )
  if (!response.ok) {
    throw new Error(`Failed to fetch counterparty history: ${response.status} ${response.statusText}`)
  }
  return response.json()
}

export async function triggerPFEComputation(
  counterpartyId: string,
): Promise<CounterpartyExposureDto> {
  const response = await fetch(
    `/api/v1/counterparty-risk/${encodeURIComponent(counterpartyId)}/pfe`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ positions: [] }),
    },
  )
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

export async function triggerCVAComputation(
  counterpartyId: string,
): Promise<CounterpartyExposureDto | null> {
  const response = await fetch(
    `/api/v1/counterparty-risk/${encodeURIComponent(counterpartyId)}/cva`,
    { method: 'POST' },
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(`Failed to compute CVA: ${response.status} ${response.statusText}`)
  }
  return response.json()
}
