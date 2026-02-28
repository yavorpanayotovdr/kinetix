import { Zap, ExternalLink } from 'lucide-react'
import type { StressTestResultDto } from '../types'
import { Card, Button } from './ui'

interface StressSummaryCardProps {
  result: StressTestResultDto | null
  loading: boolean
  onRun: () => void
  onViewDetails: () => void
}

function formatCurrency(value: string | number): string {
  const num = typeof value === 'string' ? Number(value) : value
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(num)
}

export function StressSummaryCard({ result, loading, onRun, onViewDetails }: StressSummaryCardProps) {
  const pnlValue = result ? Number(result.pnlImpact) : 0
  const isLoss = pnlValue < 0

  return (
    <Card
      data-testid="stress-summary-card"
      header={
        <div className="flex items-center justify-between w-full">
          <span className="flex items-center gap-1.5">
            <Zap className="h-4 w-4" />
            Stress Test Summary
          </span>
          <Button
            data-testid="stress-summary-run-btn"
            variant="danger"
            size="sm"
            icon={<Zap className="h-3.5 w-3.5" />}
            onClick={onRun}
            loading={loading}
          >
            {loading ? 'Running...' : 'Run Stress Tests'}
          </Button>
        </div>
      }
    >
      {!result && !loading && (
        <p className="text-sm text-slate-500">No stress test results yet. Run a stress test to see the summary.</p>
      )}

      {result && !loading && (
        <>
          <table data-testid="stress-summary-table" className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-slate-600">
                <th className="py-2">Scenario</th>
                <th className="py-2 text-right">Base VaR</th>
                <th className="py-2 text-right">Stressed VaR</th>
                <th className="py-2 text-right">P&L Impact</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b hover:bg-slate-50 transition-colors">
                <td className="py-1.5 font-medium">{result.scenarioName.replace(/_/g, ' ')}</td>
                <td className="py-1.5 text-right">{formatCurrency(result.baseVar)}</td>
                <td className="py-1.5 text-right font-medium">{formatCurrency(result.stressedVar)}</td>
                <td
                  data-testid="stress-summary-pnl-impact"
                  className={`py-1.5 text-right font-medium ${isLoss ? 'text-red-600' : ''}`}
                >
                  {formatCurrency(result.pnlImpact)}
                </td>
              </tr>
            </tbody>
          </table>

          <div className="mt-3 flex justify-end">
            <button
              data-testid="stress-summary-view-details"
              onClick={onViewDetails}
              className="inline-flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800"
            >
              View Details
              <ExternalLink className="h-3 w-3" />
            </button>
          </div>
        </>
      )}
    </Card>
  )
}
