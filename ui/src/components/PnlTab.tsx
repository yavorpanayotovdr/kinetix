import { TrendingUp } from 'lucide-react'
import { usePnlAttribution } from '../hooks/usePnlAttribution'
import { PnlWaterfallChart } from './PnlWaterfallChart'
import { PnlAttributionTable } from './PnlAttributionTable'
import { Card } from './ui/Card'
import { EmptyState } from './ui/EmptyState'
import { Spinner } from './ui/Spinner'

interface PnlTabProps {
  portfolioId: string | null
}

export function PnlTab({ portfolioId }: PnlTabProps) {
  const { data, loading, error } = usePnlAttribution(portfolioId)

  if (loading) {
    return (
      <div data-testid="pnl-loading" className="flex items-center justify-center py-12">
        <Spinner />
      </div>
    )
  }

  if (error) {
    return (
      <div data-testid="pnl-error" className="text-center py-12">
        <p className="text-red-600 text-sm">{error}</p>
      </div>
    )
  }

  if (!data) {
    return (
      <div data-testid="pnl-empty">
        <EmptyState
          icon={<TrendingUp className="h-10 w-10" />}
          title="No P&L attribution data"
          description="P&L attribution will appear here once calculated."
        />
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <Card header="P&L Waterfall">
        <PnlWaterfallChart data={data} />
      </Card>

      <Card header="Factor Attribution">
        <PnlAttributionTable data={data} />
      </Card>
    </div>
  )
}
