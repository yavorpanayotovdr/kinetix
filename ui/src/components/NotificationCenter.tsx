import { useMemo, useState } from 'react'
import { Bell, Plus, Trash2, AlertTriangle, AlertCircle, Info } from 'lucide-react'
import type { AlertRuleDto, AlertEventDto, CreateAlertRuleRequestDto } from '../types'
import { formatRelativeTime } from '../utils/format'
import { Card, Button, Badge, Input, Select, Spinner } from './ui'

interface NotificationCenterProps {
  rules: AlertRuleDto[]
  alerts: AlertEventDto[]
  loading: boolean
  error: string | null
  onCreateRule: (request: CreateAlertRuleRequestDto) => void
  onDeleteRule: (ruleId: string) => void
}

const severityBadgeVariant: Record<string, 'critical' | 'warning' | 'info'> = {
  CRITICAL: 'critical',
  WARNING: 'warning',
  INFO: 'info',
}

const severityBorderColor: Record<string, string> = {
  CRITICAL: 'border-red-500',
  WARNING: 'border-yellow-500',
  INFO: 'border-blue-500',
}

const severityIcon: Record<string, typeof AlertTriangle> = {
  CRITICAL: AlertCircle,
  WARNING: AlertTriangle,
  INFO: Info,
}

const severityOrder: Record<string, number> = {
  CRITICAL: 0,
  WARNING: 1,
  INFO: 2,
}

