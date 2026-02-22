import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SystemDashboard } from './SystemDashboard'
import type { SystemHealthResponse } from '../api/system'

const allUpHealth: SystemHealthResponse = {
  status: 'UP',
  services: {
    gateway: { status: 'UP' },
    'position-service': { status: 'UP' },
    'market-data-service': { status: 'UP' },
    'risk-orchestrator': { status: 'UP' },
    'notification-service': { status: 'UP' },
  },
}

const degradedHealth: SystemHealthResponse = {
  status: 'DEGRADED',
  services: {
    gateway: { status: 'UP' },
    'position-service': { status: 'UP' },
    'market-data-service': { status: 'UP' },
    'risk-orchestrator': { status: 'DOWN' },
    'notification-service': { status: 'UP' },
  },
}

describe('SystemDashboard', () => {
  it('shows loading state', () => {
    render(
      <SystemDashboard
        health={null}
        loading={true}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('system-loading')).toBeInTheDocument()
  })

  it('shows error state', () => {
    render(
      <SystemDashboard
        health={null}
        loading={false}
        error="Connection failed"
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('system-error')).toHaveTextContent(
      'Connection failed',
    )
  })

  it('renders all service health cards when UP', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('system-dashboard')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-gateway')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-position-service')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-market-data-service')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-risk-orchestrator')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-notification-service')).toBeInTheDocument()
  })

  it('shows green dots and UP text for healthy services', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const dot = screen.getByTestId('service-status-dot-gateway')
    expect(dot.className).toContain('bg-green-500')
    expect(screen.getByTestId('service-status-text-gateway')).toHaveTextContent('UP')
  })

  it('shows red dot and DOWN text for unhealthy service', () => {
    render(
      <SystemDashboard
        health={degradedHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const dot = screen.getByTestId('service-status-dot-risk-orchestrator')
    expect(dot.className).toContain('bg-red-500')
    expect(
      screen.getByTestId('service-status-text-risk-orchestrator'),
    ).toHaveTextContent('DOWN')
  })

  it('shows All Systems Operational banner when all UP', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('system-status-banner')).toHaveTextContent(
      'All Systems Operational',
    )
  })

  it('shows Degraded banner when a service is DOWN', () => {
    render(
      <SystemDashboard
        health={degradedHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByTestId('system-status-banner')).toHaveTextContent(
      'Degraded',
    )
  })

  it('renders observability links with correct targets', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const links = screen.getByTestId('observability-links')
    expect(links).toBeInTheDocument()

    const systemHealthLink = screen.getByTestId('obs-link-system-health')
    expect(systemHealthLink).toHaveAttribute(
      'href',
      'http://localhost:3000/d/kinetix-system-health',
    )
    expect(systemHealthLink).toHaveAttribute('target', '_blank')

    const prometheusLink = screen.getByTestId('obs-link-prometheus')
    expect(prometheusLink).toHaveAttribute('href', 'http://localhost:9090')

    const grafanaLink = screen.getByTestId('obs-link-grafana')
    expect(grafanaLink).toHaveAttribute('href', 'http://localhost:3000')
  })

  it('renders friendly service labels', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.getByText('Gateway')).toBeInTheDocument()
    expect(screen.getByText('Position Service')).toBeInTheDocument()
    // "Market Data" appears in both service card and observability link
    expect(screen.getAllByText('Market Data').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('Risk Orchestrator')).toBeInTheDocument()
    expect(screen.getByText('Notifications')).toBeInTheDocument()
  })

  it('calls onRefresh when refresh button is clicked', () => {
    const onRefresh = vi.fn()
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={onRefresh}
      />,
    )

    screen.getByTestId('system-refresh-btn').click()
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })
})
