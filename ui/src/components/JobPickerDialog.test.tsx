import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('../api/jobHistory')

import { JobPickerDialog } from './JobPickerDialog'
import { fetchValuationJobs } from '../api/jobHistory'

const mockFetchValuationJobs = vi.mocked(fetchValuationJobs)

const completedJobs = [
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
  },
]

describe('JobPickerDialog', () => {
  const defaultProps = {
    open: true,
    portfolioId: 'port-1',
    onSelect: vi.fn(),
    onCancel: vi.fn(),
  }

  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('does not render when closed', () => {
    mockFetchValuationJobs.mockResolvedValue({ items: completedJobs, totalCount: 2 })
    const { container } = render(<JobPickerDialog {...defaultProps} open={false} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders job list when open', async () => {
    mockFetchValuationJobs.mockResolvedValue({ items: completedJobs, totalCount: 2 })

    render(<JobPickerDialog {...defaultProps} />)

    await waitFor(() => {
      expect(screen.getByTestId('job-picker-table')).toBeInTheDocument()
    })

    const rows = screen.getAllByTestId('job-picker-row')
    expect(rows).toHaveLength(2)
  })

  it('calls onSelect when Use as Baseline button is clicked', async () => {
    const onSelect = vi.fn()
    mockFetchValuationJobs.mockResolvedValue({ items: completedJobs, totalCount: 2 })

    render(<JobPickerDialog {...defaultProps} onSelect={onSelect} />)

    await waitFor(() => {
      expect(screen.getByTestId('job-picker-table')).toBeInTheDocument()
    })

    const buttons = screen.getAllByTestId('job-picker-select')
    fireEvent.click(buttons[0])

    expect(onSelect).toHaveBeenCalledWith('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')
  })

  it('shows empty state when no completed jobs', async () => {
    mockFetchValuationJobs.mockResolvedValue({ items: [], totalCount: 0 })

    render(<JobPickerDialog {...defaultProps} />)

    await waitFor(() => {
      expect(screen.getByTestId('job-picker-empty')).toBeInTheDocument()
    })
  })

  it('filters out non-completed jobs', async () => {
    const mixedJobs = [
      ...completedJobs,
      {
        jobId: 'cccccccc-dddd-eeee-ffff-000000000000',
        portfolioId: 'port-1',
        triggerType: 'ON_DEMAND',
        status: 'RUNNING',
        startedAt: '2025-01-15T09:00:00Z',
        completedAt: null,
        durationMs: null,
        calculationType: 'PARAMETRIC',
        confidenceLevel: 'CL_95',
        varValue: null,
        expectedShortfall: null,
        pvValue: null,
      },
    ]
    mockFetchValuationJobs.mockResolvedValue({ items: mixedJobs, totalCount: 3 })

    render(<JobPickerDialog {...defaultProps} />)

    await waitFor(() => {
      expect(screen.getByTestId('job-picker-table')).toBeInTheDocument()
    })

    const rows = screen.getAllByTestId('job-picker-row')
    expect(rows).toHaveLength(2)
  })

  it('calls onCancel when cancel button is clicked', async () => {
    const onCancel = vi.fn()
    mockFetchValuationJobs.mockResolvedValue({ items: completedJobs, totalCount: 2 })

    render(<JobPickerDialog {...defaultProps} onCancel={onCancel} />)

    await waitFor(() => {
      expect(screen.getByTestId('job-picker-table')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByTestId('job-picker-cancel'))
    expect(onCancel).toHaveBeenCalledOnce()
  })
})
