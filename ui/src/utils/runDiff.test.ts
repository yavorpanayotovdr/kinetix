import { describe, it, expect } from 'vitest'
import { computeRunDiff, filterByThreshold } from './runDiff'
import type { RunSnapshotDto, PositionDiffDto } from '../types'

function makeSnapshot(overrides: Partial<RunSnapshotDto> = {}): RunSnapshotDto {
  return {
    jobId: 'job-1',
    label: 'Test',
    valuationDate: '2025-01-15',
    calcType: 'PARAMETRIC',
    confLevel: 'CL_95',
    varValue: '5000',
    es: '6250',
    pv: '100000',
    delta: '0.5',
    gamma: '0.01',
    vega: '100',
    theta: '-50',
    rho: '25',
    componentBreakdown: [],
    positionRisk: [],
    modelVersion: null,
    parameters: {},
    calculatedAt: '2025-01-15T10:00:00Z',
    ...overrides,
  }
}

describe('computeRunDiff', () => {
  it('equal runs produce zero diff for all metrics', () => {
    const snap = makeSnapshot()
    const diff = computeRunDiff(snap, snap)

    expect(diff.varChange).toBe(0)
    expect(diff.varChangePercent).toBe(0)
    expect(diff.esChange).toBe(0)
    expect(diff.esChangePercent).toBe(0)
    expect(diff.pvChange).toBe(0)
    expect(diff.deltaChange).toBe(0)
    expect(diff.gammaChange).toBe(0)
    expect(diff.vegaChange).toBe(0)
    expect(diff.thetaChange).toBe(0)
    expect(diff.rhoChange).toBe(0)
    expect(diff.positionDiffs).toHaveLength(0)
    expect(diff.componentDiffs).toHaveLength(0)
  })

  it('VaR increase calculated correctly', () => {
    const base = makeSnapshot({ varValue: '5000' })
    const target = makeSnapshot({ varValue: '7000' })
    const diff = computeRunDiff(base, target)

    expect(diff.varChange).toBe(2000)
    expect(diff.varChangePercent).toBeCloseTo(40)
  })

  it('VaR decrease calculated correctly', () => {
    const base = makeSnapshot({ varValue: '5000' })
    const target = makeSnapshot({ varValue: '3000' })
    const diff = computeRunDiff(base, target)

    expect(diff.varChange).toBe(-2000)
    expect(diff.varChangePercent).toBeCloseTo(-40)
  })

  it('new position flagged when only in target', () => {
    const base = makeSnapshot({ positionRisk: [] })
    const target = makeSnapshot({
      positionRisk: [
        {
          instrumentId: 'SPY',
          assetClass: 'EQUITY',
          marketValue: '10000',
          delta: '0.8',
          gamma: null,
          vega: null,
          theta: null,
          rho: null,
          varContribution: '500',
          esContribution: '625',
          percentageOfTotal: '10',
        },
      ],
    })
    const diff = computeRunDiff(base, target)

    expect(diff.positionDiffs).toHaveLength(1)
    expect(diff.positionDiffs[0].changeType).toBe('NEW')
    expect(diff.positionDiffs[0].instrumentId).toBe('SPY')
  })

  it('removed position flagged when only in base', () => {
    const base = makeSnapshot({
      positionRisk: [
        {
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          marketValue: '5000',
          delta: '0.6',
          gamma: null,
          vega: null,
          theta: null,
          rho: null,
          varContribution: '250',
          esContribution: '310',
          percentageOfTotal: '5',
        },
      ],
    })
    const target = makeSnapshot({ positionRisk: [] })
    const diff = computeRunDiff(base, target)

    expect(diff.positionDiffs).toHaveLength(1)
    expect(diff.positionDiffs[0].changeType).toBe('REMOVED')
    expect(diff.positionDiffs[0].instrumentId).toBe('AAPL')
  })

  it('modified position detected when market value differs', () => {
    const position = {
      instrumentId: 'MSFT',
      assetClass: 'EQUITY',
      marketValue: '8000',
      delta: '0.7',
      gamma: null,
      vega: null,
      theta: null,
      rho: null,
      varContribution: '400',
      esContribution: '500',
      percentageOfTotal: '8',
    }
    const base = makeSnapshot({ positionRisk: [position] })
    const target = makeSnapshot({
      positionRisk: [{ ...position, marketValue: '9000' }],
    })
    const diff = computeRunDiff(base, target)

    expect(diff.positionDiffs).toHaveLength(1)
    expect(diff.positionDiffs[0].changeType).toBe('MODIFIED')
    expect(diff.positionDiffs[0].marketValueChange).toBe('1000')
  })

  it('threshold filtering removes small changes', () => {
    const diffs: PositionDiffDto[] = [
      {
        instrumentId: 'A',
        assetClass: 'EQUITY',
        changeType: 'MODIFIED',
        baseMarketValue: '1000',
        targetMarketValue: '1200',
        marketValueChange: '200',
        baseVarContribution: '100',
        targetVarContribution: '120',
        varContributionChange: '20',
        baseDelta: null,
        targetDelta: null,
        baseGamma: null,
        targetGamma: null,
        baseVega: null,
        targetVega: null,
      },
      {
        instrumentId: 'B',
        assetClass: 'EQUITY',
        changeType: 'MODIFIED',
        baseMarketValue: '5000',
        targetMarketValue: '5600',
        marketValueChange: '600',
        baseVarContribution: '250',
        targetVarContribution: '280',
        varContributionChange: '30',
        baseDelta: null,
        targetDelta: null,
        baseGamma: null,
        targetGamma: null,
        baseVega: null,
        targetVega: null,
      },
    ]
    const filtered = filterByThreshold(diffs, 500)

    expect(filtered).toHaveLength(1)
    expect(filtered[0].instrumentId).toBe('B')
  })

  it('threshold filtering keeps large changes', () => {
    const diffs: PositionDiffDto[] = [
      {
        instrumentId: 'C',
        assetClass: 'EQUITY',
        changeType: 'MODIFIED',
        baseMarketValue: '2000',
        targetMarketValue: '2100',
        marketValueChange: '100',
        baseVarContribution: '200',
        targetVarContribution: '210',
        varContributionChange: '10',
        baseDelta: null,
        targetDelta: null,
        baseGamma: null,
        targetGamma: null,
        baseVega: null,
        targetVega: null,
      },
    ]
    const filtered = filterByThreshold(diffs, 100)

    expect(filtered).toHaveLength(1)
    expect(filtered[0].instrumentId).toBe('C')
  })

  it('handles null numeric values gracefully', () => {
    const base = makeSnapshot({ varValue: null, es: null, pv: null, delta: null, gamma: null, vega: null, theta: null, rho: null })
    const target = makeSnapshot({ varValue: null, es: null, pv: null, delta: null, gamma: null, vega: null, theta: null, rho: null })
    const diff = computeRunDiff(base, target)

    expect(diff.varChange).toBe(0)
    expect(diff.varChangePercent).toBeNull()
    expect(diff.esChange).toBe(0)
    expect(diff.pvChange).toBe(0)
    expect(diff.deltaChange).toBe(0)
  })
})
