import { RefreshCw, ExternalLink, Server } from 'lucide-react'
import type { SystemHealthResponse } from '../api/system'
import { Card, Button, StatusDot, Spinner } from './ui'

interface Props {
  health: SystemHealthResponse | null
  loading: boolean
  error: string | null
  onRefresh: () => void
}

const SERVICE_LABELS: Record<string, string> = {
  gateway: 'Gateway',
  'position-service': 'Position Service',
  'market-data-service': 'Market Data',
  'risk-orchestrator': 'Risk Orchestrator',
  'notification-service': 'Notifications',
}

const OBSERVABILITY_LINKS = [
  {
    name: 'System Health',
    url: 'http://localhost:3000/d/kinetix-system-health',
    description: 'Request rate, error rate, latency, JVM, Kafka lag',
  },
  {
    name: 'Risk Overview',
    url: 'http://localhost:3000/d/kinetix-risk-overview',
    description: 'VaR gauge, ES, component breakdown',
  },
  {
    name: 'Market Data',
    url: 'http://localhost:3000/d/kinetix-market-data',
    description: 'Feed rate, latency, staleness',
  },
  {
    name: 'Service Logs',
    url: 'http://localhost:3000/d/kinetix-service-logs',
    description: 'Log volume, errors, warnings, full log lines',
  },
  {
    name: 'Prometheus',
    url: 'http://localhost:9090',
    description: 'Raw metrics & alert rules',
  },
  {
    name: 'Grafana',
    url: 'http://localhost:3000',
    description: 'All dashboards',
  },
]

export function SystemDashboard({ health, loading, error, onRefresh }: Props) {
  if (loading) {
    return (
      <div data-testid="system-loading" className="flex items-center gap-2 text-slate-500">
        <Spinner size="sm" />
        Loading system health...
      </div>
    )
  }

  if (error) {
    return (
      <p data-testid="system-error" className="text-red-600">
        {error}
      </p>
    )
  }

  const services = health?.services ?? {}
  const overallUp = health?.status === 'UP'

  return (
    <div data-testid="system-dashboard">
      <div
        data-testid="system-status-banner"
        className={`mb-6 rounded-lg px-4 py-3 text-sm font-medium flex items-center justify-between ${
          overallUp
            ? 'bg-green-50 text-green-800 border border-green-200'
            : 'bg-yellow-50 text-yellow-800 border border-yellow-200'
        }`}
      >
        <span>{overallUp ? 'All Systems Operational' : 'Degraded'}</span>
        <Button
          data-testid="system-refresh-btn"
          variant="secondary"
          size="sm"
          icon={<RefreshCw className="h-3 w-3" />}
          onClick={onRefresh}
        >
          Refresh
        </Button>
      </div>

      <h2 className="text-lg font-semibold mb-3 flex items-center gap-2">
        <Server className="h-5 w-5 text-slate-500" />
        Service Health
      </h2>
      <div
        data-testid="service-health-grid"
        className="grid grid-cols-3 gap-4 mb-8"
      >
        {Object.entries(services).map(([key, svc]) => {
          const up = svc.status === 'UP'
          return (
            <Card key={key} data-testid={`service-card-${key}`}>
              <div className="flex items-center gap-2">
                <StatusDot
                  data-testid={`service-status-dot-${key}`}
                  status={up ? 'up' : 'down'}
                  pulse={up}
                />
                <span className="font-medium">
                  {SERVICE_LABELS[key] ?? key}
                </span>
              </div>
              <p
                data-testid={`service-status-text-${key}`}
                className={`mt-1 text-sm ${up ? 'text-green-700' : 'text-red-700'}`}
              >
                {svc.status}
              </p>
            </Card>
          )
        })}
      </div>

      <h2 className="text-lg font-semibold mb-3">Observability</h2>
      <div
        data-testid="observability-links"
        className="grid grid-cols-3 gap-4"
      >
        {OBSERVABILITY_LINKS.map((link) => (
          <a
            key={link.name}
            href={link.url}
            target="_blank"
            rel="noopener noreferrer"
            data-testid={`obs-link-${link.name.toLowerCase().replace(/\s+/g, '-')}`}
            className="block group"
          >
            <Card className="hover:shadow-md hover:border-primary-300 transition-shadow">
              <div className="flex items-center justify-between">
                <span className="font-medium text-primary-600">{link.name}</span>
                <ExternalLink className="h-3.5 w-3.5 text-slate-400 group-hover:text-primary-500 transition-colors" />
              </div>
              <p className="mt-1 text-sm text-slate-500">{link.description}</p>
            </Card>
          </a>
        ))}
      </div>
    </div>
  )
}
