import { useState, useCallback } from 'react'
import { Zap } from 'lucide-react'
import type { StressTestResultDto } from '../types'
import { runStressTest } from '../api/stress'
import { createScenario, submitScenario } from '../api/scenarios'
import { exportStressResultsToCsv } from '../utils/exportStressResults'
import type { ScenarioSavePayload, ScenarioRunPayload } from '../hooks/useCustomScenario'
import { useScenarioGovernance } from '../hooks/useScenarioGovernance'
import { Card, Spinner } from './ui'
import { ScenarioControlBar } from './ScenarioControlBar'
import { ScenarioComparisonTable } from './ScenarioComparisonTable'
import { ScenarioDetailPanel } from './ScenarioDetailPanel'
import { ScenarioComparisonView } from './ScenarioComparisonView'
import { ScenarioGovernancePanel } from './ScenarioGovernancePanel'
import { CustomScenarioBuilder } from './CustomScenarioBuilder'

export interface ScenariosTabProps {
  portfolioId: string | null
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
  onAppendResult: (result: StressTestResultDto) => void
}

export function ScenariosTab({
  portfolioId,
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
  onAppendResult,
}: ScenariosTabProps) {
  const [builderOpen, setBuilderOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [running, setRunning] = useState(false)
  const [checkedScenarios, setCheckedScenarios] = useState<Set<string>>(new Set())
  const [showComparison, setShowComparison] = useState(false)
  const [showGovernance, setShowGovernance] = useState(false)
  const governance = useScenarioGovernance()

  const handleToggleCheck = useCallback((scenario: string) => {
    setCheckedScenarios((prev) => {
      const next = new Set(prev)
      if (next.has(scenario)) {
        next.delete(scenario)
      } else if (next.size < 3) {
        next.add(scenario)
      }
      return next
    })
  }, [])

  const handleCompare = useCallback(() => {
    setShowComparison(true)
  }, [])

  const handleSave = useCallback(
    async (payload: ScenarioSavePayload) => {
      setSaving(true)
      try {
        const shocks = JSON.stringify({
          volShocks: payload.volShocks,
          priceShocks: payload.priceShocks,
        })
        const scenario = await createScenario({
          name: payload.name,
          description: payload.description,
          shocks,
          createdBy: 'user',
        })
        await submitScenario(scenario.id)
        setBuilderOpen(false)
      } finally {
        setSaving(false)
      }
    },
    [],
  )

  const handleRunAdHoc = useCallback(
    async (payload: ScenarioRunPayload) => {
      if (!portfolioId) return
      setRunning(true)
      try {
        const result = await runStressTest(portfolioId, 'AD_HOC', {
          volShocks: payload.volShocks,
          priceShocks: payload.priceShocks,
          confidenceLevel,
          timeHorizonDays,
        })
        if (result) {
          onAppendResult(result)
        }
      } finally {
        setRunning(false)
      }
    },
    [portfolioId, confidenceLevel, timeHorizonDays, onAppendResult],
  )

  const comparedScenarios = results.filter((r) => checkedScenarios.has(r.scenarioName))

  return (
    <>
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
          onCustomScenario={() => setBuilderOpen(true)}
          compareCount={checkedScenarios.size}
          onCompare={handleCompare}
          onExportCsv={results.length > 0 ? () => exportStressResultsToCsv(results) : undefined}
          onManageScenarios={() => setShowGovernance((v) => !v)}
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
          checkedScenarios={checkedScenarios}
          onToggleCheck={handleToggleCheck}
          scenarioMetadata={governance.scenarios}
        />

        {showComparison && comparedScenarios.length >= 2 && (
          <ScenarioComparisonView scenarios={comparedScenarios} />
        )}

        {showGovernance && (
          <ScenarioGovernancePanel
            scenarios={governance.scenarios}
            onSubmit={governance.submit}
            onApprove={governance.approve}
            onRetire={governance.retire}
            loading={governance.loading}
          />
        )}

        <ScenarioDetailPanel
          result={selectedScenario ? results.find((r) => r.scenarioName === selectedScenario) ?? null : null}
        />
      </Card>

      <CustomScenarioBuilder
        open={builderOpen}
        onClose={() => setBuilderOpen(false)}
        onSave={handleSave}
        onRunAdHoc={handleRunAdHoc}
        saving={saving}
        running={running}
      />
    </>
  )
}
