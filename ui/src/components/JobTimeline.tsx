import { useState } from 'react'
import { ChevronDown, ChevronRight, Copy, Check, Search } from 'lucide-react'
import type { JobPhaseDto } from '../types'
import { formatDuration } from '../utils/format'
import { PHASE_LABELS } from '../constants/phaseLabels'

interface JobTimelineProps {
  phases: JobPhaseDto[]
  search?: string
}

function StatusDotInline({ status }: { status: string }) {
  const color =
    status === 'COMPLETED'
      ? 'bg-green-500'
      : status === 'PARTIAL'
        ? 'bg-amber-500'
        : status === 'FAILED'
          ? 'bg-red-500'
          : 'bg-blue-400 animate-pulse'
  return <span data-testid={`phase-status-dot-${status}`} className={`inline-block h-3 w-3 rounded-full ${color} shrink-0`} />
}

function effectivePhaseStatus(phase: JobPhaseDto): string {
  if (phase.status !== 'COMPLETED') return phase.status
  const raw = phase.details['marketDataItems']
  if (!raw) return phase.status
  try {
    const items = JSON.parse(raw) as { status: string }[]
    if (items.some((i) => i.status === 'MISSING')) return 'PARTIAL'
  } catch { /* ignore */ }
  return phase.status
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

interface PositionBreakdownItem {
  instrumentId: string
  assetClass: string
  [key: string]: string
}

interface MarketDataItem {
  instrumentId: string
  dataType: string
  status: string
  issue?: Record<string, string>
  [key: string]: string | Record<string, string> | undefined
}

function phaseMatchesSearch(phase: JobPhaseDto, term: string): boolean {
  const tokens = term.toLowerCase().split(/\s+/).filter(Boolean)
  const parts = [PHASE_LABELS[phase.name] ?? phase.name, ...Object.values(phase.details)]
  if (phase.error) parts.push(phase.error)
  const text = parts.join(' ').toLowerCase()
  return tokens.every((t) => text.includes(t))
}

function itemMatchesFilter(item: Record<string, unknown>, term: string): boolean {
  const tokens = term.toLowerCase().split(/\s+/).filter(Boolean)
  const text = Object.values(item).map((v) => typeof v === 'object' ? JSON.stringify(v) : String(v)).join(' ').toLowerCase()
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

export function JobTimeline({ phases, search = '' }: JobTimelineProps) {
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

  const setItemFilter = (phaseName: string, value: string) => {
    setItemFilters((prev) => ({ ...prev, [phaseName]: value }))
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

  const parseDependenciesByPosition = (details: Record<string, string>): Record<string, DependencyItem[]> | null => {
    const raw = details['dependenciesByPosition']
    if (!raw) return null
    try {
      return JSON.parse(raw) as Record<string, DependencyItem[]>
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

  const parsePositionBreakdown = (details: Record<string, string>): PositionBreakdownItem[] | null => {
    const raw = details['positionBreakdown']
    if (!raw) return null
    try {
      return JSON.parse(raw) as PositionBreakdownItem[]
    } catch {
      return null
    }
  }

  const filteredPhases = isSearchActive
    ? phases.filter((phase) => phaseMatchesSearch(phase, search))
    : phases

  if (isSearchActive && filteredPhases.length === 0) {
    return (
      <div data-testid="job-timeline" className="relative pl-4">
        <p className="text-xs text-slate-400 py-2">No phases match your search.</p>
      </div>
    )
  }

  return (
    <div data-testid="job-timeline" className="relative pl-4">
      <div className="absolute left-[5px] top-2 bottom-2 w-px bg-slate-300" />
      {filteredPhases.map((phase) => {
        const phaseIndex = phases.indexOf(phase)
        const isOpen = isSearchActive || (expanded[phaseIndex] ?? false)
        const hasDetails = Object.keys(phase.details).length > 0
        const filter = itemFilters[phase.name] ?? ''

        return (
          <div key={phaseIndex} data-testid={`job-phase-${phase.name}`} className="relative mb-3 last:mb-0">
            <div
              data-testid={`toggle-${phase.name}`}
              onClick={hasDetails ? () => toggle(phaseIndex) : undefined}
              className={`flex items-center gap-2${hasDetails ? ' cursor-pointer hover:bg-slate-50 -mx-1 px-1 rounded' : ''}`}
            >
              <StatusDotInline status={effectivePhaseStatus(phase)} />
              {hasDetails && (
                isOpen ? <ChevronDown className="h-3 w-3 text-slate-400" /> : <ChevronRight className="h-3 w-3 text-slate-400" />
              )}
              <span className="text-sm font-medium text-slate-700">
                {PHASE_LABELS[phase.name] ?? phase.name}
              </span>
              {phase.durationMs != null && (
                <span className="text-xs text-slate-400">{formatDuration(phase.durationMs)}</span>
              )}
            </div>
            {phase.error && (
              <p className="ml-5 text-xs text-red-600 mt-0.5">{phase.error}</p>
            )}
            {isOpen && hasDetails && (() => {
              const positions = parsePositions(phase.details)
              const depsByPosition = parseDependenciesByPosition(phase.details)
              const dependencies = parseDependencies(phase.details)
              const marketDataItems = parseMarketDataItems(phase.details)
              const positionBreakdown = parsePositionBreakdown(phase.details)
              const hasItems = (positions && positions.length > 0) || (dependencies && dependencies.length > 0) || (marketDataItems && marketDataItems.length > 0) || (positionBreakdown && positionBreakdown.length > 0)
              const activeFilter = filter || (isSearchActive ? search : '')
              const filteredPositions = positions && activeFilter
                ? positions.filter((p) => itemMatchesFilter(p as unknown as Record<string, unknown>, activeFilter))
                : positions
              const filteredDependencies = dependencies && activeFilter
                ? dependencies.filter((d) => itemMatchesFilter(d as unknown as Record<string, unknown>, activeFilter))
                : dependencies
              const filteredMarketDataItems = marketDataItems && activeFilter
                ? marketDataItems.filter((m) => itemMatchesFilter(m as unknown as Record<string, unknown>, activeFilter))
                : marketDataItems
              const filteredPositionBreakdown = positionBreakdown && activeFilter
                ? positionBreakdown.filter((b) => itemMatchesFilter(b as unknown as Record<string, unknown>, activeFilter))
                : positionBreakdown
              return (
                <div data-testid={`details-${phase.name}`} className="ml-5 mt-1 text-xs text-slate-500 space-y-0.5">
                  {(() => {
                    const scalarEntries = Object.entries(phase.details)
                      .filter(([key]) => key !== 'positions' && key !== 'dependencies' && key !== 'marketDataItems' && key !== 'dependenciesByPosition' && key !== 'positionBreakdown' && key !== 'dataTypes')
                    if (phase.name === 'VALUATION' && scalarEntries.length > 0) {
                      const DISPLAY_KEYS: Record<string, string> = { varValue: 'var', pvValue: 'pv' }
                      const obj = Object.fromEntries(scalarEntries.map(([k, v]) => [DISPLAY_KEYS[k] ?? k, v]))
                      const json = JSON.stringify(obj, null, 2)
                      return (
                        <div className="relative">
                          <CopyButton text={json} testId="copy-valuation-result" />
                          <pre
                            data-testid="valuation-result-json"
                            className="p-2 pl-8 bg-slate-50 rounded text-[11px] font-mono overflow-x-auto"
                          >
                            {json}
                          </pre>
                        </div>
                      )
                    }
                    return scalarEntries.map(([key, value]) => (
                      <div key={key}>
                        <span className="font-medium">{key}:</span> {value}
                      </div>
                    ))
                  })()}
                  {hasItems && (
                    <FilterInput
                      testId={`filter-${phase.name}`}
                      value={filter}
                      onChange={(v) => setItemFilter(phase.name, v)}
                    />
                  )}
                  {filteredPositions && filteredPositions.map((pos, j) => {
                    const posKey = `${phaseIndex}-${pos.instrumentId}`
                    const isPosOpen = expandedItems[posKey] ?? false
                    const posDeps = depsByPosition?.[pos.instrumentId]
                    const posDepsKey = `${phaseIndex}-posdeps-${pos.instrumentId}`
                    const isPosDepsOpen = expandedItems[posDepsKey] ?? false
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
                          <div className="ml-4 mt-0.5 space-y-1">
                            <div className="relative">
                              <CopyButton text={JSON.stringify(pos, null, 2)} testId={`copy-position-${pos.instrumentId}`} />
                              <pre
                                data-testid={`position-json-${pos.instrumentId}`}
                                className="p-2 pl-8 bg-slate-50 rounded text-[11px] font-mono overflow-x-auto"
                              >
                                {JSON.stringify(pos, null, 2)}
                              </pre>
                            </div>
                            {posDeps && posDeps.length > 0 && (
                              <div>
                                <div className="flex items-center gap-1">
                                  <button
                                    data-testid={`pos-deps-toggle-${pos.instrumentId}`}
                                    onClick={() => toggleItem(posDepsKey)}
                                    className="flex items-center gap-1 text-slate-600 hover:text-slate-800"
                                  >
                                    {isPosDepsOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                                    <span>Dependencies ({posDeps.length})</span>
                                  </button>
                                  {isPosDepsOpen && (
                                    <button
                                      data-testid={`copy-pos-deps-${pos.instrumentId}`}
                                      onClick={() => {
                                        navigator.clipboard.writeText(JSON.stringify(posDeps, null, 2))
                                      }}
                                      className="p-0.5 rounded hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
                                      title="Copy JSON"
                                    >
                                      <Copy className="h-3 w-3" />
                                    </button>
                                  )}
                                </div>
                                {isPosDepsOpen && (
                                  <div className="ml-4 mt-0.5 space-y-1">
                                    {posDeps.map((dep, k) => {
                                      const posDepKey = `${phaseIndex}-posdep-${pos.instrumentId}-${dep.dataType}`
                                      const isPosDepOpen = expandedItems[posDepKey] ?? false
                                      return (
                                        <div key={k}>
                                          <button
                                            data-testid={`pos-dep-${pos.instrumentId}-${dep.dataType}`}
                                            onClick={() => toggleItem(posDepKey)}
                                            className="flex items-center gap-1 text-slate-500 hover:text-slate-700"
                                          >
                                            {isPosDepOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                                            <span>{dep.dataType}</span>
                                          </button>
                                          {isPosDepOpen && (
                                            <div className="relative ml-4 mt-0.5">
                                              <CopyButton text={JSON.stringify(dep, null, 2)} testId={`copy-pos-dep-${pos.instrumentId}-${dep.dataType}`} />
                                              <pre
                                                data-testid={`pos-dep-json-${pos.instrumentId}-${dep.dataType}`}
                                                className="p-2 pl-8 bg-slate-50 rounded text-[11px] font-mono overflow-x-auto"
                                              >
                                                {JSON.stringify(dep, null, 2)}
                                              </pre>
                                            </div>
                                          )}
                                        </div>
                                      )
                                    })}
                                  </div>
                                )}
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    )
                  })}
                  {filteredDependencies && filteredDependencies.map((dep, j) => {
                    const depKey = `${phaseIndex}-dep-${dep.instrumentId}-${dep.dataType}`
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
                    const mdKey = `${phaseIndex}-md-${item.instrumentId}-${item.dataType}`
                    const isMdOpen = expandedItems[mdKey] ?? false
                    const isFetched = item.status === 'FETCHED'
                    const jsonBg = isFetched ? 'bg-slate-50' : 'bg-red-50'
                    const { issue: parsedIssue, ...resourceFields } = item
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
                          <div className="ml-4 mt-0.5">
                            {parsedIssue ? (() => {
                              const fullJson = JSON.stringify({ ...resourceFields, issue: parsedIssue }, null, 2)
                              const lines = fullJson.split('\n')
                              const issueLineIndex = lines.findIndex(l => l.trimStart().startsWith('"issue":'))
                              const topLines = lines.slice(0, issueLineIndex).join('\n')
                              const bottomLines = lines.slice(issueLineIndex).join('\n')
                              return (
                                <>
                                  <div className="relative">
                                    <CopyButton text={JSON.stringify(resourceFields, null, 2)} testId={`copy-market-data-${item.instrumentId}-${item.dataType}`} />
                                    <pre
                                      data-testid={`market-data-json-${item.instrumentId}-${item.dataType}`}
                                      className={`p-2 pl-8 ${jsonBg} rounded-t text-[11px] font-mono overflow-x-auto`}
                                    >
                                      {topLines}
                                    </pre>
                                  </div>
                                  <div className="relative">
                                    <CopyButton text={JSON.stringify(parsedIssue, null, 2)} testId={`copy-issue-${item.instrumentId}-${item.dataType}`} />
                                    <pre
                                      data-testid={`issue-json-${item.instrumentId}-${item.dataType}`}
                                      className="p-2 pl-8 bg-red-100 border border-red-300 text-red-900 font-mono text-[11px] rounded-b overflow-x-auto"
                                    >
                                      {bottomLines}
                                    </pre>
                                  </div>
                                </>
                              )
                            })() : (
                              <div className="relative">
                                <CopyButton text={JSON.stringify(resourceFields, null, 2)} testId={`copy-market-data-${item.instrumentId}-${item.dataType}`} />
                                <pre
                                  data-testid={`market-data-json-${item.instrumentId}-${item.dataType}`}
                                  className={`p-2 pl-8 ${jsonBg} rounded text-[11px] font-mono overflow-x-auto`}
                                >
                                  {JSON.stringify(resourceFields, null, 2)}
                                </pre>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    )
                  })}
                  {filteredPositionBreakdown && filteredPositionBreakdown.map((item, j) => {
                    const bKey = `${phaseIndex}-varb-${item.instrumentId}`
                    const isBOpen = expandedItems[bKey] ?? false
                    const BREAKDOWN_DISPLAY_KEYS: Record<string, string> = { marketValue: 'pv' }
                    const displayItem = Object.fromEntries(
                      Object.entries(item).map(([k, v]) => [BREAKDOWN_DISPLAY_KEYS[k] ?? k, v]),
                    )
                    const displayJson = JSON.stringify(displayItem, null, 2)
                    return (
                      <div key={j} className="mt-1">
                        <button
                          data-testid={`var-breakdown-${item.instrumentId}`}
                          onClick={() => toggleItem(bKey)}
                          className="flex items-center gap-1 text-slate-600 hover:text-slate-800"
                        >
                          {isBOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                          <span>{item.instrumentId}</span>
                        </button>
                        {isBOpen && (
                          <div className="relative ml-4 mt-0.5">
                            <CopyButton text={displayJson} testId={`copy-var-breakdown-${item.instrumentId}`} />
                            <pre
                              data-testid={`var-breakdown-json-${item.instrumentId}`}
                              className="p-2 pl-8 bg-slate-50 rounded text-[11px] font-mono overflow-x-auto"
                            >
                              {displayJson}
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
