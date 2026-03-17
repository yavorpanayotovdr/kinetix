import { describe, expect, it } from 'vitest'
import { isUuidPrefix, buildSearchableText, jobMatchesSearch } from './jobSearch'
import type { ValuationJobSummaryDto } from '../types'

const mockJob: ValuationJobSummaryDto = {
  jobId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  portfolioId: 'port-1',
  triggerType: 'ON_DEMAND',
  status: 'COMPLETED',
  startedAt: '2025-01-15T08:00:00Z',
  completedAt: '2025-01-15T08:01:00Z',
  durationMs: 60000,
  calculationType: 'PARAMETRIC',
  confidenceLevel: 'CL_95',
  varValue: 500.0,
  expectedShortfall: 600.0,
  pvValue: null,
  delta: null,
  gamma: null,
  vega: null,
  theta: null,
  rho: null,
  runLabel: null,
  promotedAt: null,
  promotedBy: null, manifestId: null,
}

describe('isUuidPrefix', () => {
  it('returns true for a full UUID string', () => {
    expect(isUuidPrefix('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')).toBe(true)
  })

  it('returns true for a valid UUID prefix of 8 hex characters', () => {
    expect(isUuidPrefix('a1b2c3d4')).toBe(true)
  })

  it('returns true for a short hex prefix', () => {
    expect(isUuidPrefix('a1b2')).toBe(true)
  })

  it('returns false for a keyword like PARAMETRIC', () => {
    expect(isUuidPrefix('PARAMETRIC')).toBe(false)
  })

  it('returns false for an empty string', () => {
    expect(isUuidPrefix('')).toBe(false)
  })

  it('is case-insensitive', () => {
    expect(isUuidPrefix('A1B2C3D4')).toBe(true)
  })

  it('returns false for a string with non-hex characters', () => {
    expect(isUuidPrefix('g1h2i3j4')).toBe(false)
  })

  it('returns true for a UUID prefix with dashes', () => {
    expect(isUuidPrefix('aaaaaaaa-bbbb')).toBe(true)
  })
})

describe('buildSearchableText', () => {
  it('includes jobId, triggerType, status, calculationType, varValue, expectedShortfall, and durationMs', () => {
    const text = buildSearchableText(mockJob)

    expect(text).toContain('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
    expect(text).toContain('on_demand')
    expect(text).toContain('completed')
    expect(text).toContain('parametric')
    expect(text).toContain('500')
    expect(text).toContain('600')
    expect(text).toContain('60000')
  })

  it('returns lowercase text', () => {
    const text = buildSearchableText(mockJob)
    expect(text).toBe(text.toLowerCase())
  })

  it('omits null fields gracefully', () => {
    const jobWithNulls: ValuationJobSummaryDto = {
      ...mockJob,
      calculationType: null,
      varValue: null,
      expectedShortfall: null,
      durationMs: null,
    }
    const text = buildSearchableText(jobWithNulls)

    expect(text).toContain('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
    expect(text).toContain('on_demand')
    expect(text).not.toContain('null')
  })

  it('includes detail phases and errors when detail is provided', () => {
    const detail = {
      ...mockJob,
      phases: [
        {
          name: 'FETCH_POSITIONS',
          status: 'COMPLETED',
          startedAt: '2025-01-15T08:00:00Z',
          completedAt: '2025-01-15T08:00:01Z',
          durationMs: 1000,
          details: { positionCount: '5' },
          error: null,
        },
      ],
      error: 'Some top-level error',
    }
    const text = buildSearchableText(mockJob, detail)

    expect(text).toContain('5')
    expect(text).toContain('some top-level error')
  })
})

describe('jobMatchesSearch', () => {
  it('returns true when search term is empty', () => {
    expect(jobMatchesSearch(mockJob, '')).toBe(true)
  })

  it('matches by calculation type keyword', () => {
    expect(jobMatchesSearch(mockJob, 'PARAMETRIC')).toBe(true)
  })

  it('matches by VaR value', () => {
    expect(jobMatchesSearch(mockJob, '500')).toBe(true)
  })

  it('is case-insensitive for keyword search', () => {
    expect(jobMatchesSearch(mockJob, 'parametric')).toBe(true)
  })

  it('treats spaces as AND — all tokens must match', () => {
    expect(jobMatchesSearch(mockJob, 'PARAMETRIC ON_DEMAND')).toBe(true)
    expect(jobMatchesSearch(mockJob, 'PARAMETRIC HISTORICAL')).toBe(false)
  })

  it('matches by partial job ID in keyword mode', () => {
    expect(jobMatchesSearch(mockJob, 'aaaa')).toBe(true)
  })

  it('returns false when no fields match', () => {
    expect(jobMatchesSearch(mockJob, 'NONEXISTENT')).toBe(false)
  })
})
