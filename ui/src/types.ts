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

export interface PositionRiskDto {
  instrumentId: string
  assetClass: string
  marketValue: string
  delta: string | null
  gamma: string | null
  vega: string | null
  varContribution: string
  esContribution: string
  percentageOfTotal: string
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
  greeks?: GreeksResultDto
  computedOutputs?: string[]
  pvValue?: string
}

export interface VaRCalculationRequestDto {
  calculationType?: string
  confidenceLevel?: string
  timeHorizonDays?: string
  numSimulations?: string
  requestedOutputs?: string[]
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

export interface TimeRange {
  from: string
  to: string
  label: string
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
  pvValue: number | null
}

export interface HypotheticalTradeDto {
  instrumentId: string
  assetClass: string
  side: string
  quantity: string
  priceAmount: string
  priceCurrency: string
}

export interface WhatIfRequestDto {
  hypotheticalTrades: HypotheticalTradeDto[]
  calculationType?: string
  confidenceLevel?: string
}

export interface WhatIfSnapshotDto {
  var: string
  expectedShortfall: string
  greeks: GreeksResultDto | null
  positionRisk: PositionRiskDto[]
}

export interface WhatIfImpactDto {
  varChange: string
  esChange: string
  deltaChange: number
  gammaChange: number
  vegaChange: number
}

export interface WhatIfResponseDto {
  baseVaR: string
  baseExpectedShortfall: string
  baseGreeks: GreeksResultDto | null
  basePositionRisk: PositionRiskDto[]
  hypotheticalVaR: string
  hypotheticalExpectedShortfall: string
  hypotheticalGreeks: GreeksResultDto | null
  hypotheticalPositionRisk: PositionRiskDto[]
  varChange: string
  esChange: string
  calculatedAt: string
}

export interface PositionPnlAttributionDto {
  instrumentId: string
  assetClass: string
  totalPnl: string
  deltaPnl: string
  gammaPnl: string
  vegaPnl: string
  thetaPnl: string
  rhoPnl: string
  unexplainedPnl: string
}

export interface PnlAttributionDto {
  portfolioId: string
  date: string
  totalPnl: string
  deltaPnl: string
  gammaPnl: string
  vegaPnl: string
  thetaPnl: string
  rhoPnl: string
  unexplainedPnl: string
  positionAttributions: PositionPnlAttributionDto[]
  calculatedAt: string
}

export interface SodBaselineStatusDto {
  exists: boolean
  baselineDate: string | null
  snapshotType: string | null
  createdAt: string | null
  sourceJobId: string | null
  calculationType: string | null
}

export interface SodSnapshotResultDto {
  portfolioId: string
  baselineDate: string
  snapshotType: string
  createdAt: string
  snapshotCount: number
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
  pvValue: number | null
  steps: JobStepDto[]
  error: string | null
}
