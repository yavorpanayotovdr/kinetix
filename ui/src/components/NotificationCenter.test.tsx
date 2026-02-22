import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
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

  it('shows relative timestamps instead of ISO strings', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-01-15T12:00:00Z'))

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

    expect(screen.getByText(/2h ago/)).toBeInTheDocument()
    expect(screen.queryByText('2025-01-15T10:00:00Z')).not.toBeInTheDocument()

    vi.useRealTimers()
  })

  it('applies severity border colors to alert cards', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-01-15T12:00:00Z'))

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
    const cards = alertsList.children
    expect(cards[0].className).toContain('border-yellow-500')
    expect(cards[1].className).toContain('border-red-500')

    vi.useRealTimers()
  })

  it('sorts alerts by recency then severity', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-01-15T12:00:00Z'))

    const alerts: AlertEventDto[] = [
      {
        ...sampleAlerts[0],
        id: 'evt-old',
        triggeredAt: '2025-01-15T09:00:00Z',
        severity: 'CRITICAL',
      },
      {
        ...sampleAlerts[1],
        id: 'evt-new',
        triggeredAt: '2025-01-15T11:00:00Z',
        severity: 'WARNING',
      },
    ]

    render(
      <NotificationCenter
        rules={[]}
        alerts={alerts}
        loading={false}
        error={null}
        onCreateRule={() => {}}
        onDeleteRule={() => {}}
      />,
    )

    const alertsList = screen.getByTestId('alerts-list')
    const badges = alertsList.querySelectorAll('[data-testid^="severity-badge"]')
    // Most recent first (WARNING at 11:00), then older (CRITICAL at 09:00)
    expect(badges[0]).toHaveTextContent('WARNING')
    expect(badges[1]).toHaveTextContent('CRITICAL')

    vi.useRealTimers()
  })
})
