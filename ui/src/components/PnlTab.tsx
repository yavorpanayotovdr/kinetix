import { useState, useCallback } from 'react'
import { TrendingUp } from 'lucide-react'
import { usePnlAttribution } from '../hooks/usePnlAttribution'
import { useSodBaseline } from '../hooks/useSodBaseline'
import { PnlWaterfallChart } from './PnlWaterfallChart'
import { PnlAttributionTable } from './PnlAttributionTable'
import { SodBaselineIndicator } from './SodBaselineIndicator'
import { ConfirmDialog } from './ui/ConfirmDialog'
import { Button } from './ui/Button'
import { Card } from './ui/Card'
import { EmptyState } from './ui/EmptyState'
import { Spinner } from './ui/Spinner'
import type { PnlAttributionDto } from '../types'

interface PnlTabProps {
  portfolioId: string | null
}

export function PnlTab({ portfolioId }: PnlTabProps) {
  const { data: pnlData, loading: pnlLoading, error: pnlError } = usePnlAttribution(portfolioId)
  const sod = useSodBaseline(portfolioId)
  const [showResetDialog, setShowResetDialog] = useState(false)
  const [computedData, setComputedData] = useState<PnlAttributionDto | null>(null)

  const data = computedData ?? pnlData

  const handleComputePnl = useCallback(async () => {
    const result = await sod.computeAttribution()
    if (result) {
      setComputedData(result)
    }
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
        onCreateSnapshot={sod.createSnapshot}
        onResetBaseline={() => setShowResetDialog(true)}
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
          {sod.status?.exists && (
            <div className="flex justify-end">
              <Button
                variant="secondary"
                size="sm"
                onClick={handleComputePnl}
                loading={sod.computing}
                data-testid="pnl-recompute-button"
              >
                Recompute P&L
              </Button>
            </div>
          )}

          <Card header="P&L Waterfall">
            <PnlWaterfallChart data={data} />
          </Card>

          <Card header="Factor Attribution">
            <PnlAttributionTable data={data} />
          </Card>
        </>
      )}

      <ConfirmDialog
        open={showResetDialog}
        title="Reset SOD Baseline"
        message="This will remove the current SOD baseline. You will need to set a new baseline to compute P&L attribution. Are you sure?"
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
