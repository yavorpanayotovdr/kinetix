import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { TimeBucket } from '../utils/timeBuckets'
import { ChartTooltip } from './ChartTooltip'

function makeBucket(overrides: Partial<TimeBucket> = {}): TimeBucket {
  return {
    from: new Date('2025-01-15T10:00:00Z'),
    to: new Date('2025-01-15T11:00:00Z'),
    started: 7,
    completed: 3,
    failed: 1,
    running: 2,
    jobs: [
      { jobId: 'job-aaa-111', startedAt: new Date('2025-01-15T10:01:00Z'), completedAt: new Date('2025-01-15T10:02:00Z'), status: 'COMPLETED' },
      { jobId: 'job-bbb-222', startedAt: new Date('2025-01-15T10:03:00Z'), completedAt: null, status: 'RUNNING' },
    ],
    ...overrides,
  }
}

describe('ChartTooltip', () => {
  it('returns null when not visible', () => {
    const { container } = render(
      <ChartTooltip bucket={makeBucket()} visible={false} rangeDays={1} barCenterX={100} containerWidth={600} />,
    )
    expect(container.firstElementChild).toBeNull()
  })

  it('returns null when bucket is null', () => {
    const { container } = render(
      <ChartTooltip bucket={null} visible={true} rangeDays={1} barCenterX={100} containerWidth={600} />,
    )
    expect(container.firstElementChild).toBeNull()
  })

  it('returns null when bucket has zero jobs', () => {
    const { container } = render(
      <ChartTooltip bucket={makeBucket({ started: 0, completed: 0, failed: 0, running: 0 })} visible={true} rangeDays={1} barCenterX={100} containerWidth={600} />,
    )
    expect(container.firstElementChild).toBeNull()
  })

  it('shows time range and status counts', () => {
    render(
      <ChartTooltip bucket={makeBucket()} visible={true} rangeDays={1} barCenterX={100} containerWidth={600} />,
    )
    expect(screen.getByText(/Started: 7/)).toBeInTheDocument()
    expect(screen.getByText(/Completed: 3/)).toBeInTheDocument()
    expect(screen.getByText(/Failed: 1/)).toBeInTheDocument()
    expect(screen.getByText(/Running: 2/)).toBeInTheDocument()
  })

  it('is positioned at barCenterX', () => {
    render(
      <ChartTooltip bucket={makeBucket()} visible={true} rangeDays={1} barCenterX={175} containerWidth={600} />,
    )
    const tooltip = screen.getByTestId('chart-tooltip')
    expect(tooltip.style.left).toBe('175px')
  })
})
