import type { StressScenarioDto } from '../types'

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-300',
  PENDING_APPROVAL: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300',
  APPROVED: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  RETIRED: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
}

export interface ScenarioGovernancePanelProps {
  scenarios: StressScenarioDto[]
  onSubmit: (id: string) => void
  onApprove: (id: string) => void
  onRetire: (id: string) => void
  loading: boolean
}

export function ScenarioGovernancePanel({
  scenarios,
  onSubmit,
  onApprove,
  onRetire,
  loading,
}: ScenarioGovernancePanelProps) {
  if (loading) {
    return <div className="text-sm text-slate-500">Loading scenarios...</div>
  }

  return (
    <div className="space-y-2" data-testid="governance-panel">
      {scenarios.map((scenario) => (
        <div
          key={scenario.id}
          className="flex items-center justify-between p-3 rounded border border-slate-200 dark:border-slate-700"
        >
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
              {scenario.name}
            </span>
            <span
              className={`text-xs font-medium px-2 py-0.5 rounded-full ${STATUS_COLORS[scenario.status] ?? ''}`}
            >
              {scenario.status}
            </span>
            {scenario.approvedBy && (
              <span className="text-xs text-slate-500">
                by {scenario.approvedBy}
              </span>
            )}
          </div>

          <div className="flex items-center gap-2">
            {scenario.status === 'DRAFT' && (
              <button
                className="text-xs px-2 py-1 rounded bg-blue-600 text-white hover:bg-blue-700"
                onClick={() => onSubmit(scenario.id)}
              >
                Submit for Approval
              </button>
            )}
            {scenario.status === 'PENDING_APPROVAL' && (
              <button
                className="text-xs px-2 py-1 rounded bg-green-600 text-white hover:bg-green-700"
                onClick={() => onApprove(scenario.id)}
              >
                Approve
              </button>
            )}
            {scenario.status === 'APPROVED' && (
              <button
                className="text-xs px-2 py-1 rounded bg-red-600 text-white hover:bg-red-700"
                onClick={() => onRetire(scenario.id)}
              >
                Retire
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  )
}
