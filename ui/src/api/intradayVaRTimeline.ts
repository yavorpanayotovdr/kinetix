import type { IntradayVaRTimelineDto } from '../types'

export async function fetchIntradayVaRTimeline(
  bookId: string,
  from: string,
  to: string,
): Promise<IntradayVaRTimelineDto> {
  const params = new URLSearchParams({ from, to })
  const response = await fetch(
    `/api/v1/risk/var/${encodeURIComponent(bookId)}/intraday?${params}`,
  )
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    const message = body.error || `Failed to fetch intraday VaR timeline: ${response.status}`
    throw new Error(message)
  }
  return response.json()
}
