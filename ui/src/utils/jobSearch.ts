import type { ValuationJobSummaryDto, ValuationJobDetailDto } from '../types'

export function isUuidPrefix(str: string): boolean {
  return /^[0-9a-f]{2,}(-[0-9a-f]*)*$/i.test(str)
}

export function buildSearchableText(
  run: ValuationJobSummaryDto,
  detail?: ValuationJobDetailDto,
): string {
  const parts = [
    run.jobId,
    run.triggerType,
    run.status,
    run.calculationType,
    run.varValue?.toString(),
    run.expectedShortfall?.toString(),
    run.durationMs?.toString(),
  ]

  if (detail) {
    for (const step of detail.steps) {
      parts.push(...Object.values(step.details))
      if (step.error) parts.push(step.error)
    }
    if (detail.error) parts.push(detail.error)
  }

  return parts.filter(Boolean).join(' ').toLowerCase()
}

export function jobMatchesSearch(
  run: ValuationJobSummaryDto,
  term: string,
  detail?: ValuationJobDetailDto,
): boolean {
  const trimmed = term.trim()
  if (!trimmed) return true

  const tokens = trimmed.toLowerCase().split(/\s+/).filter(Boolean)
  const text = buildSearchableText(run, detail)
  return tokens.every((t) => text.includes(t))
}
