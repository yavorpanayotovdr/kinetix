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
