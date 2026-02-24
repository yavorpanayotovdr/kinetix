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

interface PositionItem {
  instrumentId: string
  [key: string]: string
}

export function PipelineTimeline({ steps }: PipelineTimelineProps) {
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})
  const [expandedPositions, setExpandedPositions] = useState<Record<string, boolean>>({})

  const toggle = (index: number) => {
    setExpanded((prev) => ({ ...prev, [index]: !prev[index] }))
  }

  const togglePosition = (key: string) => {
    setExpandedPositions((prev) => ({ ...prev, [key]: !prev[key] }))
  }

  const parsePositions = (details: Record<string, string>): PositionItem[] | null => {
    const raw = details['positions']
    if (!raw) return null
    try {
      return JSON.parse(raw) as PositionItem[]
    } catch {
      return null
    }
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
            {isOpen && hasDetails && (() => {
              const positions = parsePositions(step.details)
              return (
                <div data-testid={`details-${step.name}`} className="ml-5 mt-1 text-xs text-slate-500 space-y-0.5">
                  {Object.entries(step.details)
                    .filter(([key]) => key !== 'positions')
                    .map(([key, value]) => (
                      <div key={key}>
                        <span className="font-medium">{key}:</span> {value}
                      </div>
                    ))}
                  {positions && positions.map((pos, j) => {
                    const posKey = `${i}-${pos.instrumentId}`
                    const isPosOpen = expandedPositions[posKey] ?? false
                    return (
                      <div key={j} className="mt-1">
                        <button
                          data-testid={`position-${pos.instrumentId}`}
                          onClick={() => togglePosition(posKey)}
                          className="flex items-center gap-1 text-slate-600 hover:text-slate-800"
                        >
                          {isPosOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                          <span>{pos.instrumentId}</span>
                        </button>
                        {isPosOpen && (
                          <pre
                            data-testid={`position-json-${pos.instrumentId}`}
                            className="ml-4 mt-0.5 p-2 bg-slate-50 rounded text-[11px] font-mono overflow-x-auto"
                          >
                            {JSON.stringify(pos, null, 2)}
                          </pre>
                        )}
                      </div>
                    )
                  })}
                </div>
              )
            })()}
          </div>
        )
      })}
    </div>
  )
}
