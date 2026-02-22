import type { SystemHealthResponse } from '../api/system'

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
    url: 'http://localhost:3000/d/system-health',
    description: 'Request rate, error rate, latency, JVM, Kafka lag',
  },
  {
    name: 'Risk Overview',
    url: 'http://localhost:3000/d/risk-overview',
    description: 'VaR gauge, ES, component breakdown',
  },
  {
    name: 'Market Data',
    url: 'http://localhost:3000/d/market-data',
    description: 'Feed rate, latency, staleness',
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
      <p data-testid="system-loading" className="text-gray-500">
        Loading system health...
      </p>
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
      {/* Overall status banner */}
      <div
        data-testid="system-status-banner"
        className={`mb-6 rounded-lg px-4 py-3 text-sm font-medium ${
          overallUp
            ? 'bg-green-50 text-green-800 border border-green-200'
            : 'bg-yellow-50 text-yellow-800 border border-yellow-200'
        }`}
      >
        {overallUp ? 'All Systems Operational' : 'Degraded'}
        <button
          data-testid="system-refresh-btn"
          onClick={onRefresh}
          className="ml-4 text-xs underline"
        >
          Refresh
        </button>
      </div>

      {/* Service health grid */}
      <h2 className="text-lg font-semibold mb-3">Service Health</h2>
      <div
        data-testid="service-health-grid"
        className="grid grid-cols-3 gap-4 mb-8"
      >
        {Object.entries(services).map(([key, svc]) => {
          const up = svc.status === 'UP'
          return (
            <div
              key={key}
              data-testid={`service-card-${key}`}
              className="rounded-lg border bg-white p-4"
            >
              <div className="flex items-center gap-2">
                <span
                  data-testid={`service-status-dot-${key}`}
                  className={`inline-block h-3 w-3 rounded-full ${
                    up ? 'bg-green-500' : 'bg-red-500'
                  }`}
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
            </div>
          )
        })}
      </div>

      {/* Observability links */}
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
            className="rounded-lg border bg-white p-4 hover:shadow transition-shadow"
          >
            <span className="font-medium text-indigo-600">{link.name}</span>
            <p className="mt-1 text-sm text-gray-500">{link.description}</p>
          </a>
        ))}
      </div>
    </div>
  )
}
