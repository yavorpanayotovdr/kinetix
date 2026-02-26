import { useRegulatory } from '../hooks/useRegulatory'
import { RegulatoryDashboard } from './RegulatoryDashboard'

interface RegulatoryTabProps {
  portfolioId: string | null
}

export function RegulatoryTab({ portfolioId }: RegulatoryTabProps) {
  const regulatory = useRegulatory(portfolioId)

  return (
    <RegulatoryDashboard
      result={regulatory.result}
      loading={regulatory.loading}
      error={regulatory.error}
      onCalculate={regulatory.calculate}
      onDownloadCsv={regulatory.downloadCsv}
      onDownloadXbrl={regulatory.downloadXbrl}
    />
  )
}
