import type { StressTestResultDto } from '../types'
import { authFetch } from '../auth/authFetch'

export async function fetchScenarios(): Promise<string[]> {
  const response = await authFetch('/api/v1/risk/stress/scenarios')
  if (!response.ok) {
    throw new Error(
      `Failed to fetch scenarios: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function runStressTest(
  bookId: string,
  scenarioName: string,
  request: Record<string, unknown> = {},
): Promise<StressTestResultDto | null> {
  const response = await authFetch(
    `/api/v1/risk/stress/${encodeURIComponent(bookId)}`,
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

export interface BatchStressRunResult {
  results: StressTestResultDto[]
  failedScenarios: { scenarioName: string; error: string }[]
  worstScenarioName: string | null
  worstPnlImpact: string | null
}

export async function runAllStressTests(
  bookId: string,
  scenarioNames: string[],
  params: RunAllParams = {},
): Promise<StressTestResultDto[]> {
  const response = await authFetch(
    `/api/v1/risk/stress/${encodeURIComponent(bookId)}/batch`,
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
  const body: BatchStressRunResult = await response.json()
  return body.results
}
