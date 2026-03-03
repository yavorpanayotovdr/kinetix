import type { WhatIfRequestDto, WhatIfResponseDto } from '../types'

export async function runWhatIfAnalysis(
  portfolioId: string,
  request: WhatIfRequestDto,
): Promise<WhatIfResponseDto> {
  const response = await fetch(
    `/api/v1/risk/what-if/${encodeURIComponent(portfolioId)}`,
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
