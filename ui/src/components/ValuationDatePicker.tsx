import { useCallback, useState } from 'react'

function todayUTC(): string {
  return new Date().toISOString().slice(0, 10)
}

function daysAgoUTC(days: number): string {
  const d = new Date()
  d.setUTCDate(d.getUTCDate() - days)
  return d.toISOString().slice(0, 10)
}

interface ValuationDatePickerProps {
  value: string | null
  onChange: (date: string | null) => void
}

export function ValuationDatePicker({ value, onChange }: ValuationDatePickerProps) {
  const [showDateInput, setShowDateInput] = useState(false)

  const handlePreset = useCallback((date: string | null) => {
    setShowDateInput(false)
    onChange(date)
  }, [onChange])

  const handleCustomDate = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const date = e.target.value
    if (date) {
      onChange(date)
    }
  }, [onChange])

  const isSelected = (date: string | null) => value === date

  const yesterday = daysAgoUTC(1)
  const tMinus2 = daysAgoUTC(2)

  return (
    <div role="group" aria-label="Valuation date" className="inline-flex items-center gap-1">
      <button
        data-testid="vdate-today"
        onClick={() => handlePreset(null)}
        className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
          isSelected(null) ? 'bg-primary-100 text-primary-700 dark:bg-primary-900 dark:text-primary-300' : 'text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-700'
        }`}
      >
        Today
      </button>
      <button
        data-testid="vdate-yesterday"
        onClick={() => handlePreset(yesterday)}
        className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
          isSelected(yesterday) ? 'bg-primary-100 text-primary-700 dark:bg-primary-900 dark:text-primary-300' : 'text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-700'
        }`}
      >
        Yesterday
      </button>
      <button
        data-testid="vdate-t2"
        onClick={() => handlePreset(tMinus2)}
        className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
          isSelected(tMinus2) ? 'bg-primary-100 text-primary-700 dark:bg-primary-900 dark:text-primary-300' : 'text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-700'
        }`}
      >
        T-2
      </button>
      <button
        data-testid="vdate-custom"
        onClick={() => setShowDateInput(!showDateInput)}
        className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
          value !== null && value !== yesterday && value !== tMinus2 ? 'bg-primary-100 text-primary-700 dark:bg-primary-900 dark:text-primary-300' : 'text-slate-500 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-700'
        }`}
      >
        Pick date…
      </button>
      {showDateInput && (
        <input
          data-testid="vdate-input"
          type="date"
          max={todayUTC()}
          value={value ?? ''}
          onChange={handleCustomDate}
          className="ml-1 px-2 py-0.5 text-xs border border-slate-300 rounded-md dark:bg-slate-800 dark:border-slate-600 dark:text-slate-200"
        />
      )}
    </div>
  )
}
