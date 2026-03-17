import type { PnlAttributionDto } from '../types'

export async function fetchPnlAttribution(
  bookId: string,
  date?: string,
): Promise<PnlAttributionDto | null> {
  const base = `/api/v1/risk/pnl-attribution/${encodeURIComponent(bookId)}`
  const url = date ? `${base}?date=${date}` : base

  const response = await fetch(url)
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error(
      `Failed to fetch P&L attribution: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
