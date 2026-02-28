import { useEffect, useCallback, useState } from 'react'
import { Button } from './ui/Button'
import { Spinner } from './ui/Spinner'
import { fetchValuationJobs } from '../api/jobHistory'
import { formatTimestamp } from '../utils/format'
import type { ValuationJobSummaryDto } from '../types'

interface JobPickerDialogProps {
  open: boolean
  portfolioId: string
  onSelect: (jobId: string) => void
  onCancel: () => void
}

export function JobPickerDialog({
  open,
  portfolioId,
  onSelect,
  onCancel,
}: JobPickerDialogProps) {
  const [jobs, setJobs] = useState<ValuationJobSummaryDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel()
    },
    [onCancel],
  )

  useEffect(() => {
    if (open) {
      document.addEventListener('keydown', handleKeyDown)
      return () => document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open, handleKeyDown])

  useEffect(() => {
    if (!open) return
    setLoading(true)
    setError(null)
    fetchValuationJobs(portfolioId, 20)
      .then(({ items }) => {
        setJobs(items.filter((j) => j.status === 'COMPLETED'))
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : String(err))
      })
      .finally(() => setLoading(false))
  }, [open, portfolioId])

  if (!open) return null

  return (
    <div
      data-testid="job-picker-overlay"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      onClick={onCancel}
    >
      <div
        data-testid="job-picker-dialog"
        className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-semibold text-slate-800">Select VaR Calculation</h3>
        <p className="mt-1 text-sm text-slate-600">
          Choose a completed VaR calculation to use as the SOD baseline.
        </p>

        <div className="mt-4 max-h-80 overflow-y-auto">
          {loading && (
            <div data-testid="job-picker-loading" className="flex justify-center py-8">
              <Spinner />
            </div>
          )}

          {error && (
            <p data-testid="job-picker-error" className="text-red-600 text-sm text-center py-4">
              {error}
            </p>
          )}

          {!loading && !error && jobs.length === 0 && (
            <p data-testid="job-picker-empty" className="text-slate-500 text-sm text-center py-8">
              No completed VaR calculations found.
            </p>
          )}

          {!loading && !error && jobs.length > 0 && (
            <table data-testid="job-picker-table" className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                  <th className="pb-2 pr-3">Job ID</th>
                  <th className="pb-2 pr-3">Type</th>
                  <th className="pb-2 pr-3">VaR</th>
                  <th className="pb-2 pr-3">Time</th>
                  <th className="pb-2 pr-3">Duration</th>
                  <th className="pb-2"></th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.jobId} data-testid="job-picker-row" className="border-b border-slate-100">
                    <td className="py-2 pr-3 font-mono text-xs">{job.jobId.slice(0, 8)}</td>
                    <td className="py-2 pr-3">{job.calculationType ?? '—'}</td>
                    <td className="py-2 pr-3">{job.varValue != null ? job.varValue.toFixed(2) : '—'}</td>
                    <td className="py-2 pr-3 text-xs">{formatTimestamp(job.startedAt)}</td>
                    <td className="py-2 pr-3 text-xs">{job.durationMs != null ? `${job.durationMs}ms` : '—'}</td>
                    <td className="py-2">
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => onSelect(job.jobId)}
                        data-testid="job-picker-select"
                      >
                        Use as Baseline
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="mt-4 flex justify-end">
          <Button
            variant="secondary"
            onClick={onCancel}
            data-testid="job-picker-cancel"
          >
            Cancel
          </Button>
        </div>
      </div>
    </div>
  )
}
