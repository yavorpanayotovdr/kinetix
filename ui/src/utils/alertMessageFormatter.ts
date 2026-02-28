import type { AlertEventDto } from '../types'

function formatCurrency(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(Math.round(value))
}

function severityPrefix(severity: string): string {
  if (severity === 'CRITICAL' || severity === 'WARNING') return `${severity}: `
  return ''
}

export function formatAlertMessage(alert: AlertEventDto): string {
  const prefix = severityPrefix(alert.severity)

  if (!Number.isFinite(alert.currentValue) || !Number.isFinite(alert.threshold)) {
    return `${prefix}${alert.message}`
  }

  const current = formatCurrency(alert.currentValue)
  const threshold = formatCurrency(alert.threshold)
  const portfolio = alert.portfolioId

  switch (alert.type) {
    case 'PNL_THRESHOLD':
      return `${prefix}Daily P&L exceeded ${threshold} limit — current: ${current} (${portfolio})`
    case 'VAR_BREACH':
      return `${prefix}VaR breached ${threshold} limit — current: ${current} (${portfolio})`
    case 'CONCENTRATION': {
      const pct = alert.currentValue.toFixed(2)
      return `${prefix}Concentration exceeded ${alert.threshold}% limit — current: ${pct}% (${portfolio})`
    }
    default:
      return `${prefix}${alert.message}`
  }
}
