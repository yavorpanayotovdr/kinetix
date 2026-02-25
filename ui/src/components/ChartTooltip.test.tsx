import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { TimeBucket } from '../utils/timeBuckets'
import { ChartTooltip } from './ChartTooltip'

function makeBucket(overrides: Partial<TimeBucket> = {}): TimeBucket {
  return {
    from: new Date('2025-01-15T10:00:00Z'),
    to: new Date('2025-01-15T11:00:00Z'),
    completed: 3,
    failed: 1,
    running: 0,
    jobIds: ['job-aaa-111', 'job-bbb-222', 'job-ccc-333'],
    ...overrides,
  }
}

function makeManyJobIds(count: number): string[] {
  return Array.from({ length: count }, (_, i) => `job-${String(i + 1).padStart(4, '0')}-abcdef`)
}

describe('ChartTooltip', () => {
  describe('hover mode', () => {
    it('always renders the container even when not visible', () => {
      const { container } = render(
        <ChartTooltip bucket={makeBucket()} visible={false} rangeDays={1} pinned={false} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      const wrapper = container.firstElementChild as HTMLElement
      expect(wrapper).not.toBeNull()
      expect(wrapper.className).toContain('h-28')
    })

    it('shows truncated job IDs in hover mode', () => {
      render(
        <ChartTooltip bucket={makeBucket()} visible={true} rangeDays={1} pinned={false} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      expect(screen.getByText('job-aaa-')).toBeInTheDocument()
      expect(screen.getByText('job-bbb-')).toBeInTheDocument()
    })

    it('shows clickable "and N more" button in hover mode', () => {
      const bucket = makeBucket({ jobIds: makeManyJobIds(8) })
      render(
        <ChartTooltip bucket={bucket} visible={true} rangeDays={1} pinned={false} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      expect(screen.getByText('and 3 more')).toBeInTheDocument()
      expect(screen.getByText('and 3 more').tagName).toBe('BUTTON')
    })

    it('calls onPin and expands when "and more" is clicked in hover mode', () => {
      const onPin = vi.fn()
      const bucket = makeBucket({ jobIds: makeManyJobIds(8) })
      render(
        <ChartTooltip bucket={bucket} visible={true} rangeDays={1} pinned={false} onClose={vi.fn()} onPin={onPin} />,
      )
      fireEvent.click(screen.getByText('and 3 more'))
      expect(onPin).toHaveBeenCalledTimes(1)
    })
  })

  describe('pinned mode', () => {
    it('shows close button when pinned', () => {
      render(
        <ChartTooltip bucket={makeBucket()} visible={true} rangeDays={1} pinned={true} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      expect(screen.getByTestId('tooltip-close')).toBeInTheDocument()
    })

    it('calls onClose when close button is clicked', () => {
      const onClose = vi.fn()
      render(
        <ChartTooltip bucket={makeBucket()} visible={true} rangeDays={1} pinned={true} onClose={onClose} />,
      )
      fireEvent.click(screen.getByTestId('tooltip-close'))
      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('calls onClose on Escape key when pinned', () => {
      const onClose = vi.fn()
      render(
        <ChartTooltip bucket={makeBucket()} visible={true} rangeDays={1} pinned={true} onClose={onClose} />,
      )
      fireEvent.keyDown(document, { key: 'Escape' })
      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('shows full job IDs when pinned', () => {
      render(
        <ChartTooltip bucket={makeBucket()} visible={true} rangeDays={1} pinned={true} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      expect(screen.getByText('job-aaa-111')).toBeInTheDocument()
      expect(screen.getByText('job-bbb-222')).toBeInTheDocument()
    })
  })

  describe('expand', () => {
    it('renders "and N more" as a clickable button when pinned', () => {
      const bucket = makeBucket({ jobIds: makeManyJobIds(8) })
      render(
        <ChartTooltip bucket={bucket} visible={true} rangeDays={1} pinned={true} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      const more = screen.getByText('and 3 more')
      expect(more.tagName).toBe('BUTTON')
    })

    it('expands to show all job IDs when clicked', () => {
      const jobIds = makeManyJobIds(8)
      const bucket = makeBucket({ jobIds })
      render(
        <ChartTooltip bucket={bucket} visible={true} rangeDays={1} pinned={true} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      fireEvent.click(screen.getByText('and 3 more'))
      for (const id of jobIds) {
        expect(screen.getByText(id)).toBeInTheDocument()
      }
    })
  })

  describe('search', () => {
    it('shows search input when pinned with more than 5 jobs', () => {
      const bucket = makeBucket({ jobIds: makeManyJobIds(8) })
      render(
        <ChartTooltip bucket={bucket} visible={true} rangeDays={1} pinned={true} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      expect(screen.getByTestId('tooltip-search')).toBeInTheDocument()
    })

    it('does not show search input when pinned with 5 or fewer jobs', () => {
      const bucket = makeBucket({ jobIds: makeManyJobIds(3) })
      render(
        <ChartTooltip bucket={bucket} visible={true} rangeDays={1} pinned={true} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      expect(screen.queryByTestId('tooltip-search')).not.toBeInTheDocument()
    })

    it('filters job IDs by search term', () => {
      const jobIds = ['alpha-001', 'alpha-002', 'beta-001', 'beta-002', 'gamma-001', 'gamma-002']
      const bucket = makeBucket({ jobIds })
      render(
        <ChartTooltip bucket={bucket} visible={true} rangeDays={1} pinned={true} onClose={vi.fn()} onPin={vi.fn()} />,
      )
      fireEvent.change(screen.getByTestId('tooltip-search'), { target: { value: 'beta' } })
      expect(screen.getByText('beta-001')).toBeInTheDocument()
      expect(screen.getByText('beta-002')).toBeInTheDocument()
      expect(screen.queryByText('alpha-001')).not.toBeInTheDocument()
    })

    it('resets search and expanded state when bucket changes', () => {
      const bucket1 = makeBucket({ jobIds: makeManyJobIds(8) })
      const bucket2 = makeBucket({
        jobIds: makeManyJobIds(7),
        from: new Date('2025-01-15T12:00:00Z'),
        to: new Date('2025-01-15T13:00:00Z'),
      })

      const { rerender } = render(
        <ChartTooltip bucket={bucket1} visible={true} rangeDays={1} pinned={true} onClose={vi.fn()} onPin={vi.fn()} />,
      )

      // Expand and type a search
      fireEvent.click(screen.getByText('and 3 more'))
      fireEvent.change(screen.getByTestId('tooltip-search'), { target: { value: 'something' } })

      // Change bucket
      rerender(
        <ChartTooltip bucket={bucket2} visible={true} rangeDays={1} pinned={true} onClose={vi.fn()} onPin={vi.fn()} />,
      )

      // Should show "and N more" again (not expanded) and search should be cleared
      expect(screen.getByText('and 2 more')).toBeInTheDocument()
      expect(screen.getByTestId<HTMLInputElement>('tooltip-search').value).toBe('')
    })
  })
})
