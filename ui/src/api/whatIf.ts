import type { WhatIfRequestDto, WhatIfResponseDto } from '../types'

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
    throw new Error(
      `Failed to run what-if analysis: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
