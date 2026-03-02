import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('../hooks/useJobPicker')

import { JobPickerDialog } from './JobPickerDialog'
import { useJobPicker } from '../hooks/useJobPicker'
import type { UseJobPickerResult } from '../hooks/useJobPicker'
import type { ValuationJobSummaryDto } from '../types'

const mockUseJobPicker = vi.mocked(useJobPicker)

const completedJobs: ValuationJobSummaryDto[] = [
  {
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
    delta: null, gamma: null, vega: null, theta: null, rho: null,
  },
  {
    jobId: 'bbbbbbbb-cccc-dddd-eeee-ffffffffffff',
    portfolioId: 'port-1',
    triggerType: 'SCHEDULED',
    status: 'COMPLETED',
    startedAt: '2025-01-15T06:00:00Z',
    completedAt: '2025-01-15T06:00:30Z',
    durationMs: 30000,
    calculationType: 'HISTORICAL',
    confidenceLevel: 'CL_95',
    varValue: 450.0,
    expectedShortfall: 550.0,
    pvValue: null,
    delta: null, gamma: null, vega: null, theta: null, rho: null,
  },
]

function defaultHookResult(overrides: Partial<UseJobPickerResult> = {}): UseJobPickerResult {
  return {
    jobs: completedJobs,
    loading: false,
    error: null,
    timeRange: { from: '', to: '', label: 'Today' },
    setTimeRange: vi.fn(),
    search: '',
    setSearch: vi.fn(),
    page: 0,
    totalPages: 1,
    totalCount: 2,
    hasNextPage: false,
    nextPage: vi.fn(),
    prevPage: vi.fn(),
    firstPage: vi.fn(),
    lastPage: vi.fn(),
    refresh: vi.fn(),
    ...overrides,
  }
}

