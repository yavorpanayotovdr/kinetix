import { useState } from 'react'
import { ChevronDown, ChevronRight, Copy, Check } from 'lucide-react'
import type { JobStepDto } from '../types'

interface JobTimelineProps {
  steps: JobStepDto[]
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

function CopyButton({ text, testId }: { text: string; testId: string }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = () => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }

  return (
    <button
      data-testid={testId}
      onClick={handleCopy}
      className="absolute top-1.5 right-1.5 p-1 rounded hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
      title="Copy JSON"
    >
      {copied ? <Check className="h-3 w-3 text-green-500" /> : <Copy className="h-3 w-3" />}
    </button>
  )
}

interface PositionItem {
  instrumentId: string
  [key: string]: string
}

interface DependencyItem {
  instrumentId: string
  dataType: string
  [key: string]: string
}

export function JobTimeline({ steps }: JobTimelineProps) {
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})
  const [expandedItems, setExpandedItems] = useState<Record<string, boolean>>({})

  const toggle = (index: number) => {
    setExpanded((prev) => ({ ...prev, [index]: !prev[index] }))
  }

  const toggleItem = (key: string) => {
    setExpandedItems((prev) => ({ ...prev, [key]: !prev[key] }))
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

  const parseDependencies = (details: Record<string, string>): DependencyItem[] | null => {
    const raw = details['dependencies']
    if (!raw) return null
    try {
      return JSON.parse(raw) as DependencyItem[]
    } catch {
      return null
    }
  }

  return (
    <div data-testid="job-timeline" className="relative pl-4">
      <div className="absolute left-[5px] top-2 bottom-2 w-px bg-slate-300" />
      {steps.map((step, i) => {
        const isOpen = expanded[i] ?? false
        const hasDetails = Object.keys(step.details).length > 0

        return (
          <div key={i} data-testid={`job-step-${step.name}`} className="relative mb-3 last:mb-0">
            <div
              data-testid={`toggle-${step.name}`}
              onClick={hasDetails ? () => toggle(i) : undefined}
              className={`flex items-center gap-2${hasDetails ? ' cursor-pointer hover:bg-slate-50 -mx-1 px-1 rounded' : ''}`}
            >
              <StatusDotInline status={step.status} />
              {hasDetails && (
                isOpen ? <ChevronDown className="h-3 w-3 text-slate-400" /> : <ChevronRight className="h-3 w-3 text-slate-400" />
              )}
              <span className="text-sm font-medium text-slate-700">
                {STEP_LABELS[step.name] ?? step.name}
              </span>
              {step.durationMs != null && (
                <span className="text-xs text-slate-400">{step.durationMs}ms</span>
              )}
            </div>
            {step.error && (
              <p className="ml-5 text-xs text-red-600 mt-0.5">{step.error}</p>
            )}
            {isOpen && hasDetails && (() => {
              const positions = parsePositions(step.details)
              const dependencies = parseDependencies(step.details)
              return (
                <div data-testid={`details-${step.name}`} className="ml-5 mt-1 text-xs text-slate-500 space-y-0.5">
                  {Object.entries(step.details)
                    .filter(([key]) => key !== 'positions' && key !== 'dependencies')
                    .map(([key, value]) => (
                      <div key={key}>
                        <span className="font-medium">{key}:</span> {value}
                      </div>
                    ))}
                  {positions && positions.map((pos, j) => {
                    const posKey = `${i}-${pos.instrumentId}`
                    const isPosOpen = expandedItems[posKey] ?? false
                    return (
                      <div key={j} className="mt-1">
                        <button
                          data-testid={`position-${pos.instrumentId}`}
                          onClick={() => toggleItem(posKey)}
                          className="flex items-center gap-1 text-slate-600 hover:text-slate-800"
                        >
                          {isPosOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                          <span>{pos.instrumentId}</span>
                        </button>
                        {isPosOpen && (
                          <div className="relative ml-4 mt-0.5">
                            <CopyButton text={JSON.stringify(pos, null, 2)} testId={`copy-position-${pos.instrumentId}`} />
                            <pre
                              data-testid={`position-json-${pos.instrumentId}`}
                              className="p-2 pr-8 bg-slate-50 rounded text-[11px] font-mono overflow-x-auto"
                            >
                              {JSON.stringify(pos, null, 2)}
                            </pre>
                          </div>
                        )}
                      </div>
                    )
                  })}
                  {dependencies && dependencies.map((dep, j) => {
                    const depKey = `${i}-dep-${dep.instrumentId}-${dep.dataType}`
                    const isDepOpen = expandedItems[depKey] ?? false
                    return (
                      <div key={j} className="mt-1">
                        <button
                          data-testid={`dependency-${dep.instrumentId}-${dep.dataType}`}
                          onClick={() => toggleItem(depKey)}
                          className="flex items-center gap-1 text-slate-600 hover:text-slate-800"
                        >
                          {isDepOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                          <span>{dep.dataType} — {dep.instrumentId}</span>
                        </button>
                        {isDepOpen && (
                          <div className="relative ml-4 mt-0.5">
                            <CopyButton text={JSON.stringify(dep, null, 2)} testId={`copy-dependency-${dep.instrumentId}-${dep.dataType}`} />
                            <pre
                              data-testid={`dependency-json-${dep.instrumentId}-${dep.dataType}`}
                              className="p-2 pr-8 bg-slate-50 rounded text-[11px] font-mono overflow-x-auto"
                            >
                              {JSON.stringify(dep, null, 2)}
                            </pre>
                          </div>
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
