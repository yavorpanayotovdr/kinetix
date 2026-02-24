export interface MoneyDto {
  amount: string
  currency: string
}

export interface PositionDto {
  portfolioId: string
  instrumentId: string
  assetClass: string
  quantity: string
  averageCost: MoneyDto
  marketPrice: MoneyDto
  marketValue: MoneyDto
  unrealizedPnl: MoneyDto
}

export interface PortfolioDto {
  portfolioId: string
}

export interface PriceUpdateMessage {
  type: 'price'
  instrumentId: string
  priceAmount: string
  priceCurrency: string
  timestamp: string
  source: string
}

export interface SubscribeMessage {
  type: 'subscribe'
  instrumentIds: string[]
}

export interface UnsubscribeMessage {
  type: 'unsubscribe'
  instrumentIds: string[]
}

export type ClientMessage = SubscribeMessage | UnsubscribeMessage

export interface ComponentBreakdownDto {
  assetClass: string
  varContribution: string
  percentageOfTotal: string
}

export interface VaRResultDto {
  portfolioId: string
  calculationType: string
  confidenceLevel: string
  varValue: string
  expectedShortfall: string
  componentBreakdown: ComponentBreakdownDto[]
  calculatedAt: string
}

export interface VaRCalculationRequestDto {
  calculationType?: string
  confidenceLevel?: string
  timeHorizonDays?: string
  numSimulations?: string
}

export interface AssetClassImpactDto {
  assetClass: string
  baseExposure: string
  stressedExposure: string
  pnlImpact: string
}

export interface StressTestResultDto {
  scenarioName: string
  baseVar: string
  stressedVar: string
  pnlImpact: string
  assetClassImpacts: AssetClassImpactDto[]
  calculatedAt: string
}

export interface GreekValuesDto {
  assetClass: string
  delta: string
  gamma: string
  vega: string
}

export interface GreeksResultDto {
  portfolioId: string
  assetClassGreeks: GreekValuesDto[]
  theta: string
  rho: string
  calculatedAt: string
}

export interface RiskClassChargeDto {
  riskClass: string
  deltaCharge: string
  vegaCharge: string
  curvatureCharge: string
  totalCharge: string
}

export interface FrtbResultDto {
  portfolioId: string
  sbmCharges: RiskClassChargeDto[]
  totalSbmCharge: string
  grossJtd: string
  hedgeBenefit: string
  netDrc: string
  exoticNotional: string
  otherNotional: string
  totalRrao: string
  totalCapitalCharge: string
  calculatedAt: string
}

export interface ReportResultDto {
  portfolioId: string
  format: string
  content: string
  generatedAt: string
}

export interface AlertRuleDto {
  id: string
  name: string
  type: string
  threshold: number
  operator: string
  severity: string
  channels: string[]
  enabled: boolean
}

export interface AlertEventDto {
  id: string
  ruleId: string
  ruleName: string
  type: string
  severity: string
  message: string
  currentValue: number
  threshold: number
  portfolioId: string
  triggeredAt: string
}

export interface CreateAlertRuleRequestDto {
  name: string
  type: string
  threshold: number
  operator: string
  severity: string
  channels: string[]
}

export interface JobStepDto {
  name: string
  status: string
  startedAt: string
  completedAt: string | null
  durationMs: number | null
  details: Record<string, string>
  error: string | null
}

export interface ValuationJobSummaryDto {
  jobId: string
  portfolioId: string
  triggerType: string
  status: string
  startedAt: string
  completedAt: string | null
  durationMs: number | null
  calculationType: string | null
  varValue: number | null
  expectedShortfall: number | null
}

export interface ValuationJobDetailDto {
  jobId: string
  portfolioId: string
  triggerType: string
  status: string
  startedAt: string
  completedAt: string | null
  durationMs: number | null
  calculationType: string | null
  confidenceLevel: string | null
  varValue: number | null
  expectedShortfall: number | null
  steps: JobStepDto[]
  error: string | null
}
