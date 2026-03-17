import type { RunComparisonResponseDto, ModelComparisonRequestDto, VaRAttributionDto, BacktestComparisonDto, MarketDataQuantDiffDto } from '../types'

export async function compareDayOverDay(
  bookId: string,
  targetDate?: string,
  baseDate?: string,
): Promise<RunComparisonResponseDto | null> {
  let url = `/api/v1/risk/compare/${encodeURIComponent(bookId)}/day-over-day`
  const params = new URLSearchParams()
  if (targetDate) params.set('targetDate', targetDate)
  if (baseDate) params.set('baseDate', baseDate)
  const qs = params.toString()
  if (qs) url += `?${qs}`
  const response = await fetch(url)
  if (response.status === 404) return null
  if (!response.ok) throw new Error(`Failed to compare day-over-day: ${response.status}`)
  return response.json()
}

export async function compareByJobIds(
  bookId: string,
  baseJobId: string,
  targetJobId: string,
): Promise<RunComparisonResponseDto> {
  const response = await fetch(`/api/v1/risk/compare/${encodeURIComponent(bookId)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ baseJobId, targetJobId }),
  })
  if (!response.ok) throw new Error(`Failed to compare jobs: ${response.status}`)
  return response.json()
}

export async function requestAttribution(
  bookId: string,
  targetDate?: string,
  baseDate?: string,
): Promise<VaRAttributionDto> {
  let url = `/api/v1/risk/compare/${encodeURIComponent(bookId)}/day-over-day/attribution`
  const params = new URLSearchParams()
  if (targetDate) params.set('targetDate', targetDate)
  if (baseDate) params.set('baseDate', baseDate)
  const qs = params.toString()
  if (qs) url += `?${qs}`
  const response = await fetch(url, { method: 'POST' })
  if (!response.ok) throw new Error(`Failed to request attribution: ${response.status}`)
  return response.json()
}

export async function compareModelVersions(
  bookId: string,
  request: ModelComparisonRequestDto,
): Promise<RunComparisonResponseDto> {
  const response = await fetch(`/api/v1/risk/compare/${encodeURIComponent(bookId)}/model`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) throw new Error(`Failed to compare model versions: ${response.status}`)
  return response.json()
}

export async function compareBacktests(
  baseId: string,
  targetId: string,
): Promise<BacktestComparisonDto | null> {
  const url = `/api/v1/regulatory/backtest/compare?baseId=${encodeURIComponent(baseId)}&targetId=${encodeURIComponent(targetId)}`
  const response = await fetch(url)
  if (response.status === 404) return null
  if (!response.ok) throw new Error(`Failed to compare backtests: ${response.status}`)
  return response.json()
}

export async function fetchMarketDataQuantDiff(
  bookId: string,
  dataType: string,
  instrumentId: string,
  baseManifestId: string,
  targetManifestId: string,
): Promise<MarketDataQuantDiffDto | null> {
  const params = new URLSearchParams({
    dataType,
    instrumentId,
    baseManifestId,
    targetManifestId,
  })
  const url = `/api/v1/risk/compare/${encodeURIComponent(bookId)}/market-data-quant?${params}`
  const response = await fetch(url)
  if (response.status === 404) return null
  if (!response.ok) throw new Error(`Failed to fetch quant diff: ${response.status}`)
  return response.json()
}
