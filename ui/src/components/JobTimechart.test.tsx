import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { TimeBucket } from '../utils/timeBuckets'
import { JobTimechart } from './JobTimechart'

const CONTAINER_WIDTH = 600

function makeBucket(overrides: Partial<TimeBucket> & { from: Date; to: Date }): TimeBucket {
  return { started: 0, completed: 0, failed: 0, running: 0, jobIds: [], ...overrides }
}

const timeRange = {
  from: '2025-01-15T10:00:00Z',
  to: '2025-01-15T11:00:00Z',
  label: 'Custom',
}

class FakeResizeObserver {
  callback: ResizeObserverCallback
  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
  }
  observe() {
    this.callback(
      [{ contentRect: { width: CONTAINER_WIDTH } } as unknown as ResizeObserverEntry],
      this as unknown as ResizeObserver,
    )
  }
  unobserve() {}
  disconnect() {}
}

describe('JobTimechart', () => {
  const onZoom = vi.fn()
  const onResetZoom = vi.fn()

  beforeEach(() => {
    vi.resetAllMocks()
    vi.stubGlobal('ResizeObserver', FakeResizeObserver)
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(CONTAINER_WIDTH)
  })

  it('zooms into clicked bar time range on click', () => {
    const buckets = [
      makeBucket({ from: new Date('2025-01-15T10:00:00Z'), to: new Date('2025-01-15T10:15:00Z'), completed: 3 }),
      makeBucket({ from: new Date('2025-01-15T10:15:00Z'), to: new Date('2025-01-15T10:30:00Z'), completed: 1 }),
      makeBucket({ from: new Date('2025-01-15T10:30:00Z'), to: new Date('2025-01-15T10:45:00Z'), completed: 2 }),
      makeBucket({ from: new Date('2025-01-15T10:45:00Z'), to: new Date('2025-01-15T11:00:00Z'), completed: 1 }),
    ]

    render(
      <JobTimechart
        buckets={buckets}
        timeRange={timeRange}
        onZoom={onZoom}
        zoomDepth={0}
        onResetZoom={onResetZoom}
      />,
    )

    const svg = screen.getByTestId('job-timechart').querySelector('svg')!

    // plotWidth = 600 - 40 - 16 = 544, barWidth = 544 / 4 = 136
    // Bar index 1 spans x=[40+136, 40+272) = [176, 312)
    // Click in the middle of bar 1 at x=210
    // getBoundingClientRect().left is 0 in jsdom, so clientX = 210
    fireEvent.mouseDown(svg, { clientX: 210 })
    fireEvent.mouseUp(svg)

    expect(onZoom).toHaveBeenCalledTimes(1)

    const zoomRange = onZoom.mock.calls[0][0]
    expect(zoomRange.label).toBe('Custom')
    expect(new Date(zoomRange.from).getTime()).toBe(new Date('2025-01-15T10:15:00Z').getTime())
    expect(new Date(zoomRange.to).getTime()).toBe(new Date('2025-01-15T10:30:00Z').getTime())
  })

  it('does not zoom on click when no buckets exist', () => {
    render(
      <JobTimechart
        buckets={[]}
        timeRange={timeRange}
        onZoom={onZoom}
        zoomDepth={0}
        onResetZoom={onResetZoom}
      />,
    )

    const svg = screen.getByTestId('job-timechart').querySelector('svg')!

    fireEvent.mouseDown(svg, { clientX: 100 })
    fireEvent.mouseUp(svg)

    expect(onZoom).not.toHaveBeenCalled()
  })

  it('does not zoom on click outside bar area', () => {
    const buckets = [
      makeBucket({ from: new Date('2025-01-15T10:00:00Z'), to: new Date('2025-01-15T10:15:00Z'), completed: 1 }),
    ]

    render(
      <JobTimechart
        buckets={buckets}
        timeRange={timeRange}
        onZoom={onZoom}
        zoomDepth={0}
        onResetZoom={onResetZoom}
      />,
    )

    const svg = screen.getByTestId('job-timechart').querySelector('svg')!

    // Click to the left of the plot area (x < PADDING_LEFT)
    fireEvent.mouseDown(svg, { clientX: 10 })
    fireEvent.mouseUp(svg)

    expect(onZoom).not.toHaveBeenCalled()
  })
})
