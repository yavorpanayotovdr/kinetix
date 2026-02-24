import type { CalculationRunSummaryDto, CalculationRunDetailDto } from '../types'

export async function fetchCalculationRuns(
  portfolioId: string,
  limit: number = 20,
  offset: number = 0,
): Promise<CalculationRunSummaryDto[]> {
  const params = new URLSearchParams({
    limit: limit.toString(),
    offset: offset.toString(),
  })
  const response = await fetch(
    `/api/v1/risk/runs/${encodeURIComponent(portfolioId)}?${params}`,
  )
  if (!response.ok) {
    throw new Error(
      `Failed to fetch calculation runs: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function fetchCalculationRunDetail(
  runId: string,
): Promise<CalculationRunDetailDto | null> {
  const response = await fetch(
    `/api/v1/risk/runs/detail/${encodeURIComponent(runId)}`,
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to fetch run detail: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
