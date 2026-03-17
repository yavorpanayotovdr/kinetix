import type { PnlAttributionDto, SodBaselineStatusDto } from '../types'

export async function fetchSodBaselineStatus(
  bookId: string,
): Promise<SodBaselineStatusDto> {
  const response = await fetch(
    `/api/v1/risk/sod-snapshot/${encodeURIComponent(bookId)}/status`,
  )
  if (response.status === 404) {
    return { exists: false, baselineDate: null, snapshotType: null, createdAt: null, sourceJobId: null, calculationType: null }
  }
  if (!response.ok) {
    throw new Error(
      `Failed to fetch SOD baseline status: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function createSodSnapshot(
  bookId: string,
  jobId?: string,
): Promise<SodBaselineStatusDto> {
  const url = jobId
    ? `/api/v1/risk/sod-snapshot/${encodeURIComponent(bookId)}?jobId=${encodeURIComponent(jobId)}`
    : `/api/v1/risk/sod-snapshot/${encodeURIComponent(bookId)}`
  const response = await fetch(url, { method: 'POST' })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const message = body?.message ?? `Failed to create SOD snapshot: ${response.status} ${response.statusText}`
    throw new Error(message)
  }
  return response.json()
}

export async function resetSodBaseline(
  bookId: string,
): Promise<void> {
  const response = await fetch(
    `/api/v1/risk/sod-snapshot/${encodeURIComponent(bookId)}`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    throw new Error(
      `Failed to reset SOD baseline: ${response.status} ${response.statusText}`,
    )
  }
}

export async function computePnlAttribution(
  bookId: string,
): Promise<PnlAttributionDto> {
  const response = await fetch(
    `/api/v1/risk/pnl-attribution/${encodeURIComponent(bookId)}/compute`,
    { method: 'POST' },
  )
  if (response.status === 412) {
    throw new Error('No SOD baseline exists. Set a baseline first.')
  }
  if (!response.ok) {
    throw new Error(
      `Failed to compute P&L attribution: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
