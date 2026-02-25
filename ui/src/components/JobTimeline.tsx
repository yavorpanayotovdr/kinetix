import { useState } from 'react'
import { ChevronDown, ChevronRight, Copy, Check, Search } from 'lucide-react'
import type { JobStepDto } from '../types'

interface JobTimelineProps {
  steps: JobStepDto[]
  search?: string
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
      : status === 'PARTIAL'
        ? 'bg-amber-500'
        : status === 'FAILED'
          ? 'bg-red-500'
          : 'bg-slate-300'
  return <span data-testid={`step-dot-${status}`} className={`inline-block h-3 w-3 rounded-full ${color} shrink-0`} />
}

function effectiveStepStatus(step: JobStepDto): string {
  if (step.status !== 'COMPLETED') return step.status
  const raw = step.details['marketDataItems']
  if (!raw) return step.status
  try {
    const items = JSON.parse(raw) as { status: string }[]
    if (items.some((i) => i.status === 'MISSING')) return 'PARTIAL'
  } catch { /* ignore */ }
  return step.status
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
      className="absolute top-1.5 left-1.5 p-1 rounded hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
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

interface MarketDataItem {
  instrumentId: string
  dataType: string
  status: string
  [key: string]: string
}

function stepMatchesSearch(step: JobStepDto, term: string): boolean {
  const tokens = term.toLowerCase().split(/\s+/).filter(Boolean)
  const parts = [STEP_LABELS[step.name] ?? step.name, ...Object.values(step.details)]
  if (step.error) parts.push(step.error)
  const text = parts.join(' ').toLowerCase()
  return tokens.every((t) => text.includes(t))
}

function itemMatchesFilter(item: Record<string, string>, term: string): boolean {
  const tokens = term.toLowerCase().split(/\s+/).filter(Boolean)
  const text = Object.values(item).join(' ').toLowerCase()
  return tokens.every((t) => text.includes(t))
}

function FilterInput({ testId, value, onChange }: { testId: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="relative mb-1.5">
      <Search className="absolute left-1.5 top-1/2 -translate-y-1/2 h-3 w-3 text-slate-400" />
      <input
        data-testid={testId}
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Filter…"
        className="w-full pl-6 pr-2 py-1 text-xs rounded border border-slate-200 bg-white focus:outline-none focus:border-primary-300"
      />
    </div>
  )
}

export function JobTimeline({ steps, search = '' }: JobTimelineProps) {
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})
  const [expandedItems, setExpandedItems] = useState<Record<string, boolean>>({})
  const [itemFilters, setItemFilters] = useState<Record<string, string>>({})

  const isSearchActive = search.trim().length > 0

  const toggle = (index: number) => {
    setExpanded((prev) => ({ ...prev, [index]: !prev[index] }))
  }

  const toggleItem = (key: string) => {
    setExpandedItems((prev) => ({ ...prev, [key]: !prev[key] }))
  }

  const setItemFilter = (stepName: string, value: string) => {
    setItemFilters((prev) => ({ ...prev, [stepName]: value }))
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

  const parseMarketDataItems = (details: Record<string, string>): MarketDataItem[] | null => {
    const raw = details['marketDataItems']
    if (!raw) return null
    try {
      return JSON.parse(raw) as MarketDataItem[]
    } catch {
      return null
    }
  }

  const filteredSteps = isSearchActive
    ? steps.filter((step) => stepMatchesSearch(step, search))
    : steps

  if (isSearchActive && filteredSteps.length === 0) {
    return (
      <div data-testid="job-timeline" className="relative pl-4">
        <p className="text-xs text-slate-400 py-2">No steps match your search.</p>
      </div>
    )
  }

  return (
    <div data-testid="job-timeline" className="relative pl-4">
      <div className="absolute left-[5px] top-2 bottom-2 w-px bg-slate-300" />
      {filteredSteps.map((step) => {
        const stepIndex = steps.indexOf(step)
        const isOpen = isSearchActive || (expanded[stepIndex] ?? false)
        const hasDetails = Object.keys(step.details).length > 0
        const filter = itemFilters[step.name] ?? ''

        return (
          <div key={stepIndex} data-testid={`job-step-${step.name}`} className="relative mb-3 last:mb-0">
            <div
              data-testid={`toggle-${step.name}`}
              onClick={hasDetails ? () => toggle(stepIndex) : undefined}
              className={`flex items-center gap-2${hasDetails ? ' cursor-pointer hover:bg-slate-50 -mx-1 px-1 rounded' : ''}`}
            >
              <StatusDotInline status={effectiveStepStatus(step)} />
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
              const marketDataItems = parseMarketDataItems(step.details)
              const hasItems = (positions && positions.length > 0) || (dependencies && dependencies.length > 0) || (marketDataItems && marketDataItems.length > 0)
              const activeFilter = filter || (isSearchActive ? search : '')
              const filteredPositions = positions && activeFilter
                ? positions.filter((p) => itemMatchesFilter(p as unknown as Record<string, string>, activeFilter))
                : positions
              const filteredDependencies = dependencies && activeFilter
                ? dependencies.filter((d) => itemMatchesFilter(d as unknown as Record<string, string>, activeFilter))
                : dependencies
              const filteredMarketDataItems = marketDataItems && activeFilter
                ? marketDataItems.filter((m) => itemMatchesFilter(m as unknown as Record<string, string>, activeFilter))
                : marketDataItems
              return (
                <div data-testid={`details-${step.name}`} className="ml-5 mt-1 text-xs text-slate-500 space-y-0.5">
                  {Object.entries(step.details)
                    .filter(([key]) => key !== 'positions' && key !== 'dependencies' && key !== 'marketDataItems')
                    .map(([key, value]) => (
                      <div key={key}>
                        <span className="font-medium">{key}:</span> {value}
                      </div>
                    ))}
                  {hasItems && (
                    <FilterInput
                      testId={`filter-${step.name}`}
                      value={filter}
                      onChange={(v) => setItemFilter(step.name, v)}
                    />
                  )}
                  {filteredPositions && filteredPositions.map((pos, j) => {
                    const posKey = `${stepIndex}-${pos.instrumentId}`
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
                              className="p-2 pl-8 bg-slate-50 rounded text-[11px] font-mono overflow-x-auto"
                            >
                              {JSON.stringify(pos, null, 2)}
                            </pre>
                          </div>
                        )}
                      </div>
                    )
                  })}
                  {filteredDependencies && filteredDependencies.map((dep, j) => {
                    const depKey = `${stepIndex}-dep-${dep.instrumentId}-${dep.dataType}`
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
                              className="p-2 pl-8 bg-slate-50 rounded text-[11px] font-mono overflow-x-auto"
                            >
                              {JSON.stringify(dep, null, 2)}
                            </pre>
                          </div>
                        )}
                      </div>
                    )
                  })}
                  {filteredMarketDataItems && filteredMarketDataItems.map((item, j) => {
                    const mdKey = `${stepIndex}-md-${item.instrumentId}-${item.dataType}`
                    const isMdOpen = expandedItems[mdKey] ?? false
                    const isFetched = item.status === 'FETCHED'
                    const jsonBg = isFetched ? 'bg-slate-50' : 'bg-red-50'
                    return (
                      <div key={j} className="mt-1">
                        <button
                          data-testid={`market-data-${item.instrumentId}-${item.dataType}`}
                          onClick={() => toggleItem(mdKey)}
                          className="flex items-center gap-1 text-slate-600 hover:text-slate-800"
                        >
                          {isMdOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                          {!isFetched && <span data-testid={`market-data-dot-${item.status}`} className="inline-block h-2 w-2 rounded-full bg-red-500 shrink-0" />}
                          <span>{item.dataType} — {item.instrumentId}</span>
                        </button>
                        {isMdOpen && (
                          <div className="relative ml-4 mt-0.5">
                            <CopyButton text={JSON.stringify(item, null, 2)} testId={`copy-market-data-${item.instrumentId}-${item.dataType}`} />
                            <pre
                              data-testid={`market-data-json-${item.instrumentId}-${item.dataType}`}
                              className={`p-2 pl-8 ${jsonBg} rounded text-[11px] font-mono overflow-x-auto`}
                            >
                              {JSON.stringify(item, null, 2)}
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
