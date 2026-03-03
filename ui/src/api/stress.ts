import type { StressTestResultDto } from '../types'

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

export interface RunAllParams {
  confidenceLevel?: string
  timeHorizonDays?: string
}

export async function runAllStressTests(
  portfolioId: string,
  scenarioNames: string[],
  params: RunAllParams = {},
): Promise<StressTestResultDto[]> {
  const response = await fetch(
    `/api/v1/risk/stress/${encodeURIComponent(portfolioId)}/batch`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        scenarioNames,
        ...params,
      }),
    },
  )
  if (!response.ok) {
    throw new Error(
      `Failed to run batch stress tests: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