describe('JobPickerDialog', () => {
  const defaultProps = {
    open: true,
    portfolioId: 'port-1',
    onSelect: vi.fn(),
    onCancel: vi.fn(),
  }

  beforeEach(() => {
    vi.resetAllMocks()
    mockUseJobPicker.mockReturnValue(defaultHookResult())
  })

  describe('rendering', () => {
    it('does not render when closed', () => {
      const { container } = render(<JobPickerDialog {...defaultProps} open={false} />)
      expect(container.firstChild).toBeNull()
    })

    it('renders dialog with title and description when open', () => {
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByText('Select VaR Calculation')).toBeInTheDocument()
      expect(screen.getByText('Choose a completed VaR calculation to use as the SOD baseline.')).toBeInTheDocument()
    })

    it('renders job list when open', () => {
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-table')).toBeInTheDocument()

      const rows = screen.getAllByTestId('job-picker-row')
      expect(rows).toHaveLength(2)
    })

    it('renders truncated job IDs with full UUID as title', () => {
      render(<JobPickerDialog {...defaultProps} />)
      const rows = screen.getAllByTestId('job-picker-row')
      expect(rows[0]).toHaveTextContent('aaaaaaaa')
      // Full UUID available via title attribute
      const idCell = rows[0].querySelector('td[title]')
      expect(idCell?.getAttribute('title')).toBe('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
    })

    it('renders ES and Trigger columns', () => {
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByText('ES')).toBeInTheDocument()
      expect(screen.getByText('Trigger')).toBeInTheDocument()
      expect(screen.getByText('600.00')).toBeInTheDocument()
      expect(screen.getByText('ON_DEMAND')).toBeInTheDocument()
    })

    it('shows loading spinner while jobs are being fetched', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ loading: true, jobs: [] }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-loading')).toBeInTheDocument()
    })

    it('shows error message when fetch fails', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ error: 'Network error', jobs: [] }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-error')).toHaveTextContent('Network error')
    })

    it('shows empty state when no completed jobs exist', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ jobs: [], totalCount: 0 }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-empty')).toHaveTextContent(
        'No completed VaR calculations found.',
      )
    })

    it('shows filtered empty state when search has no matches', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ jobs: [], totalCount: 0, search: 'nonexistent' }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-empty')).toHaveTextContent(
        'No completed calculations match your search.',
      )
    })

    it('shows dash for null VaR, ES, and duration values', () => {
      const jobWithNulls: ValuationJobSummaryDto = {
        ...completedJobs[0],
        jobId: 'nullnull-0000-0000-0000-000000000000',
        varValue: null,
        expectedShortfall: null,
        durationMs: null,
      }
      mockUseJobPicker.mockReturnValue(defaultHookResult({ jobs: [jobWithNulls] }))
      render(<JobPickerDialog {...defaultProps} />)

      const row = screen.getByTestId('job-picker-row')
      const cells = row.querySelectorAll('td')
      // VaR (index 4), ES (index 5), Duration (index 6) should all show dash
      expect(cells[4]).toHaveTextContent('—')
      expect(cells[5]).toHaveTextContent('—')
      expect(cells[6]).toHaveTextContent('—')
    })
  })

  describe('search', () => {
    it('renders search input', () => {
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-search')).toBeInTheDocument()
    })

    it('renders search input with accessible label', () => {
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByLabelText('Search jobs')).toBeInTheDocument()
    })

    it('calls setSearch when user types in search input', () => {
      const setSearch = vi.fn()
      mockUseJobPicker.mockReturnValue(defaultHookResult({ setSearch }))
      render(<JobPickerDialog {...defaultProps} />)

      fireEvent.change(screen.getByTestId('job-picker-search'), { target: { value: 'PARAMETRIC' } })
      expect(setSearch).toHaveBeenCalledWith('PARAMETRIC')
    })
  })

  describe('time range', () => {
    it('renders TimeRangeSelector', () => {
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-time-range')).toBeInTheDocument()
      expect(screen.getByTestId('time-range-selector')).toBeInTheDocument()
    })
  })

  describe('pagination', () => {
    it('renders pagination controls when totalPages > 1', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ totalPages: 3, totalCount: 60, hasNextPage: true }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-pagination')).toBeInTheDocument()
    })

    it('does not render pagination when totalPages is 1', () => {
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.queryByTestId('job-picker-pagination')).not.toBeInTheDocument()
    })

    it('shows page info text', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ totalPages: 3, totalCount: 60, hasNextPage: true }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-page-info')).toHaveTextContent('Page 1 of 3')
    })

    it('shows total count', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ totalPages: 3, totalCount: 60, hasNextPage: true }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-total')).toHaveTextContent('Total: 60')
    })

    it('disables previous and first buttons on first page', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ page: 0, totalPages: 3, totalCount: 60, hasNextPage: true }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-first')).toBeDisabled()
      expect(screen.getByTestId('job-picker-prev')).toBeDisabled()
    })

    it('disables next and last buttons on last page', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ page: 2, totalPages: 3, totalCount: 60, hasNextPage: false }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-next')).toBeDisabled()
      expect(screen.getByTestId('job-picker-last')).toBeDisabled()
    })

    it('calls nextPage when next button is clicked', () => {
      const nextPageFn = vi.fn()
      mockUseJobPicker.mockReturnValue(defaultHookResult({ totalPages: 3, totalCount: 60, hasNextPage: true, nextPage: nextPageFn }))
      render(<JobPickerDialog {...defaultProps} />)

      fireEvent.click(screen.getByTestId('job-picker-next'))
      expect(nextPageFn).toHaveBeenCalledOnce()
    })

    it('calls prevPage when previous button is clicked', () => {
      const prevPageFn = vi.fn()
      mockUseJobPicker.mockReturnValue(defaultHookResult({ page: 1, totalPages: 3, totalCount: 60, hasNextPage: true, prevPage: prevPageFn }))
      render(<JobPickerDialog {...defaultProps} />)

      fireEvent.click(screen.getByTestId('job-picker-prev'))
      expect(prevPageFn).toHaveBeenCalledOnce()
    })

    it('pagination buttons have aria-labels', () => {
      mockUseJobPicker.mockReturnValue(defaultHookResult({ totalPages: 3, totalCount: 60, hasNextPage: true }))
      render(<JobPickerDialog {...defaultProps} />)
      expect(screen.getByTestId('job-picker-first')).toHaveAttribute('aria-label', 'First page')
      expect(screen.getByTestId('job-picker-prev')).toHaveAttribute('aria-label', 'Previous page')
      expect(screen.getByTestId('job-picker-next')).toHaveAttribute('aria-label', 'Next page')
      expect(screen.getByTestId('job-picker-last')).toHaveAttribute('aria-label', 'Last page')
    })
  })

  describe('selection', () => {
    it('calls onSelect when Use button is clicked', () => {
      const onSelect = vi.fn()
      render(<JobPickerDialog {...defaultProps} onSelect={onSelect} />)

      const buttons = screen.getAllByTestId('job-picker-select')
      fireEvent.click(buttons[0])

      expect(onSelect).toHaveBeenCalledWith('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
    })

    it('calls onSelect when a job row is clicked', () => {
      const onSelect = vi.fn()
      render(<JobPickerDialog {...defaultProps} onSelect={onSelect} />)

      const rows = screen.getAllByTestId('job-picker-row')
      fireEvent.click(rows[1])

      expect(onSelect).toHaveBeenCalledWith('bbbbbbbb-cccc-dddd-eeee-ffffffffffff')
    })

    it('calls onSelect when Enter is pressed on a focused row', () => {
      const onSelect = vi.fn()
      render(<JobPickerDialog {...defaultProps} onSelect={onSelect} />)

      const rows = screen.getAllByTestId('job-picker-row')
      fireEvent.keyDown(rows[0], { key: 'Enter' })

      expect(onSelect).toHaveBeenCalledWith('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
    })
  })

  describe('dismissal', () => {
    it('calls onCancel when Cancel button is clicked', () => {
      const onCancel = vi.fn()
      render(<JobPickerDialog {...defaultProps} onCancel={onCancel} />)

      fireEvent.click(screen.getByTestId('job-picker-cancel'))
      expect(onCancel).toHaveBeenCalledOnce()
    })

    it('calls onCancel when Escape key is pressed', async () => {
      const onCancel = vi.fn()
      render(<JobPickerDialog {...defaultProps} onCancel={onCancel} />)

      fireEvent.keyDown(document, { key: 'Escape' })
      await waitFor(() => {
        expect(onCancel).toHaveBeenCalledOnce()
      })
    })

    it('calls onCancel when overlay backdrop is clicked', () => {
      const onCancel = vi.fn()
      render(<JobPickerDialog {...defaultProps} onCancel={onCancel} />)

      fireEvent.click(screen.getByTestId('job-picker-overlay'))
      expect(onCancel).toHaveBeenCalledOnce()
    })

    it('does not call onCancel when dialog body is clicked', () => {
      const onCancel = vi.fn()
      render(<JobPickerDialog {...defaultProps} onCancel={onCancel} />)

      fireEvent.click(screen.getByTestId('job-picker-dialog'))
      expect(onCancel).not.toHaveBeenCalled()
    })
  })

  describe('accessibility', () => {
    it('dialog has role dialog and aria-modal', () => {
      render(<JobPickerDialog {...defaultProps} />)
      const overlay = screen.getByTestId('job-picker-overlay')
      expect(overlay).toHaveAttribute('role', 'dialog')
      expect(overlay).toHaveAttribute('aria-modal', 'true')
    })

    it('dialog has accessible title via aria-labelledby', () => {
      render(<JobPickerDialog {...defaultProps} />)
      const overlay = screen.getByTestId('job-picker-overlay')
      expect(overlay).toHaveAttribute('aria-labelledby', 'job-picker-title')
      expect(document.getElementById('job-picker-title')).toHaveTextContent('Select VaR Calculation')
    })

    it('job rows are keyboard navigable', () => {
      render(<JobPickerDialog {...defaultProps} />)
      const rows = screen.getAllByTestId('job-picker-row')
      rows.forEach((row) => {
        expect(row).toHaveAttribute('tabindex', '0')
      })
    })
  })
})
