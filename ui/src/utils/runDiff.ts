import type { RunSnapshotDto, PositionDiffDto, ComponentDiffDto, PositionChangeType, PositionRiskDto, ComponentBreakdownDto } from '../types'

export interface RunDiff {
  varChange: number
  varChangePercent: number | null
  esChange: number
  esChangePercent: number | null
  pvChange: number
  deltaChange: number
  gammaChange: number
  vegaChange: number
  thetaChange: number
  rhoChange: number
  positionDiffs: PositionDiffDto[]
  componentDiffs: ComponentDiffDto[]
}

function num(val: string | null | undefined): number {
  if (val == null) return 0
  const n = Number(val)
  return isNaN(n) ? 0 : n
}

function pctChange(base: number, change: number): number | null {
  if (base === 0) return null
  return (change / base) * 100
}

export function computeRunDiff(base: RunSnapshotDto, target: RunSnapshotDto): RunDiff {
  const baseVar = num(base.varValue)
  const targetVar = num(target.varValue)
  const varChange = targetVar - baseVar

  const baseEs = num(base.es)
  const targetEs = num(target.es)
  const esChange = targetEs - baseEs

  return {
    varChange,
    varChangePercent: pctChange(baseVar, varChange),
    esChange,
    esChangePercent: pctChange(baseEs, esChange),
    pvChange: num(target.pv) - num(base.pv),
    deltaChange: num(target.delta) - num(base.delta),
    gammaChange: num(target.gamma) - num(base.gamma),
    vegaChange: num(target.vega) - num(base.vega),
    thetaChange: num(target.theta) - num(base.theta),
    rhoChange: num(target.rho) - num(base.rho),
    positionDiffs: matchPositions(base.positionRisk, target.positionRisk),
    componentDiffs: matchComponents(base.componentBreakdown, target.componentBreakdown),
  }
}

function matchPositions(base: PositionRiskDto[], target: PositionRiskDto[]): PositionDiffDto[] {
  const allIds = new Set([...base.map(p => p.instrumentId), ...target.map(p => p.instrumentId)])
  return Array.from(allIds).map(id => {
    const b = base.find(p => p.instrumentId === id)
    const t = target.find(p => p.instrumentId === id)

    const baseMV = num(b?.marketValue)
    const targetMV = num(t?.marketValue)
    const baseVarC = num(b?.varContribution)
    const targetVarC = num(t?.varContribution)

    let changeType: PositionChangeType
    if (!b) changeType = 'NEW'
    else if (!t) changeType = 'REMOVED'
    else if (baseMV !== targetMV || baseVarC !== targetVarC) changeType = 'MODIFIED'
    else changeType = 'UNCHANGED'

    return {
      instrumentId: id,
      assetClass: (t ?? b)!.assetClass,
      changeType,
      baseMarketValue: String(baseMV),
      targetMarketValue: String(targetMV),
      marketValueChange: String(targetMV - baseMV),
      baseVarContribution: String(baseVarC),
      targetVarContribution: String(targetVarC),
      varContributionChange: String(targetVarC - baseVarC),
      baseDelta: b?.delta ?? null,
      targetDelta: t?.delta ?? null,
      baseGamma: b?.gamma ?? null,
      targetGamma: t?.gamma ?? null,
      baseVega: b?.vega ?? null,
      targetVega: t?.vega ?? null,
    }
  })
}

function matchComponents(base: ComponentBreakdownDto[], target: ComponentBreakdownDto[]): ComponentDiffDto[] {
  const allClasses = new Set([...base.map(c => c.assetClass), ...target.map(c => c.assetClass)])
  return Array.from(allClasses).map(ac => {
    const b = base.find(c => c.assetClass === ac)
    const t = target.find(c => c.assetClass === ac)
    const baseC = num(b?.varContribution)
    const targetC = num(t?.varContribution)
    const change = targetC - baseC
    return {
      assetClass: ac,
      baseContribution: String(baseC),
      targetContribution: String(targetC),
      change: String(change),
      changePercent: baseC !== 0 ? String((change / baseC) * 100) : null,
    }
  })
}

export function filterByThreshold(diffs: PositionDiffDto[], threshold: number): PositionDiffDto[] {
  return diffs.filter(d => Math.abs(Number(d.marketValueChange)) >= threshold)
}
