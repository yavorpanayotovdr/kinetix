import type { AlertRuleDto, AlertEventDto, CreateAlertRuleRequestDto } from '../types'

export async function fetchRules(): Promise<AlertRuleDto[]> {
  const response = await fetch('/api/v1/notifications/rules')
  if (!response.ok) {
    throw new Error(
      `Failed to fetch rules: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function createRule(
  request: CreateAlertRuleRequestDto,
): Promise<AlertRuleDto> {
  const response = await fetch('/api/v1/notifications/rules', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw new Error(
      `Failed to create rule: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}

export async function deleteRule(ruleId: string): Promise<void> {
  const response = await fetch(
    `/api/v1/notifications/rules/${encodeURIComponent(ruleId)}`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    throw new Error(
      `Failed to delete rule: ${response.status} ${response.statusText}`,
    )
  }
}

export async function fetchAlerts(
  limit?: number,
): Promise<AlertEventDto[]> {
  const params = limit ? `?limit=${limit}` : ''
  const response = await fetch(`/api/v1/notifications/alerts${params}`)
  if (!response.ok) {
    throw new Error(
      `Failed to fetch alerts: ${response.status} ${response.statusText}`,
    )
  }
  return response.json()
}