export function NotificationCenter({
  rules,
  alerts,
  loading,
  error,
  onCreateRule,
  onDeleteRule,
}: NotificationCenterProps) {
  const [name, setName] = useState('')
  const [type, setType] = useState('VAR_BREACH')
  const [threshold, setThreshold] = useState('')
  const [operator, setOperator] = useState('GREATER_THAN')
  const [severity, setSeverity] = useState('CRITICAL')
  const [channels, setChannels] = useState<string[]>(['IN_APP'])

  const sortedAlerts = useMemo(
    () =>
      [...alerts].sort((a, b) => {
        const timeCompare = new Date(b.triggeredAt).getTime() - new Date(a.triggeredAt).getTime()
        if (timeCompare !== 0) return timeCompare
        return (severityOrder[a.severity] ?? 99) - (severityOrder[b.severity] ?? 99)
      }),
    [alerts],
  )

  function handleChannelToggle(ch: string) {
    setChannels((prev) =>
      prev.includes(ch) ? prev.filter((c) => c !== ch) : [...prev, ch],
    )
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    onCreateRule({
      name,
      type,
      threshold: Number(threshold),
      operator,
      severity,
      channels,
    })
    setName('')
    setThreshold('')
  }

  return (
    <Card
      data-testid="notification-center"
      header={<span className="flex items-center gap-1.5"><Bell className="h-4 w-4" />Notification Center</span>}
    >
      {loading && (
        <div data-testid="notification-loading" className="flex items-center gap-2 text-slate-500 text-sm">
          <Spinner size="sm" />
          Loading notifications...
        </div>
      )}

      {error && (
        <div data-testid="notification-error" className="text-red-600 text-sm mb-3">
          {error}
        </div>
      )}

      {/* Create Rule Form */}
      <div data-testid="create-rule-form" className="mb-4 p-3 bg-slate-50 rounded-lg">
        <h3 className="text-sm font-semibold text-slate-700 mb-2">Create Alert Rule</h3>
        <form onSubmit={handleSubmit} className="grid grid-cols-3 gap-2 text-sm">
          <Input
            data-testid="rule-name-input"
            placeholder="Rule name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <Select
            data-testid="rule-type-select"
            value={type}
            onChange={(e) => setType(e.target.value)}
          >
            <option value="VAR_BREACH">VAR_BREACH</option>
            <option value="PNL_THRESHOLD">PNL_THRESHOLD</option>
            <option value="RISK_LIMIT">RISK_LIMIT</option>
          </Select>
          <Input
            data-testid="rule-threshold-input"
            type="number"
            placeholder="Threshold"
            value={threshold}
            onChange={(e) => setThreshold(e.target.value)}
            required
          />
          <Select
            data-testid="rule-operator-select"
            value={operator}
            onChange={(e) => setOperator(e.target.value)}
          >
            <option value="GREATER_THAN">GREATER_THAN</option>
            <option value="LESS_THAN">LESS_THAN</option>
            <option value="EQUALS">EQUALS</option>
          </Select>
          <Select
            data-testid="rule-severity-select"
            value={severity}
            onChange={(e) => setSeverity(e.target.value)}
          >
            <option value="CRITICAL">CRITICAL</option>
            <option value="WARNING">WARNING</option>
            <option value="INFO">INFO</option>
          </Select>
          <div className="flex items-center gap-2">
            {['IN_APP', 'EMAIL', 'WEBHOOK'].map((ch) => (
              <label key={ch} className="flex items-center gap-1 text-xs">
                <input
                  type="checkbox"
                  data-testid={`channel-${ch}`}
                  checked={channels.includes(ch)}
                  onChange={() => handleChannelToggle(ch)}
                />
                {ch}
              </label>
            ))}
          </div>
          <Button
            data-testid="create-rule-btn"
            type="submit"
            variant="primary"
            size="md"
            icon={<Plus className="h-3.5 w-3.5" />}
            className="col-span-3"
          >
            Create Rule
          </Button>
        </form>
      </div>

      {/* Alert Rules Table */}
      <h3 className="text-sm font-semibold text-slate-700 mb-2">Alert Rules</h3>
      <table data-testid="rules-table" className="w-full text-sm mb-4">
        <thead>
          <tr className="border-b text-left text-slate-600">
            <th className="py-2">Name</th>
            <th className="py-2">Type</th>
            <th className="py-2 text-right">Threshold</th>
            <th className="py-2">Severity</th>
            <th className="py-2">Enabled</th>
            <th className="py-2"></th>
          </tr>
        </thead>
        <tbody>
          {rules.map((rule) => (
            <tr key={rule.id} className="border-b hover:bg-slate-50 transition-colors">
              <td className="py-1.5">{rule.name}</td>
              <td className="py-1.5">{rule.type}</td>
              <td className="py-1.5 text-right">{rule.threshold.toLocaleString()}</td>
              <td className="py-1.5">
                <Badge variant={severityBadgeVariant[rule.severity] ?? 'neutral'}>
                  {rule.severity}
                </Badge>
              </td>
              <td className="py-1.5">{rule.enabled ? 'Yes' : 'No'}</td>
              <td className="py-1.5">
                <button
                  data-testid={`delete-rule-${rule.id}`}
                  onClick={() => onDeleteRule(rule.id)}
                  className="text-red-500 hover:text-red-700 transition-colors"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Recent Alerts */}
      <h3 className="text-sm font-semibold text-slate-700 mb-2">Recent Alerts</h3>
      <div data-testid="alerts-list" className="space-y-2">
        {sortedAlerts.map((alert) => {
          const SevIcon = severityIcon[alert.severity] ?? Info
          return (
            <div
              key={alert.id}
              className={`flex items-start gap-2 p-2 bg-slate-50 rounded text-sm border-l-4 ${severityBorderColor[alert.severity] ?? 'border-gray-300'}`}
            >
              <SevIcon className="h-4 w-4 mt-0.5 shrink-0 text-slate-500" />
              <span
                data-testid={`severity-badge-${alert.id}`}
                className={`px-2 py-0.5 rounded text-xs font-medium ${
                  alert.severity === 'CRITICAL' ? 'bg-red-100 text-red-800' :
                  alert.severity === 'WARNING' ? 'bg-yellow-100 text-yellow-800' :
                  'bg-blue-100 text-blue-800'
                }`}
              >
                {alert.severity}
              </span>
              <div className="flex-1">
                <div className="text-slate-800">{alert.message}</div>
                <div className="text-xs text-slate-500">
                  Portfolio: {alert.portfolioId} | {formatRelativeTime(alert.triggeredAt)}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </Card>
  )
}
