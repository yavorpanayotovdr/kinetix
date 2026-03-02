import type { ValuationJobSummaryDto, ValuationJobDetailDto } from '../types'

export async function fetchValuationJobs(
  portfolioId: string,
  limit: number = 20,
  offset: number = 0,
  from?: string,
  to?: string,
  status?: string,
): Promise<{ items: ValuationJobSummaryDto[]; totalCount: number }> {
  const params = new URLSearchParams({
    limit: limit.toString(),
    offset: offset.toString(),
  })
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  if (status) params.set('status', status)
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

const CHART_LIMIT = 10_000

export async function fetchValuationJobsForChart(
  portfolioId: string,
  from?: string,
  to?: string,
): Promise<ValuationJobSummaryDto[]> {
  const params = new URLSearchParams({
    limit: CHART_LIMIT.toString(),
    offset: '0',
  })
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const response = await fetch(
    `/api/v1/risk/jobs/${encodeURIComponent(portfolioId)}?${params}`,
  )
  if (!response.ok) {
    throw new Error(
      `Failed to fetch chart data: ${response.status} ${response.statusText}`,
    )
  }
  const data: { items: ValuationJobSummaryDto[] } = await response.json()
  return data.items
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
