import { useStressTest } from '../hooks/useStressTest'
import { StressTestPanel } from './StressTestPanel'

interface ScenariosTabProps {
  portfolioId: string | null
}

export function ScenariosTab({ portfolioId }: ScenariosTabProps) {
  const stress = useStressTest(portfolioId)

  return (
    <StressTestPanel
      scenarios={stress.scenarios}
      result={stress.result}
      loading={stress.loading}
      error={stress.error}
      selectedScenario={stress.selectedScenario}
      onScenarioChange={stress.setSelectedScenario}
      onRun={stress.run}
    />
  )
}
