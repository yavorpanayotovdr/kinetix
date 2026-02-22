import { useCallback, useEffect, useState } from 'react'
import {
  fetchRules,
  createRule as apiCreateRule,
  deleteRule as apiDeleteRule,
  fetchAlerts,
} from '../api/notifications'
import type { AlertRuleDto, AlertEventDto, CreateAlertRuleRequestDto } from '../types'

export interface UseNotificationsResult {
  rules: AlertRuleDto[]
  alerts: AlertEventDto[]
  loading: boolean
  error: string | null
  createRule: (request: CreateAlertRuleRequestDto) => void
  deleteRule: (ruleId: string) => void
}

export function useNotifications(): UseNotificationsResult {
  const [rules, setRules] = useState<AlertRuleDto[]>([])
  const [alerts, setAlerts] = useState<AlertEventDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    try {
      const [rulesData, alertsData] = await Promise.all([
        fetchRules(),
        fetchAlerts(),
      ])
      setRules(rulesData)
      setAlerts(alertsData)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const createRule = useCallback(async (request: CreateAlertRuleRequestDto) => {
    try {
      await apiCreateRule(request)
      const updatedRules = await fetchRules()
      setRules(updatedRules)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }, [])

  const deleteRule = useCallback(async (ruleId: string) => {
    try {
      await apiDeleteRule(ruleId)
      const updatedRules = await fetchRules()
      setRules(updatedRules)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }, [])

  return { rules, alerts, loading, error, createRule, deleteRule }
}
