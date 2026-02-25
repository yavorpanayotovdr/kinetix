import type { ValuationJobSummaryDto, ValuationJobDetailDto } from '../types'

export async function fetchValuationJobs(
  portfolioId: string,
  limit: number = 20,
  offset: number = 0,
  from?: string,
  to?: string,
): Promise<{ items: ValuationJobSummaryDto[]; totalCount: number }> {
  const params = new URLSearchParams({
    limit: limit.toString(),
    offset: offset.toString(),
  })
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const response = await fetch(
    `/api/v1/risk/jobs/${encodeURIComponent(portfolioId)}?${params}`,
  )
  if (!response.ok) {
    throw new Error(
      `Failed to fetch valuation jobs: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function fetchValuationJobDetail(
  jobId: string,
): Promise<ValuationJobDetailDto | null> {
  const response = await fetch(
    `/api/v1/risk/jobs/detail/${encodeURIComponent(jobId)}`,
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to fetch job detail: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
