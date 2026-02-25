import { useState } from 'react'
import { Button } from './ui'
import type { TimeRange } from '../types'

interface TimeRangeSelectorProps {
  value: TimeRange
  onChange: (range: TimeRange) => void
}

interface Preset {
  label: string
  getRange: () => { from: string; to: string }
}

const PRESETS: Preset[] = [
  {
    label: 'Last 1h',
    getRange: () => {
      const now = new Date()
      return { from: new Date(now.getTime() - 60 * 60 * 1000).toISOString(), to: now.toISOString() }
    },
  },
  {
    label: 'Last 24h',
    getRange: () => {
      const now = new Date()
      return { from: new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString(), to: now.toISOString() }
    },
  },
  {
    label: 'Last 7d',
    getRange: () => {
      const now = new Date()
      return { from: new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString(), to: now.toISOString() }
    },
  },
  {
    label: 'Today',
    getRange: () => {
      const now = new Date()
      const start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      return { from: start.toISOString(), to: now.toISOString() }
    },
  },
]

function toDatetimeLocal(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fromDatetimeLocal(value: string): string {
  return new Date(value).toISOString()
}

export function TimeRangeSelector({ value, onChange }: TimeRangeSelectorProps) {
  const [showCustom, setShowCustom] = useState(false)
  const [customFrom, setCustomFrom] = useState(() => toDatetimeLocal(value.from))
  const [customTo, setCustomTo] = useState(() => toDatetimeLocal(value.to))

  const handlePreset = (preset: Preset) => {
    setShowCustom(false)
    const { from, to } = preset.getRange()
    onChange({ from, to, label: preset.label })
  }

  const handleCustomToggle = () => {
    if (!showCustom) {
      setCustomFrom(toDatetimeLocal(value.from))
      setCustomTo(toDatetimeLocal(value.to))
    }
    setShowCustom((prev) => !prev)
  }

  const handleApply = () => {
    if (customFrom && customTo) {
      onChange({ from: fromDatetimeLocal(customFrom), to: fromDatetimeLocal(customTo), label: 'Custom' })
    }
  }

  return (
    <div data-testid="time-range-selector" className="flex flex-col gap-2 mb-3">
      <div className="flex items-center gap-2 flex-wrap">
        {PRESETS.map((preset) => (
          <Button
            key={preset.label}
            data-testid={`time-preset-${preset.label}`}
            variant={value.label === preset.label && !showCustom ? 'primary' : 'secondary'}
            size="sm"
            onClick={(e) => {
              e.stopPropagation()
              handlePreset(preset)
            }}
          >
            {preset.label}
          </Button>
        ))}
        <Button
          data-testid="time-preset-Custom"
          variant={showCustom ? 'primary' : 'secondary'}
          size="sm"
          onClick={(e) => {
            e.stopPropagation()
            handleCustomToggle()
          }}
        >
          Custom
        </Button>
        <span className="text-xs text-slate-500 ml-2" data-testid="time-range-label">
          {value.label}
        </span>
      </div>
      {showCustom && (
        <div data-testid="custom-range-inputs" className="flex items-center gap-2">
          <label className="text-xs text-slate-500">From</label>
          <input
            data-testid="custom-from"
            type="datetime-local"
            value={customFrom}
            onChange={(e) => setCustomFrom(e.target.value)}
            className="text-xs border border-slate-200 rounded px-2 py-1"
            onClick={(e) => e.stopPropagation()}
          />
          <label className="text-xs text-slate-500">To</label>
          <input
            data-testid="custom-to"
            type="datetime-local"
            value={customTo}
            onChange={(e) => setCustomTo(e.target.value)}
            className="text-xs border border-slate-200 rounded px-2 py-1"
            onClick={(e) => e.stopPropagation()}
          />
          <Button
            data-testid="custom-apply"
            variant="primary"
            size="sm"
            onClick={(e) => {
              e.stopPropagation()
              handleApply()
            }}
          >
            Apply
          </Button>
        </div>
      )}
    </div>
  )
}
