import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SystemDashboard } from './SystemDashboard'
import type { SystemHealthResponse } from '../api/system'

const allUpHealth: SystemHealthResponse = {
  status: 'UP',
  services: {
    gateway: { status: 'UP' },
    'position-service': { status: 'UP' },
    'price-service': { status: 'UP' },
    'risk-orchestrator': { status: 'UP' },
    'notification-service': { status: 'UP' },
    'rates-service': { status: 'UP' },
    'reference-data-service': { status: 'UP' },
    'volatility-service': { status: 'UP' },
    'correlation-service': { status: 'UP' },
  },
}

const degradedHealth: SystemHealthResponse = {
  status: 'DEGRADED',
  services: {
    gateway: { status: 'UP' },
    'position-service': { status: 'UP' },
    'price-service': { status: 'UP' },
    'risk-orchestrator': { status: 'DOWN' },
    'notification-service': { status: 'UP' },
    'rates-service': { status: 'UP' },
    'reference-data-service': { status: 'UP' },
    'volatility-service': { status: 'UP' },
    'correlation-service': { status: 'UP' },
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
    expect(screen.getByTestId('service-card-price-service')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-risk-orchestrator')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-notification-service')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-rates-service')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-reference-data-service')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-volatility-service')).toBeInTheDocument()
    expect(screen.getByTestId('service-card-correlation-service')).toBeInTheDocument()
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
    expect(screen.getByText('Prices')).toBeInTheDocument()
    expect(screen.getByText('Risk Orchestrator')).toBeInTheDocument()
    expect(screen.getByText('Notifications')).toBeInTheDocument()
    expect(screen.getByText('Rates')).toBeInTheDocument()
    expect(screen.getByText('Reference Data')).toBeInTheDocument()
    expect(screen.getByText('Volatility')).toBeInTheDocument()
    expect(screen.getByText('Correlations')).toBeInTheDocument()
  })

  it('gateway card has a Grafana icon linking to the gateway dashboard', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const link = screen.getByTestId('service-grafana-link-gateway')
    expect(link).toHaveAttribute(
      'href',
      'http://localhost:3000/d/kinetix-gateway',
    )
    expect(link).toHaveAttribute('target', '_blank')
  })

  it('position-service card has a Grafana icon linking to the position service dashboard', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const link = screen.getByTestId('service-grafana-link-position-service')
    expect(link).toHaveAttribute(
      'href',
      'http://localhost:3000/d/kinetix-position-service',
    )
    expect(link).toHaveAttribute('target', '_blank')
  })

  it('risk-orchestrator card has a Grafana icon linking to the risk orchestrator dashboard', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const link = screen.getByTestId('service-grafana-link-risk-orchestrator')
    expect(link).toHaveAttribute(
      'href',
      'http://localhost:3000/d/kinetix-risk-orchestrator',
    )
    expect(link).toHaveAttribute('target', '_blank')
  })

  it('notification-service card has a Grafana icon linking to the notification service dashboard', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const link = screen.getByTestId('service-grafana-link-notification-service')
    expect(link).toHaveAttribute(
      'href',
      'http://localhost:3000/d/kinetix-notification-service',
    )
    expect(link).toHaveAttribute('target', '_blank')
  })

  it('price-service card has a Grafana icon linking to the prices dashboard', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const link = screen.getByTestId('service-grafana-link-price-service')
    expect(link).toHaveAttribute(
      'href',
      'http://localhost:3000/d/kinetix-prices',
    )
    expect(link).toHaveAttribute('target', '_blank')
  })

  it('rates-service card has a Grafana icon linking to the rates service dashboard', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    const link = screen.getByTestId('service-grafana-link-rates-service')
    expect(link).toHaveAttribute(
      'href',
      'http://localhost:3000/d/kinetix-rates-service',
    )
    expect(link).toHaveAttribute('target', '_blank')
  })

  it('does not include Prices in observability links', () => {
    render(
      <SystemDashboard
        health={allUpHealth}
        loading={false}
        error={null}
        onRefresh={() => {}}
      />,
    )

    expect(screen.queryByTestId('obs-link-prices')).not.toBeInTheDocument()
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
