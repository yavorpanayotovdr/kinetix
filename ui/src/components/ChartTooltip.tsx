import { useCallback, useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import type { TimeBucket } from '../utils/timeBuckets'
import { formatChartTime } from '../utils/format'
import { useClickOutside } from '../hooks/useClickOutside'

interface ChartTooltipProps {
  bucket: TimeBucket | null
  visible: boolean
  rangeDays: number
  pinned: boolean
  onClose: () => void
  onPin: () => void
}

const MAX_COLLAPSED = 5

export function ChartTooltip({ bucket, visible, rangeDays, pinned, onClose, onPin }: ChartTooltipProps) {
  const [expanded, setExpanded] = useState(false)
  const [search, setSearch] = useState('')
  const [prevBucket, setPrevBucket] = useState<TimeBucket | null>(null)
  const tooltipRef = useRef<HTMLDivElement>(null)

  // Reset expanded/search when the bucket changes (React-approved derive-from-props pattern)
  if (bucket !== prevBucket) {
    setPrevBucket(bucket)
    setExpanded(false)
    setSearch('')
  }

  // Escape key dismisses pinned tooltip
  useEffect(() => {
    if (!pinned) return
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [pinned, onClose])

  // Click outside dismisses pinned tooltip
  const handleClickOutside = useCallback(() => {
    if (pinned) onClose()
  }, [pinned, onClose])
  useClickOutside(tooltipRef, handleClickOutside)

  if (!visible || !bucket) return <div className="h-28" />

  const fromLabel = formatChartTime(bucket.from, rangeDays)
  const toLabel = formatChartTime(bucket.to, rangeDays)
  const jobIds = bucket.jobIds
  const hasMany = jobIds.length > MAX_COLLAPSED

  const filteredIds = pinned && search
    ? jobIds.filter((id) => id.toLowerCase().includes(search.toLowerCase()))
    : jobIds

  const showAll = pinned && (expanded || search)
  const displayedIds = showAll ? filteredIds : filteredIds.slice(0, MAX_COLLAPSED)
  const remaining = filteredIds.length - MAX_COLLAPSED

  return (
    <div
      ref={tooltipRef}
      data-testid="chart-tooltip"
      className="h-28 overflow-hidden bg-slate-800 text-white text-xs rounded shadow-lg px-3 py-2"
    >
      {pinned && (
        <button
          data-testid="tooltip-close"
          onClick={onClose}
          className="absolute top-1 right-1 text-slate-400 hover:text-white"
        >
          <X className="h-3 w-3" />
        </button>
      )}

      <div className="font-medium mb-1">{fromLabel} – {toLabel}</div>
      <div className="flex gap-3">
        <span className="text-green-400">Completed: {bucket.completed}</span>
        <span className="text-red-400">Failed: {bucket.failed}</span>
        <span className="text-indigo-400">Running: {bucket.running}</span>
      </div>

      {jobIds.length > 0 && (
        <div className="mt-1 border-t border-slate-600 pt-1">
          {pinned && hasMany && (
            <input
              data-testid="tooltip-search"
              type="text"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                if (!expanded) setExpanded(true)
              }}
              placeholder="Search job IDs..."
              className="w-full mb-1 px-1 py-0.5 bg-slate-700 text-slate-200 rounded text-xs outline-none placeholder-slate-500"
            />
          )}

          <div className={showAll ? 'max-h-40 overflow-y-auto' : ''}>
            {displayedIds.map((id) => (
              <div key={id} className="font-mono text-slate-300">
                {pinned ? id : id.slice(0, 8)}
              </div>
            ))}
          </div>

          {!showAll && remaining > 0 && (
            pinned ? (
              <button
                onClick={() => setExpanded(true)}
                className="text-slate-400 hover:text-slate-200 cursor-pointer"
              >
                and {remaining} more
              </button>
            ) : (
              <button
                onClick={() => { onPin(); setExpanded(true) }}
                className="text-slate-400 hover:text-slate-200 cursor-pointer"
              >
                and {remaining} more
              </button>
            )
          )}
        </div>
      )}
    </div>
  )
}
