import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { AlertRuleDto, AlertEventDto } from '../types'
import { NotificationCenter } from './NotificationCenter'

const sampleRules: AlertRuleDto[] = [
  {
    id: 'rule-1',
    name: 'VaR Limit',
    type: 'VAR_BREACH',
    threshold: 100000,
    operator: 'GREATER_THAN',
    severity: 'CRITICAL',
    channels: ['IN_APP', 'EMAIL'],
    enabled: true,
  },
  {
    id: 'rule-2',
    name: 'ES Warning',
    type: 'PNL_THRESHOLD',
    threshold: 200000,
    operator: 'GREATER_THAN',
    severity: 'WARNING',
    channels: ['IN_APP'],
    enabled: false,
  },
]

const sampleAlerts: AlertEventDto[] = [
  {
    id: 'evt-1',
    ruleId: 'rule-1',
    ruleName: 'VaR Limit',
    type: 'VAR_BREACH',
    severity: 'CRITICAL',
    message: 'VaR exceeded threshold',
    currentValue: 150000,
    threshold: 100000,
    portfolioId: 'port-1',
    triggeredAt: '2025-01-15T10:00:00Z',
  },
  {
    id: 'evt-2',
    ruleId: 'rule-2',
    ruleName: 'ES Warning',
    type: 'PNL_THRESHOLD',
    severity: 'WARNING',
    message: 'Expected shortfall exceeded',
    currentValue: 250000,
    threshold: 200000,
    portfolioId: 'port-1',
    triggeredAt: '2025-01-15T10:05:00Z',
  },
]

describe('NotificationCenter', () => {
  it('renders alert rules table', () => {
    render(
      <NotificationCenter
        rules={sampleRules}
        alerts={[]}
        loading={false}
        error={null}
        onCreateRule={() => {}}
        onDeleteRule={() => {}}
      />,
    )

    const table = screen.getByTestId('rules-table')
    expect(table).toBeInTheDocument()
    const rows = table.querySelectorAll('tbody tr')
    expect(rows.length).toBe(2)
  })

  it('renders create rule form', () => {
    render(
      <NotificationCenter
        rules={[]}
        alerts={[]}
        loading={false}
        error={null}
        onCreateRule={() => {}}
        onDeleteRule={() => {}}
      />,
    )

    expect(screen.getByTestId('create-rule-form')).toBeInTheDocument()
    expect(screen.getByTestId('rule-name-input')).toBeInTheDocument()
    expect(screen.getByTestId('rule-type-select')).toBeInTheDocument()
    expect(screen.getByTestId('rule-threshold-input')).toBeInTheDocument()
    expect(screen.getByTestId('create-rule-btn')).toBeInTheDocument()
  })

  it('renders recent alerts list', () => {
    render(
      <NotificationCenter
        rules={[]}
        alerts={sampleAlerts}
        loading={false}
        error={null}
        onCreateRule={() => {}}
        onDeleteRule={() => {}}
      />,
    )

    const alertsList = screen.getByTestId('alerts-list')
    expect(alertsList).toBeInTheDocument()
    expect(alertsList.children.length).toBe(2)
  })

  it('shows severity badges', () => {
    render(
      <NotificationCenter
        rules={[]}
        alerts={sampleAlerts}
        loading={false}
        error={null}
        onCreateRule={() => {}}
        onDeleteRule={() => {}}
      />,
    )

    const criticalBadge = screen.getByTestId('severity-badge-evt-1')
    expect(criticalBadge).toHaveTextContent('CRITICAL')
    expect(criticalBadge.className).toContain('bg-red')

    const warningBadge = screen.getByTestId('severity-badge-evt-2')
    expect(warningBadge).toHaveTextContent('WARNING')
    expect(warningBadge.className).toContain('bg-yellow')
  })

  it('shows loading state', () => {
    render(
      <NotificationCenter
        rules={[]}
        alerts={[]}
        loading={true}
        error={null}
        onCreateRule={() => {}}
        onDeleteRule={() => {}}
      />,
    )

    expect(screen.getByTestId('notification-loading')).toBeInTheDocument()
  })

  it('shows error state', () => {
    render(
      <NotificationCenter
        rules={[]}
        alerts={[]}
        loading={false}
        error="Failed to load"
        onCreateRule={() => {}}
        onDeleteRule={() => {}}
      />,
    )

    expect(screen.getByTestId('notification-error')).toBeInTheDocument()
    expect(screen.getByTestId('notification-error')).toHaveTextContent('Failed to load')
  })
})
