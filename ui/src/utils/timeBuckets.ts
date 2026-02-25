import type { ValuationJobSummaryDto } from '../types'

export interface TimeBucket {
  from: Date
  to: Date
  completed: number
  failed: number
  running: number
  jobIds: string[]
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
    completed: 0,
    failed: 0,
    running: 0,
    jobIds: [],
  }))

  for (const job of jobs) {
    const jobMs = new Date(job.startedAt).getTime()
    if (jobMs < fromMs || jobMs >= toMs) continue

    const index = Math.min(Math.floor((jobMs - fromMs) / size), count - 1)
    const bucket = buckets[index]
    bucket.jobIds.push(job.jobId)

    switch (job.status) {
      case 'COMPLETED':
        bucket.completed++
        break
      case 'FAILED':
        bucket.failed++
        break
      case 'RUNNING':
        bucket.running++
        break
    }
  }

  return buckets
}
