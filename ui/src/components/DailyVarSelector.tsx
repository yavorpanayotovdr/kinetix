import { useState } from 'react'
import { Calendar } from 'lucide-react'
import { Button, Input } from './ui'

interface DailyVarSelectorProps {
  loading: boolean
  onCompare: (targetDate: string, baseDate: string) => void
}

export function DailyVarSelector({ loading, onCompare }: DailyVarSelectorProps) {
  const [targetDate, setTargetDate] = useState(() => new Date().toISOString().split('T')[0])
  const [baseDate, setBaseDate] = useState(() => new Date(Date.now() - 86_400_000).toISOString().split('T')[0])

  return (
    <div data-testid="daily-var-selector" className="flex items-end gap-3 flex-wrap">
      <div>
        <label
          htmlFor="daily-var-base-date"
          className="block text-xs text-slate-500 dark:text-slate-400 mb-1"
        >
          Base Date
        </label>
        <Input
          id="daily-var-base-date"
          data-testid="base-date-input"
          type="date"
          value={baseDate}
          onChange={(e) => setBaseDate(e.target.value)}
          className="w-40"
          aria-label="Base date"
        />
      </div>
      <div>
        <label
          htmlFor="daily-var-target-date"
          className="block text-xs text-slate-500 dark:text-slate-400 mb-1"
        >
          Target Date
        </label>
        <Input
          id="daily-var-target-date"
          data-testid="target-date-input"
          type="date"
          value={targetDate}
          onChange={(e) => setTargetDate(e.target.value)}
          className="w-40"
          aria-label="Target date"
        />
      </div>
      <Button
        data-testid="compare-dates-btn"
        variant="primary"
        onClick={() => onCompare(targetDate, baseDate)}
        loading={loading}
        disabled={loading || !targetDate || !baseDate}
        aria-label="Compare selected dates"
      >
        <Calendar className="h-4 w-4 mr-1" aria-hidden="true" />
        Compare
      </Button>
    </div>
  )
}
