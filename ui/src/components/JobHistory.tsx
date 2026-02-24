import { useState } from 'react'
import { ChevronDown, ChevronRight, History } from 'lucide-react'
import { useJobHistory } from '../hooks/useJobHistory'
import { JobHistoryTable } from './JobHistoryTable'
import { Card, Badge, Spinner, Button } from './ui'
import { RefreshCw } from 'lucide-react'

interface JobHistoryProps {
  portfolioId: string | null
}

export function JobHistory({ portfolioId }: JobHistoryProps) {
  const [expanded, setExpanded] = useState(true)
  const { runs, expandedJobs, loadingJobIds, loading, error, toggleJob, closeJob, refresh } = useJobHistory(
    expanded ? portfolioId : null,
  )

  return (
    <Card data-testid="job-history">
      <button
        data-testid="job-history-toggle"
        onClick={() => setExpanded((prev) => !prev)}
        className="flex items-center gap-2 w-full text-left"
      >
        {expanded ? <ChevronDown className="h-4 w-4 text-slate-500" /> : <ChevronRight className="h-4 w-4 text-slate-500" />}
        <History className="h-4 w-4 text-slate-500" />
        <span className="text-sm font-semibold text-slate-700">Calculation Jobs</span>
        {expanded && runs.length > 0 && (
          <Badge variant="neutral">{runs.length}</Badge>
        )}
      </button>

      {expanded && (
        <div className="mt-3">
          {loading && !runs.length && (
            <div data-testid="job-history-loading" className="flex items-center gap-2 text-sm text-slate-500 py-2">
              <Spinner size="sm" />
              Loading jobs...
            </div>
          )}

          {error && (
            <p data-testid="job-history-error" className="text-sm text-red-600 py-2">{error}</p>
          )}

          {!error && !(loading && runs.length === 0) && (
            <>
              <div className="flex justify-end mb-2">
                <Button
                  data-testid="job-history-refresh"
                  variant="secondary"
                  size="sm"
                  icon={<RefreshCw className="h-3 w-3" />}
                  onClick={(e) => {
                    e.stopPropagation()
                    refresh()
                  }}
                >
                  Refresh
                </Button>
              </div>
              <JobHistoryTable
                runs={runs}
                expandedJobs={expandedJobs}
                loadingJobIds={loadingJobIds}
                onSelectJob={toggleJob}
                onCloseJob={closeJob}
              />
            </>
          )}
        </div>
      )}
    </Card>
  )
}
