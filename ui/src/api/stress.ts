import type { StressTestResultDto, GreeksResultDto } from '../types'

export async function fetchScenarios(): Promise<string[]> {
  const response = await fetch('/api/v1/risk/stress/scenarios')
  if (!response.ok) {
    throw new Error(
      `Failed to fetch scenarios: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function runStressTest(
  portfolioId: string,
  scenarioName: string,
  request: Record<string, unknown> = {},
): Promise<StressTestResultDto | null> {
  const response = await fetch(
    `/api/v1/risk/stress/${encodeURIComponent(portfolioId)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ scenarioName, ...request }),
    },
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to run stress test: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function fetchGreeks(
  portfolioId: string,
  request: Record<string, unknown> = {},
): Promise<GreeksResultDto | null> {
  const response = await fetch(
    `/api/v1/risk/greeks/${encodeURIComponent(portfolioId)}`,
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
      `Failed to fetch Greeks: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
