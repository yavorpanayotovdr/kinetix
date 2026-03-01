import { useState, useCallback } from 'react'
import { TrendingUp, Download } from 'lucide-react'
import { usePnlAttribution } from '../hooks/usePnlAttribution'
import { useSodBaseline } from '../hooks/useSodBaseline'
import { PnlWaterfallChart } from './PnlWaterfallChart'
import { PnlAttributionTable } from './PnlAttributionTable'
import { SodBaselineIndicator } from './SodBaselineIndicator'
import { JobPickerDialog } from './JobPickerDialog'
import { ConfirmDialog } from './ui/ConfirmDialog'
import { Button } from './ui/Button'
import { Card } from './ui/Card'
import { EmptyState } from './ui/EmptyState'
import { Spinner } from './ui/Spinner'
import { formatTimestamp } from '../utils/format'
import { exportToCsv } from '../utils/exportCsv'
import type { PnlAttributionDto } from '../types'

interface PnlTabProps {
  portfolioId: string | null
}

export function PnlTab({ portfolioId }: PnlTabProps) {
  const { data: pnlData, loading: pnlLoading } = usePnlAttribution(portfolioId)
  const sod = useSodBaseline(portfolioId)
  const [showResetDialog, setShowResetDialog] = useState(false)
  const [showJobPicker, setShowJobPicker] = useState(false)
  const [computedData, setComputedData] = useState<PnlAttributionDto | null>(null)

  const data = computedData ?? pnlData

  const handleComputePnl = useCallback(async () => {
    const result = await sod.computeAttribution()
    if (result) {
      setComputedData(result)
    }
  }, [sod])

  const handleJobSelect = useCallback(async (jobId: string) => {
    setShowJobPicker(false)
    await sod.createSnapshot(jobId)
  }, [sod])

  const handleResetConfirm = useCallback(async () => {
    await sod.resetBaseline()
    setShowResetDialog(false)
    setComputedData(null)
  }, [sod])

  if (pnlLoading && sod.loading) {
    return (
      <div data-testid="pnl-loading" className="flex items-center justify-center py-12">
        <Spinner />
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <SodBaselineIndicator
        status={sod.status}
        loading={sod.loading}
        creating={sod.creating}
        resetting={sod.resetting}
        onCreateSnapshot={() => sod.createSnapshot()}
        onResetBaseline={() => setShowResetDialog(true)}
        onPickFromHistory={() => setShowJobPicker(true)}
      />

      {sod.error && (
        <div data-testid="sod-error" className="text-center py-2">
          <p className="text-red-600 text-sm">{sod.error}</p>
        </div>
      )}

      {sod.status?.exists && !data && (
        <div data-testid="pnl-compute-prompt" className="text-center py-8">
          <EmptyState
            icon={<TrendingUp className="h-10 w-10" />}
            title="SOD baseline set"
            description="Click below to compute P&L attribution using the current baseline."
          />
          <Button
            variant="primary"
            onClick={handleComputePnl}
            loading={sod.computing}
            data-testid="pnl-compute-button"
            className="mt-4"
          >
            Compute P&L Attribution
          </Button>
        </div>
      )}

      {!sod.status?.exists && !data && (
        <div data-testid="pnl-empty">
          <EmptyState
            icon={<TrendingUp className="h-10 w-10" />}
            title="No P&L attribution data"
            description="Set an SOD baseline first, then compute P&L attribution."
          />
        </div>
      )}

      {data && (
        <>
          <div className="flex justify-end gap-2">
            <button
              data-testid="pnl-csv-export"
              onClick={() => {
                const headers = ['Instrument', 'Asset Class', 'Total P&L', 'Delta', 'Gamma', 'Vega', 'Theta', 'Rho', 'Unexplained']
                const rows = data.positionAttributions.map((p) => [
                  p.instrumentId,
                  p.assetClass,
                  p.totalPnl,
                  p.deltaPnl,
                  p.gammaPnl,
                  p.vegaPnl,
                  p.thetaPnl,
                  p.rhoPnl,
                  p.unexplainedPnl,
                ])
                exportToCsv('pnl-attribution.csv', headers, rows)
              }}
              className="inline-flex items-center gap-1 px-2 py-1 text-xs font-medium text-slate-500 border border-slate-300 rounded hover:bg-slate-50 transition-colors"
            >
              <Download className="h-3.5 w-3.5" />
              Export CSV
            </button>
            {sod.status?.exists && (
              <Button
                variant="secondary"
                size="sm"
                onClick={handleComputePnl}
                loading={sod.computing}
                data-testid="pnl-recompute-button"
              >
                Recompute P&L
              </Button>
            )}
          </div>

          <Card header="P&L Waterfall">
            <PnlWaterfallChart data={data} />
          </Card>

          <Card header="Factor Attribution">
            <PnlAttributionTable data={data} />
          </Card>
        </>
      )}

      {portfolioId && (
        <JobPickerDialog
          open={showJobPicker}
          portfolioId={portfolioId}
          onSelect={handleJobSelect}
          onCancel={() => setShowJobPicker(false)}
        />
      )}

      <ConfirmDialog
        open={showResetDialog}
        title="Reset SOD Baseline"
        message={
          <div>
            <p>This will remove the current SOD baseline and its snapshot data. You can immediately set a new baseline using the current market state.</p>
            {sod.status && (sod.status.sourceJobId || sod.status.calculationType || sod.status.createdAt) && (
              <div data-testid="reset-dialog-metadata" className="mt-3 rounded border border-slate-200 bg-slate-50 p-3 text-xs">
                <p className="font-medium text-slate-700 mb-1">Current baseline</p>
                {sod.status.sourceJobId && (
                  <p className="text-slate-600">
                    Job ID: <span className="font-mono">{sod.status.sourceJobId.slice(0, 8)}</span>
                  </p>
                )}
                {sod.status.calculationType && (
                  <p className="text-slate-600">
                    Type: {sod.status.calculationType}
                  </p>
                )}
                {sod.status.createdAt && (
                  <p className="text-slate-600">
                    Created: {formatTimestamp(sod.status.createdAt)}
                  </p>
                )}
              </div>
            )}
          </div>
        }
        confirmLabel="Reset Baseline"
        cancelLabel="Cancel"
        variant="danger"
        loading={sod.resetting}
        onConfirm={handleResetConfirm}
        onCancel={() => setShowResetDialog(false)}
      />
    </div>
  )
}
