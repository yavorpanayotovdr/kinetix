export interface VolPointResponse {
  strike: number
  maturityDays: number
  impliedVol: number
}

export interface VolSurfaceResponse {
  instrumentId: string
  asOfDate: string
  source: string
  points: VolPointResponse[]
}

export interface VolPointDiffResponse {
  strike: number
  maturityDays: number
  baseVol: number
  compareVol: number
  diff: number
}

export interface VolSurfaceDiffResponse {
  instrumentId: string
  baseDate: string
  compareDate: string
  diffs: VolPointDiffResponse[]
}

export async function fetchVolSurface(instrumentId: string): Promise<VolSurfaceResponse | null> {
  const response = await fetch(`/api/v1/volatility/${encodeURIComponent(instrumentId)}/surface`)
  if (response.status === 404) return null
  if (!response.ok) {
    throw new Error(`Failed to fetch vol surface: ${response.status} ${response.statusText}`)
  }
  return response.json()
}

export async function fetchVolSurfaceDiff(
  instrumentId: string,
  compareDate: string,
): Promise<VolSurfaceDiffResponse | null> {
  const url = `/api/v1/volatility/${encodeURIComponent(instrumentId)}/surface/diff?compareDate=${encodeURIComponent(compareDate)}`
  const response = await fetch(url)
  if (response.status === 404) return null
  if (!response.ok) {
    throw new Error(`Failed to fetch vol surface diff: ${response.status} ${response.statusText}`)
  }
  return response.json()
}
