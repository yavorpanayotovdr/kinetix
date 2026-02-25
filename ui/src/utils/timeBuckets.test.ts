import { describe, expect, it } from 'vitest'
import { bucketJobs } from './timeBuckets'
import type { ValuationJobSummaryDto } from '../types'

function makeJob(startedAt: string, status: string): ValuationJobSummaryDto {
  return {
    jobId: `job-${Math.random()}`,
    portfolioId: 'port-1',
    triggerType: 'ON_DEMAND',
    status,
    startedAt,
    completedAt: null,
    durationMs: null,
    calculationType: null,
    varValue: null,
    expectedShortfall: null,
  }
}

describe('bucketJobs', () => {
  it('returns empty buckets when no jobs are provided', () => {
    const result = bucketJobs([], '2025-01-15T10:00:00Z', '2025-01-15T11:00:00Z')
    expect(result).toHaveLength(12)
    expect(result.every((b) => b.completed === 0 && b.failed === 0 && b.running === 0)).toBe(true)
  })

  it('returns empty array for invalid range', () => {
    const result = bucketJobs([], '2025-01-15T11:00:00Z', '2025-01-15T10:00:00Z')
    expect(result).toEqual([])
  })

  it('creates 5-minute buckets for a 1-hour range', () => {
    const jobs = [makeJob('2025-01-15T10:02:00Z', 'COMPLETED')]
    const result = bucketJobs(jobs, '2025-01-15T10:00:00Z', '2025-01-15T11:00:00Z')

    expect(result).toHaveLength(12) // 60 min / 5 min = 12
    expect(result[0].completed).toBe(1)
    expect(result[0].failed).toBe(0)
    expect(result[0].running).toBe(0)
  })

  it('creates 15-minute buckets for a 6-hour range', () => {
    const jobs = [makeJob('2025-01-15T10:10:00Z', 'FAILED')]
    const result = bucketJobs(jobs, '2025-01-15T10:00:00Z', '2025-01-15T16:00:00Z')

    expect(result).toHaveLength(24) // 360 min / 15 min = 24
    expect(result[0].failed).toBe(1)
  })

  it('creates 1-hour buckets for a 24-hour range', () => {
    const jobs = [makeJob('2025-01-15T10:30:00Z', 'RUNNING')]
    const result = bucketJobs(jobs, '2025-01-15T00:00:00Z', '2025-01-16T00:00:00Z')

    expect(result).toHaveLength(24) // 24h / 1h = 24
    expect(result[10].running).toBe(1)
  })

  it('creates 4-hour buckets for a 7-day range', () => {
    const jobs = [makeJob('2025-01-15T02:00:00Z', 'COMPLETED')]
    const result = bucketJobs(jobs, '2025-01-15T00:00:00Z', '2025-01-22T00:00:00Z')

    expect(result).toHaveLength(42) // 168h / 4h = 42
    expect(result[0].completed).toBe(1)
  })

  it('creates 1-day buckets for ranges longer than 7 days', () => {
    const jobs = [makeJob('2025-01-16T12:00:00Z', 'COMPLETED')]
    const result = bucketJobs(jobs, '2025-01-15T00:00:00Z', '2025-01-29T00:00:00Z')

    expect(result).toHaveLength(14) // 14 days
    expect(result[1].completed).toBe(1)
  })

  it('counts multiple statuses correctly within a bucket', () => {
    const jobs = [
      makeJob('2025-01-15T10:01:00Z', 'COMPLETED'),
      makeJob('2025-01-15T10:02:00Z', 'COMPLETED'),
      makeJob('2025-01-15T10:03:00Z', 'FAILED'),
      makeJob('2025-01-15T10:04:00Z', 'RUNNING'),
    ]
    const result = bucketJobs(jobs, '2025-01-15T10:00:00Z', '2025-01-15T11:00:00Z')

    expect(result[0].completed).toBe(2)
    expect(result[0].failed).toBe(1)
    expect(result[0].running).toBe(1)
  })

  it('places jobs in correct buckets based on startedAt', () => {
    const jobs = [
      makeJob('2025-01-15T10:01:00Z', 'COMPLETED'),
      makeJob('2025-01-15T10:06:00Z', 'FAILED'),
    ]
    const result = bucketJobs(jobs, '2025-01-15T10:00:00Z', '2025-01-15T11:00:00Z')

    expect(result[0].completed).toBe(1)
    expect(result[0].failed).toBe(0)
    expect(result[1].failed).toBe(1)
    expect(result[1].completed).toBe(0)
  })

  it('ignores jobs outside the time range', () => {
    const jobs = [
      makeJob('2025-01-15T09:00:00Z', 'COMPLETED'),
      makeJob('2025-01-15T12:00:00Z', 'COMPLETED'),
    ]
    const result = bucketJobs(jobs, '2025-01-15T10:00:00Z', '2025-01-15T11:00:00Z')

    const total = result.reduce((sum, b) => sum + b.completed + b.failed + b.running, 0)
    expect(total).toBe(0)
  })

  it('sets correct from/to on each bucket', () => {
    const result = bucketJobs([], '2025-01-15T10:00:00Z', '2025-01-15T11:00:00Z')

    expect(result[0].from).toEqual(new Date('2025-01-15T10:00:00Z'))
    expect(result[0].to).toEqual(new Date('2025-01-15T10:05:00Z'))
    expect(result[11].from).toEqual(new Date('2025-01-15T10:55:00Z'))
    expect(result[11].to).toEqual(new Date('2025-01-15T11:00:00Z'))
  })
})
