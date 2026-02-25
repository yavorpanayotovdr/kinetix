import { useMemo } from 'react'
import { bucketJobs, type TimeBucket } from '../utils/timeBuckets'
import type { ValuationJobSummaryDto, TimeRange } from '../types'

export function useTimeBuckets(runs: ValuationJobSummaryDto[], timeRange: TimeRange): TimeBucket[] {
  return useMemo(
    () => bucketJobs(runs, timeRange.from, timeRange.to),
    [runs, timeRange.from, timeRange.to],
  )
}
