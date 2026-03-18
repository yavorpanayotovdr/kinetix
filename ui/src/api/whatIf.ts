import type { WhatIfRequestDto, WhatIfResponseDto } from '../types'

export class FetchError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

export async function runWhatIfAnalysis(
  bookId: string,
  request: WhatIfRequestDto,
): Promise<WhatIfResponseDto> {
  const response = await fetch(
    `/api/v1/risk/what-if/${encodeURIComponent(bookId)}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    },
  )
  if (!response.ok) {
    throw new FetchError(
      `Failed to run what-if analysis: ${response.status} ${response.statusText}`,
      response.status,
    )
  }
  return response.json()
}
