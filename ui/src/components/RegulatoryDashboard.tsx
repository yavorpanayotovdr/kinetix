import type { FrtbResultDto } from '../types'

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
    <div data-testid="regulatory-dashboard" className="bg-white rounded-lg shadow p-4 mb-4">
      <h2 className="text-lg font-semibold text-gray-800 mb-3">Regulatory Reporting — FRTB</h2>

      <div className="flex items-center gap-3 mb-4">
        <button
          data-testid="frtb-calculate-btn"
          onClick={onCalculate}
          disabled={loading}
          className="px-4 py-1.5 bg-indigo-600 text-white rounded text-sm hover:bg-indigo-700 disabled:opacity-50"
        >
          {loading ? 'Calculating...' : 'Calculate FRTB'}
        </button>
        <button
          data-testid="download-csv-btn"
          onClick={onDownloadCsv}
          disabled={!result || loading}
          className="px-4 py-1.5 bg-green-600 text-white rounded text-sm hover:bg-green-700 disabled:opacity-50"
        >
          Download CSV
        </button>
        <button
          data-testid="download-xbrl-btn"
          onClick={onDownloadXbrl}
          disabled={!result || loading}
          className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 disabled:opacity-50"
        >
          Download XBRL
        </button>
      </div>

      {loading && (
        <div data-testid="regulatory-loading" className="text-gray-500 text-sm">
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
          {/* Capital charge summary stats */}
          <div data-testid="capital-summary" className="grid grid-cols-4 gap-3 mb-4">
            <div className="bg-gray-50 rounded p-3 text-center">
              <div className="text-xs text-gray-500">Total Capital</div>
              <div className="text-lg font-bold text-gray-800">{formatCurrency(result.totalCapitalCharge)}</div>
            </div>
            <div className="bg-indigo-50 rounded p-3 text-center">
              <div className="text-xs text-gray-500">SbM</div>
              <div className="text-lg font-bold text-indigo-700">{formatCurrency(result.totalSbmCharge)}</div>
            </div>
            <div className="bg-orange-50 rounded p-3 text-center">
              <div className="text-xs text-gray-500">DRC</div>
              <div className="text-lg font-bold text-orange-700">{formatCurrency(result.netDrc)}</div>
            </div>
            <div className="bg-red-50 rounded p-3 text-center">
              <div className="text-xs text-gray-500">RRAO</div>
              <div className="text-lg font-bold text-red-700">{formatCurrency(result.totalRrao)}</div>
            </div>
          </div>

          {/* Pie chart proportions */}
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

          {/* SbM breakdown table */}
          <h3 className="text-sm font-semibold text-gray-700 mb-2">SbM Breakdown by Risk Class</h3>
          <table data-testid="sbm-breakdown-table" className="w-full text-sm mb-4">
            <thead>
              <tr className="border-b text-left text-gray-600">
                <th className="py-2">Risk Class</th>
                <th className="py-2 text-right">Delta</th>
                <th className="py-2 text-right">Vega</th>
                <th className="py-2 text-right">Curvature</th>
                <th className="py-2 text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              {result.sbmCharges.map((charge) => (
                <tr key={charge.riskClass} className="border-b">
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
    </div>
  )
}
