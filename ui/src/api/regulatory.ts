import type { FrtbResultDto, ReportResultDto } from '../types'

export async function fetchFrtb(
  portfolioId: string,
): Promise<FrtbResultDto | null> {
  const response = await fetch(
    `/api/v1/regulatory/frtb/${encodeURIComponent(portfolioId)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    },
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to fetch FRTB: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function generateReport(
  portfolioId: string,
  format: string,
): Promise<ReportResultDto | null> {
  const response = await fetch(
    `/api/v1/regulatory/report/${encodeURIComponent(portfolioId)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ format }),
    },
  )
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to generate report: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
