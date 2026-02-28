import type { StressTestResultDto } from '../types'
import { StressTestPanel } from './StressTestPanel'

interface ScenariosTabProps {
  scenarios: string[]
  result: StressTestResultDto | null
  loading: boolean
  error: string | null
  selectedScenario: string
  onScenarioChange: (scenario: string) => void
  onRun: () => void
}

export function ScenariosTab({
  scenarios,
  result,
  loading,
  error,
  selectedScenario,
  onScenarioChange,
  onRun,
}: ScenariosTabProps) {
  return (
    <StressTestPanel
      scenarios={scenarios}
      result={result}
      loading={loading}
      error={error}
      selectedScenario={selectedScenario}
      onScenarioChange={onScenarioChange}
      onRun={onRun}
    />
  )
}
