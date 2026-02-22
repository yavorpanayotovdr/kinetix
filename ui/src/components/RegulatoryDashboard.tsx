import { Calculator, Download, FileText } from 'lucide-react'
import type { FrtbResultDto } from '../types'
import { Card, Button, Spinner } from './ui'

interface RegulatoryDashboardProps {
  result: FrtbResultDto | null
  loading: boolean
  error: string | null
  onCalculate: () => void
  onDownloadCsv: () => void
  onDownloadXbrl: () => void
}

function formatCurrency(value: string | number): string {
  const num = typeof value === 'string' ? Number(value) : value
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(num)
}

export function RegulatoryDashboard({
  result,
  loading,
  error,
  onCalculate,
  onDownloadCsv,
  onDownloadXbrl,
}: RegulatoryDashboardProps) {
  const totalSbm = result ? Number(result.totalSbmCharge) : 0
  const totalDrc = result ? Number(result.netDrc) : 0
  const totalRrao = result ? Number(result.totalRrao) : 0
  const grandTotal = totalSbm + totalDrc + totalRrao

  return (
    <Card
      data-testid="regulatory-dashboard"
      header={<span className="flex items-center gap-1.5"><FileText className="h-4 w-4" />Regulatory Reporting — FRTB</span>}
    >
      <div className="flex items-center gap-3 mb-4">
        <Button
          data-testid="frtb-calculate-btn"
          variant="primary"
          icon={<Calculator className="h-3.5 w-3.5" />}
          onClick={onCalculate}
          loading={loading}
        >
          {loading ? 'Calculating...' : 'Calculate FRTB'}
        </Button>
        <Button
          data-testid="download-csv-btn"
          variant="success"
          icon={<Download className="h-3.5 w-3.5" />}
          onClick={onDownloadCsv}
          disabled={!result || loading}
        >
          Download CSV
        </Button>
        <Button
          data-testid="download-xbrl-btn"
          variant="secondary"
          icon={<FileText className="h-3.5 w-3.5" />}
          onClick={onDownloadXbrl}
          disabled={!result || loading}
        >
          Download XBRL
        </Button>
      </div>

      {loading && (
        <div data-testid="regulatory-loading" className="flex items-center gap-2 text-slate-500 text-sm">
          <Spinner size="sm" />
          Calculating FRTB capital requirements...
        </div>
      )}

      {error && (
        <div data-testid="regulatory-error" className="text-red-600 text-sm">
          {error}
        </div>
      )}

      {result && !loading && (
        <div data-testid="regulatory-results">
          <div data-testid="capital-summary" className="grid grid-cols-4 gap-3 mb-4">
            <div className="bg-slate-50 rounded-lg p-3 text-center">
              <div className="text-xs text-slate-500">Total Capital</div>
              <div className="text-lg font-bold text-slate-800">{formatCurrency(result.totalCapitalCharge)}</div>
            </div>
            <div className="bg-indigo-50 rounded-lg p-3 text-center">
              <div className="text-xs text-slate-500">SbM</div>
              <div className="text-lg font-bold text-indigo-700">{formatCurrency(result.totalSbmCharge)}</div>
            </div>
            <div className="bg-orange-50 rounded-lg p-3 text-center">
              <div className="text-xs text-slate-500">DRC</div>
              <div className="text-lg font-bold text-orange-700">{formatCurrency(result.netDrc)}</div>
            </div>
            <div className="bg-red-50 rounded-lg p-3 text-center">
              <div className="text-xs text-slate-500">RRAO</div>
              <div className="text-lg font-bold text-red-700">{formatCurrency(result.totalRrao)}</div>
            </div>
          </div>

          <div data-testid="charge-proportions" className="flex gap-2 mb-4 text-xs">
            {grandTotal > 0 && (
              <>
                <div
                  className="bg-indigo-500 text-white rounded px-2 py-1"
                  style={{ flex: totalSbm / grandTotal }}
                >
                  SbM {((totalSbm / grandTotal) * 100).toFixed(0)}%
                </div>
                <div
                  className="bg-orange-500 text-white rounded px-2 py-1"
                  style={{ flex: totalDrc / grandTotal }}
                >
                  DRC {((totalDrc / grandTotal) * 100).toFixed(0)}%
                </div>
                <div
                  className="bg-red-500 text-white rounded px-2 py-1"
                  style={{ flex: totalRrao / grandTotal }}
                >
                  RRAO {((totalRrao / grandTotal) * 100).toFixed(0)}%
                </div>
              </>
            )}
          </div>

          <h3 className="text-sm font-semibold text-slate-700 mb-2">SbM Breakdown by Risk Class</h3>
          <table data-testid="sbm-breakdown-table" className="w-full text-sm mb-4">
            <thead>
              <tr className="border-b text-left text-slate-600">
                <th className="py-2">Risk Class</th>
                <th className="py-2 text-right">Delta</th>
                <th className="py-2 text-right">Vega</th>
                <th className="py-2 text-right">Curvature</th>
                <th className="py-2 text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              {result.sbmCharges
                .filter((charge) => Number(charge.totalCharge) !== 0)
                .map((charge) => (
                <tr key={charge.riskClass} className="border-b hover:bg-slate-50 transition-colors">
                  <td className="py-1.5">{charge.riskClass}</td>
                  <td className="py-1.5 text-right">{formatCurrency(charge.deltaCharge)}</td>
                  <td className="py-1.5 text-right">{formatCurrency(charge.vegaCharge)}</td>
                  <td className="py-1.5 text-right">{formatCurrency(charge.curvatureCharge)}</td>
                  <td className="py-1.5 text-right font-medium">{formatCurrency(charge.totalCharge)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
