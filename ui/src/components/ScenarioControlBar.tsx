import { Zap } from 'lucide-react'
import { Button, Select } from './ui'

interface ScenarioControlBarProps {
  onRunAll: () => void
  loading: boolean
  confidenceLevel: string
  onConfidenceLevelChange: (cl: string) => void
  timeHorizonDays: string
  onTimeHorizonDaysChange: (days: string) => void
  onCustomScenario?: () => void
}

export function ScenarioControlBar({
  onRunAll,
  loading,
  confidenceLevel,
  onConfidenceLevelChange,
  timeHorizonDays,
  onTimeHorizonDaysChange,
  onCustomScenario,
}: ScenarioControlBarProps) {
  return (
    <div data-testid="scenario-control-bar" className="flex items-center gap-3 mb-4">
      <Button
        data-testid="run-all-btn"
        variant="danger"
        size="md"
        icon={<Zap className="h-3.5 w-3.5" />}
        onClick={onRunAll}
        loading={loading}
      >
        {loading ? 'Running...' : 'Run All Scenarios'}
      </Button>

      <Select
        data-testid="confidence-level-select"
        value={confidenceLevel}
        onChange={(e) => onConfidenceLevelChange(e.target.value)}
        aria-label="Confidence level"
      >
        <option value="CL_95">95% CL</option>
        <option value="CL_99">99% CL</option>
      </Select>

      <Select
        data-testid="time-horizon-select"
        value={timeHorizonDays}
        onChange={(e) => onTimeHorizonDaysChange(e.target.value)}
        aria-label="Time horizon"
      >
        <option value="1">1 Day</option>
        <option value="5">5 Days</option>
        <option value="10">10 Days</option>
      </Select>

      {onCustomScenario && (
        <Button
          data-testid="custom-scenario-btn"
          variant="primary"
          size="md"
          onClick={onCustomScenario}
        >
          + Custom Scenario
        </Button>
      )}
    </div>
  )
}
