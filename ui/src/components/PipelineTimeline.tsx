import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import type { PipelineStepDto } from '../types'

interface PipelineTimelineProps {
  steps: PipelineStepDto[]
}

const STEP_LABELS: Record<string, string> = {
  FETCH_POSITIONS: 'Fetch Positions',
  DISCOVER_DEPENDENCIES: 'Discover Dependencies',
  FETCH_MARKET_DATA: 'Fetch Market Data',
  CALCULATE_VAR: 'Calculate VaR',
  PUBLISH_RESULT: 'Publish Result',
}

function StatusDotInline({ status }: { status: string }) {
  const color =
    status === 'COMPLETED'
      ? 'bg-green-500'
      : status === 'FAILED'
        ? 'bg-red-500'
        : 'bg-slate-300'
  return <span data-testid={`step-dot-${status}`} className={`inline-block h-3 w-3 rounded-full ${color} shrink-0`} />
}

export function PipelineTimeline({ steps }: PipelineTimelineProps) {
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})

  const toggle = (index: number) => {
    setExpanded((prev) => ({ ...prev, [index]: !prev[index] }))
  }

  return (
    <div data-testid="pipeline-timeline" className="relative pl-4">
      <div className="absolute left-[5px] top-2 bottom-2 w-px bg-slate-300" />
      {steps.map((step, i) => {
        const isOpen = expanded[i] ?? false
        const hasDetails = Object.keys(step.details).length > 0

        return (
          <div key={i} data-testid={`pipeline-step-${step.name}`} className="relative mb-3 last:mb-0">
            <div className="flex items-center gap-2">
              <StatusDotInline status={step.status} />
              <span className="text-sm font-medium text-slate-700">
                {STEP_LABELS[step.name] ?? step.name}
              </span>
              {step.durationMs != null && (
                <span className="text-xs text-slate-400">{step.durationMs}ms</span>
              )}
              {hasDetails && (
                <button
                  data-testid={`toggle-${step.name}`}
                  onClick={() => toggle(i)}
                  className="text-slate-400 hover:text-slate-600"
                >
                  {isOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                </button>
              )}
            </div>
            {step.error && (
              <p className="ml-5 text-xs text-red-600 mt-0.5">{step.error}</p>
            )}
            {isOpen && hasDetails && (
              <div data-testid={`details-${step.name}`} className="ml-5 mt-1 text-xs text-slate-500 space-y-0.5">
                {Object.entries(step.details).map(([key, value]) => (
                  <div key={key}>
                    <span className="font-medium">{key}:</span> {value}
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
