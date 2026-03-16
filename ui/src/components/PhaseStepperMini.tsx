interface PhaseStepperMiniProps {
  currentPhase: string
  failed?: boolean
}

interface VisualPhase {
  key: string
  label: string
  phases: string[]
}

const VISUAL_PHASES: VisualPhase[] = [
  { key: 'positions', label: 'Loading Positions', phases: ['FETCH_POSITIONS', 'DISCOVER_DEPENDENCIES'] },
  { key: 'market-data', label: 'Fetching Market Data', phases: ['FETCH_MARKET_DATA'] },
  { key: 'valuation', label: 'Calculating Risk', phases: ['VALUATION', 'PUBLISH_RESULT'] },
]

function getVisualIndex(phase: string): number {
  for (let i = 0; i < VISUAL_PHASES.length; i++) {
    if (VISUAL_PHASES[i].phases.includes(phase)) return i
  }
  return -1
}

export function PhaseStepperMini({ currentPhase, failed = false }: PhaseStepperMiniProps) {
  const activeIndex = getVisualIndex(currentPhase)
  const activeLabel = activeIndex >= 0 ? VISUAL_PHASES[activeIndex].label : currentPhase

  return (
    <div data-testid="phase-stepper-mini" role="group" aria-label="Job phases" className="mt-1">
      <div className="flex items-center gap-0.5">
        {VISUAL_PHASES.map((vp, i) => {
          const isCompleted = i < activeIndex
          const isActive = i === activeIndex
          const isPending = i > activeIndex

          let dotClass: string
          if (failed && isActive) {
            dotClass = 'bg-red-500 h-2 w-2 rounded-full'
          } else if (isCompleted) {
            dotClass = 'bg-blue-400 h-2 w-2 rounded-full'
          } else if (isActive) {
            dotClass = 'bg-blue-500 h-2.5 w-2.5 rounded-full animate-pulse'
          } else {
            dotClass = 'bg-slate-200 h-2 w-2 rounded-full'
          }

          const connectorClass = i < VISUAL_PHASES.length - 1
            ? (isCompleted ? 'bg-blue-300' : 'bg-slate-200')
            : ''

          return (
            <div key={vp.key} className="flex items-center gap-0.5">
              <span
                data-testid={`phase-dot-${vp.key}`}
                className={`inline-block transition-colors duration-300 ${dotClass}`}
                aria-label={`${vp.label}: ${isCompleted ? 'completed' : isActive ? (failed ? 'failed' : 'active') : 'pending'}`}
              />
              {isPending || isCompleted || isActive ? null : null}
              {i < VISUAL_PHASES.length - 1 && (
                <span className={`inline-block w-3 h-px transition-colors duration-300 ${connectorClass}`} />
              )}
            </div>
          )
        })}
      </div>
      <div data-testid="phase-label" className="text-xs text-slate-500 mt-0.5">
        {activeLabel}
      </div>
    </div>
  )
}
