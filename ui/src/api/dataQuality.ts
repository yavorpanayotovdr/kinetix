import type { DataQualityStatus } from '../types'

export async function fetchDataQualityStatus(): Promise<DataQualityStatus> {
  const response = await fetch('/api/v1/data-quality/status')
  if (!response.ok) {
    throw new Error(
      `Failed to fetch data quality status: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
