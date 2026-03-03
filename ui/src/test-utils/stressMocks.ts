import type { AssetClassImpactDto, PositionStressImpactDto, StressTestResultDto } from '../types'

export function makeAssetClassImpact(overrides?: Partial<AssetClassImpactDto>): AssetClassImpactDto {
  return {
    assetClass: 'EQUITY',
    baseExposure: '1000000.00',
    stressedExposure: '600000.00',
    pnlImpact: '-400000.00',
    ...overrides,
  }
}

export function makePositionImpact(overrides?: Partial<PositionStressImpactDto>): PositionStressImpactDto {
  return {
    instrumentId: 'AAPL',
    assetClass: 'EQUITY',
    baseMarketValue: '500000.00',
    stressedMarketValue: '300000.00',
    pnlImpact: '-200000.00',
    percentageOfTotal: '40.00',
    ...overrides,
  }
}

export function makeStressResult(overrides?: Partial<StressTestResultDto>): StressTestResultDto {
  return {
    scenarioName: 'GFC_2008',
    baseVar: '100000.00',
    stressedVar: '300000.00',
    pnlImpact: '-500000.00',
    assetClassImpacts: [
      makeAssetClassImpact({ assetClass: 'EQUITY', pnlImpact: '-400000.00' }),
      makeAssetClassImpact({ assetClass: 'COMMODITY', baseExposure: '500000.00', stressedExposure: '350000.00', pnlImpact: '-150000.00' }),
    ],
    positionImpacts: [
      makePositionImpact({ instrumentId: 'AAPL', pnlImpact: '-200000.00', percentageOfTotal: '40.00' }),
      makePositionImpact({ instrumentId: 'GOLD', assetClass: 'COMMODITY', baseMarketValue: '500000.00', stressedMarketValue: '350000.00', pnlImpact: '-150000.00', percentageOfTotal: '30.00' }),
    ],
    calculatedAt: '2026-03-03T08:00:00Z',
    ...overrides,
  }
}

export const SCENARIOS_LIST = ['GFC_2008', 'COVID_2020', 'TAPER_TANTRUM_2013', 'EURO_CRISIS_2011']

export const ALL_STRESS_RESULTS: StressTestResultDto[] = [
  makeStressResult({ scenarioName: 'GFC_2008', pnlImpact: '-500000.00', baseVar: '100000.00', stressedVar: '300000.00' }),
  makeStressResult({ scenarioName: 'COVID_2020', pnlImpact: '-350000.00', baseVar: '100000.00', stressedVar: '250000.00' }),
  makeStressResult({ scenarioName: 'TAPER_TANTRUM_2013', pnlImpact: '-100000.00', baseVar: '100000.00', stressedVar: '150000.00' }),
  makeStressResult({ scenarioName: 'EURO_CRISIS_2011', pnlImpact: '-200000.00', baseVar: '100000.00', stressedVar: '200000.00' }),
]
