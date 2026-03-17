import type { EodTimelineResponseDto } from '../types'

export async function fetchEodTimeline(
  bookId: string,
  from: string,
  to: string,
): Promise<EodTimelineResponseDto> {
  const params = new URLSearchParams({ from, to })
  const response = await fetch(
    `/api/v1/risk/eod-timeline/${encodeURIComponent(bookId)}?${params}`,
  )
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    const message = body.error || `Failed to fetch EOD timeline: ${response.status}`
    throw new Error(message)
  }
  return response.json()
}
