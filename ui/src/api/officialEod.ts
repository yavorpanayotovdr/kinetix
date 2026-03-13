export interface EodPromotionResponse {
  jobId: string
  portfolioId: string
  valuationDate: string
  runLabel: string
  promotedAt: string | null
  promotedBy: string | null
}

export async function promoteToOfficialEod(
  jobId: string,
  promotedBy: string,
): Promise<EodPromotionResponse> {
  const response = await fetch(
    `/api/v1/risk/jobs/${encodeURIComponent(jobId)}/label`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ label: 'OFFICIAL_EOD', promotedBy }),
    },
  )
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    const message = body.error || `Promotion failed: ${response.status}`
    const err = new Error(message) as Error & { status: number }
    err.status = response.status
    throw err
  }
  return response.json()
}

export async function demoteOfficialEod(
  jobId: string,
  demotedBy: string,
): Promise<EodPromotionResponse> {
  const response = await fetch(
    `/api/v1/risk/jobs/${encodeURIComponent(jobId)}/label`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ label: 'ADHOC', promotedBy: demotedBy }),
    },
  )
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    const message = body.error || `Demotion failed: ${response.status}`
    const err = new Error(message) as Error & { status: number }
    err.status = response.status
    throw err
  }
  return response.json()
}

export async function fetchOfficialEod(
  portfolioId: string,
  date: string,
): Promise<EodPromotionResponse | null> {
  const response = await fetch(
    `/api/v1/risk/jobs/${encodeURIComponent(portfolioId)}/official-eod?date=${encodeURIComponent(date)}`,
  )
  if (response.status === 404) return null
  if (!response.ok) {
    throw new Error(
      `Failed to fetch Official EOD: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
