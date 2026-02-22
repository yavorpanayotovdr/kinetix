export interface ServiceHealth {
  status: 'UP' | 'DOWN'
}

export interface SystemHealthResponse {
  status: 'UP' | 'DEGRADED'
  services: Record<string, ServiceHealth>
}

export async function fetchSystemHealth(): Promise<SystemHealthResponse> {
  const response = await fetch('/api/v1/system/health')
  if (!response.ok) {
    throw new Error(
      `Failed to fetch system health: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
