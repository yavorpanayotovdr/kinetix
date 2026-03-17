import { PHASE_LABELS, PHASE_ORDER } from '../constants/phaseLabels'

interface PhaseStepperMiniProps {
  currentPhase: string
  failed?: boolean
}

export function PhaseStepperMini({ currentPhase, failed = false }: PhaseStepperMiniProps) {
  const activeIndex = PHASE_ORDER.indexOf(currentPhase)
  const activeLabel = activeIndex >= 0 ? PHASE_LABELS[currentPhase] : currentPhase

  return (
    <div data-testid="phase-stepper-mini" role="group" aria-label="Job phases" className="mt-1">
      <div className="flex items-center gap-0.5">
        {PHASE_ORDER.map((phase, i) => {
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

          const connectorClass = i < PHASE_ORDER.length - 1
            ? (isCompleted ? 'bg-blue-300' : 'bg-slate-200')
            : ''

          return (
            <div key={phase} className="flex items-center gap-0.5">
              <span
                data-testid={`phase-dot-${phase}`}
                className={`inline-block transition-colors duration-300 ${dotClass}`}
                aria-label={`${PHASE_LABELS[phase]}: ${isCompleted ? 'completed' : isActive ? (failed ? 'failed' : 'active') : 'pending'}`}
              />
              {isPending || isCompleted || isActive ? null : null}
              {i < PHASE_ORDER.length - 1 && (
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
