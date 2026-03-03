import { Zap } from 'lucide-react'
import type { StressTestResultDto } from '../types'
import { Card, Spinner } from './ui'
import { ScenarioControlBar } from './ScenarioControlBar'
import { ScenarioComparisonTable } from './ScenarioComparisonTable'

export interface ScenariosTabProps {
  results: StressTestResultDto[]
  loading: boolean
  error: string | null
  selectedScenario: string | null
  onSelectScenario: (scenario: string | null) => void
  confidenceLevel: string
  onConfidenceLevelChange: (cl: string) => void
  timeHorizonDays: string
  onTimeHorizonDaysChange: (days: string) => void
  onRunAll: () => void
  onCustomScenario?: () => void
}

export function ScenariosTab({
  results,
  loading,
  error,
  selectedScenario,
  onSelectScenario,
  confidenceLevel,
  onConfidenceLevelChange,
  timeHorizonDays,
  onTimeHorizonDaysChange,
  onRunAll,
  onCustomScenario,
}: ScenariosTabProps) {
  return (
    <Card
      data-testid="scenarios-tab"
      header={
        <span className="flex items-center gap-1.5">
          <Zap className="h-4 w-4" />
          Stress Testing
        </span>
      }
    >
      <ScenarioControlBar
        onRunAll={onRunAll}
        loading={loading}
        confidenceLevel={confidenceLevel}
        onConfidenceLevelChange={onConfidenceLevelChange}
        timeHorizonDays={timeHorizonDays}
        onTimeHorizonDaysChange={onTimeHorizonDaysChange}
        onCustomScenario={onCustomScenario}
      />

      {loading && (
        <div data-testid="stress-loading" className="flex items-center gap-2 text-slate-500 text-sm mb-4">
          <Spinner size="sm" />
          Running all stress scenarios...
        </div>
      )}

      {error && (
        <div data-testid="stress-error" className="text-red-600 text-sm mb-4">
          {error}
        </div>
      )}

      <ScenarioComparisonTable
        results={results}
        selectedScenario={selectedScenario}
        onSelectScenario={onSelectScenario}
      />
    </Card>
  )
}
