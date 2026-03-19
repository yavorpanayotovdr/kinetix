import type { DivisionDto, DeskDto, BookAggregationDto } from '../types'

export async function fetchDivisions(): Promise<DivisionDto[]> {
  const response = await fetch('/api/v1/divisions')
  if (!response.ok) {
    throw new Error(
      `Failed to fetch divisions: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function fetchDesks(divisionId?: string): Promise<DeskDto[]> {
  const url = divisionId
    ? `/api/v1/divisions/${encodeURIComponent(divisionId)}/desks`
    : '/api/v1/desks'
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(
      `Failed to fetch desks: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function fetchDeskSummary(
  deskId: string,
  baseCurrency?: string,
): Promise<BookAggregationDto> {
  const url = baseCurrency
    ? `/api/v1/desks/${encodeURIComponent(deskId)}/summary?baseCurrency=${encodeURIComponent(baseCurrency)}`
    : `/api/v1/desks/${encodeURIComponent(deskId)}/summary`
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(
      `Failed to fetch desk summary: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function fetchDivisionSummary(
  divisionId: string,
  baseCurrency?: string,
): Promise<BookAggregationDto> {
  const url = baseCurrency
    ? `/api/v1/divisions/${encodeURIComponent(divisionId)}/summary?baseCurrency=${encodeURIComponent(baseCurrency)}`
    : `/api/v1/divisions/${encodeURIComponent(divisionId)}/summary`
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(
      `Failed to fetch division summary: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function fetchFirmSummary(
  baseCurrency?: string,
): Promise<BookAggregationDto> {
  const url = baseCurrency
    ? `/api/v1/firm/summary?baseCurrency=${encodeURIComponent(baseCurrency)}`
    : '/api/v1/firm/summary'
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(
      `Failed to fetch firm summary: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
