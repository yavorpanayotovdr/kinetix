import type { ValuationJobSummaryDto } from '../types'

export interface BucketJob {
  jobId: string
  startedAt: Date
  completedAt: Date | null
  status: string
}

export interface TimeBucket {
  from: Date
  to: Date
  started: number
  completed: number
  failed: number
  running: number
  jobs: BucketJob[]
}

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

function bucketSizeMs(rangeMs: number): number {
  if (rangeMs <= HOUR) return 5 * MINUTE
  if (rangeMs <= 6 * HOUR) return 15 * MINUTE
  if (rangeMs <= DAY) return HOUR
  if (rangeMs <= 7 * DAY) return 4 * HOUR
  return DAY
}

function findBucketIndex(ms: number, fromMs: number, toMs: number, size: number, count: number): number | null {
  if (ms < fromMs || ms >= toMs) return null
  return Math.min(Math.floor((ms - fromMs) / size), count - 1)
}

export function bucketJobs(jobs: ValuationJobSummaryDto[], from: string, to: string): TimeBucket[] {
  const fromMs = new Date(from).getTime()
  const toMs = new Date(to).getTime()
  const rangeMs = toMs - fromMs

  if (rangeMs <= 0) return []

  const size = bucketSizeMs(rangeMs)
  const count = Math.ceil(rangeMs / size)

  if (count === 0) return []

  const buckets: TimeBucket[] = Array.from({ length: count }, (_, i) => ({
    from: new Date(fromMs + i * size),
    to: new Date(Math.min(fromMs + (i + 1) * size, toMs)),
    started: 0,
    completed: 0,
    failed: 0,
    running: 0,
    jobs: [],
  }))

  // Pass 1: bucket by startedAt
  for (const job of jobs) {
    const startMs = new Date(job.startedAt).getTime()
    const index = findBucketIndex(startMs, fromMs, toMs, size, count)
    if (index === null) continue

    const bucket = buckets[index]
    const completedAt = job.completedAt ? new Date(job.completedAt) : null

    bucket.started++
    bucket.jobs.push({
      jobId: job.jobId,
      startedAt: new Date(job.startedAt),
      completedAt,
      status: job.status,
    })

    // If no completedAt, it's still running
    if (!job.completedAt) {
      bucket.running++
    }
  }

  // Pass 2: bucket by completedAt
  for (const job of jobs) {
    if (!job.completedAt) continue

    const completeMs = new Date(job.completedAt).getTime()
    const index = findBucketIndex(completeMs, fromMs, toMs, size, count)
    if (index === null) continue

    const bucket = buckets[index]
    if (job.status === 'COMPLETED') {
      bucket.completed++
    } else if (job.status === 'FAILED') {
      bucket.failed++
    }
  }

  return buckets
}
