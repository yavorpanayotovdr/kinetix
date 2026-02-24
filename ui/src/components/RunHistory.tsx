import { useState } from 'react'
import { ChevronDown, ChevronRight, History } from 'lucide-react'
import { useRunHistory } from '../hooks/useRunHistory'
import { RunHistoryTable } from './RunHistoryTable'
import { Card, Badge, Spinner, Button } from './ui'
import { RefreshCw } from 'lucide-react'

interface RunHistoryProps {
  portfolioId: string | null
}

export function RunHistory({ portfolioId }: RunHistoryProps) {
  const [expanded, setExpanded] = useState(true)
  const { runs, selectedRunId, selectedRun, detailLoading, loading, error, selectRun, clearSelection, refresh } = useRunHistory(
    expanded ? portfolioId : null,
  )

  return (
    <Card data-testid="run-history">
      <button
        data-testid="run-history-toggle"
        onClick={() => setExpanded((prev) => !prev)}
        className="flex items-center gap-2 w-full text-left"
      >
        {expanded ? <ChevronDown className="h-4 w-4 text-slate-500" /> : <ChevronRight className="h-4 w-4 text-slate-500" />}
        <History className="h-4 w-4 text-slate-500" />
        <span className="text-sm font-semibold text-slate-700">Calculation Runs</span>
        {expanded && runs.length > 0 && (
          <Badge variant="neutral">{runs.length}</Badge>
        )}
      </button>

      {expanded && (
        <div className="mt-3">
          {loading && !runs.length && (
            <div data-testid="run-history-loading" className="flex items-center gap-2 text-sm text-slate-500 py-2">
              <Spinner size="sm" />
              Loading runs...
            </div>
          )}

          {error && (
            <p data-testid="run-history-error" className="text-sm text-red-600 py-2">{error}</p>
          )}

          {!error && !(loading && runs.length === 0) && (
            <>
              <div className="flex justify-end mb-2">
                <Button
                  data-testid="run-history-refresh"
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
              <RunHistoryTable
                runs={runs}
                selectedRunId={selectedRunId}
                selectedRun={selectedRun}
                detailLoading={detailLoading}
                onSelectRun={selectRun}
                onClearSelection={clearSelection}
              />
            </>
          )}
        </div>
      )}
    </Card>
  )
}
