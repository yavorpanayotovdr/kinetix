import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CalculationJobDetailDto } from '../types'

vi.mock('../hooks/useJobHistory')

import { useJobHistory } from '../hooks/useJobHistory'
import { JobHistory } from './JobHistory'

const mockUseJobHistory = vi.mocked(useJobHistory)

const defaultHookResult = {
  runs: [],
  expandedJobs: {} as Record<string, CalculationJobDetailDto>,
  loadingJobIds: new Set<string>(),
  loading: false,
  error: null,
  toggleJob: vi.fn(),
  closeJob: vi.fn(),
  clearSelection: vi.fn(),
  refresh: vi.fn(),
}

describe('JobHistory', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockUseJobHistory.mockReturnValue(defaultHookResult)
  })

  it('renders expanded by default', () => {
    mockUseJobHistory.mockReturnValue({
      ...defaultHookResult,
      runs: [
        {
          jobId: 'job-1',
          portfolioId: 'port-1',
          triggerType: 'ON_DEMAND',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:00:00.150Z',
          durationMs: 150,
          calculationType: 'PARAMETRIC',
          varValue: 5000.0,
          expectedShortfall: 6250.0,
        },
      ],
    })

    render(<JobHistory portfolioId="port-1" />)

    expect(screen.getByTestId('job-history')).toBeInTheDocument()
    expect(screen.getByText('Calculation Jobs')).toBeInTheDocument()
    expect(screen.getByTestId('job-history-table')).toBeInTheDocument()
  })

  it('passes portfolioId to hook on initial render', () => {
    render(<JobHistory portfolioId="port-1" />)

    expect(mockUseJobHistory).toHaveBeenCalledWith('port-1')
  })

  it('passes null to hook when collapsed', () => {
    render(<JobHistory portfolioId="port-1" />)

    fireEvent.click(screen.getByTestId('job-history-toggle'))

    expect(mockUseJobHistory).toHaveBeenCalledWith(null)
  })

  it('shows loading state', () => {
    mockUseJobHistory.mockReturnValue({
      ...defaultHookResult,
      loading: true,
    })

    render(<JobHistory portfolioId="port-1" />)

    expect(screen.getByTestId('job-history-loading')).toBeInTheDocument()
  })

  it('shows error message', () => {
    mockUseJobHistory.mockReturnValue({
      ...defaultHookResult,
      error: 'Failed to load',
    })

    render(<JobHistory portfolioId="port-1" />)

    expect(screen.getByTestId('job-history-error')).toBeInTheDocument()
    expect(screen.getByText('Failed to load')).toBeInTheDocument()
  })

  it('shows job count badge', () => {
    mockUseJobHistory.mockReturnValue({
      ...defaultHookResult,
      runs: [
        {
          jobId: 'job-1',
          portfolioId: 'port-1',
          triggerType: 'ON_DEMAND',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:00:00.150Z',
          durationMs: 150,
          calculationType: 'PARAMETRIC',
          varValue: 5000.0,
          expectedShortfall: 6250.0,
        },
      ],
    })

    render(<JobHistory portfolioId="port-1" />)

    expect(screen.getByText('1')).toBeInTheDocument()
  })

  it('shows inline job detail when a job is expanded', () => {
    const detail = {
      jobId: 'job-1',
      portfolioId: 'port-1',
      triggerType: 'ON_DEMAND',
      status: 'COMPLETED',
      startedAt: '2025-01-15T10:00:00Z',
      completedAt: '2025-01-15T10:00:00.150Z',
      durationMs: 150,
      calculationType: 'PARAMETRIC',
      confidenceLevel: 'CL_95',
      varValue: 5000.0,
      expectedShortfall: 6250.0,
      steps: [
        {
          name: 'FETCH_POSITIONS',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:00:00.020Z',
          durationMs: 20,
          details: { positionCount: '5' },
          error: null,
        },
      ],
      error: null,
    }

    mockUseJobHistory.mockReturnValue({
      ...defaultHookResult,
      runs: [
        {
          jobId: 'job-1',
          portfolioId: 'port-1',
          triggerType: 'ON_DEMAND',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:00:00.150Z',
          durationMs: 150,
          calculationType: 'PARAMETRIC',
          varValue: 5000.0,
          expectedShortfall: 6250.0,
        },
      ],
      expandedJobs: { 'job-1': detail },
    })

    render(<JobHistory portfolioId="port-1" />)

    expect(screen.getByTestId('job-detail-panel')).toBeInTheDocument()
    expect(screen.getByTestId('job-timeline')).toBeInTheDocument()
    expect(screen.getByTestId('job-detail-row-job-1')).toBeInTheDocument()
  })

  it('calls closeJob when close detail button is clicked', () => {
    const closeJob = vi.fn()
    mockUseJobHistory.mockReturnValue({
      ...defaultHookResult,
      runs: [
        {
          jobId: 'job-1',
          portfolioId: 'port-1',
          triggerType: 'ON_DEMAND',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: null,
          durationMs: null,
          calculationType: null,
          varValue: null,
          expectedShortfall: null,
        },
      ],
      expandedJobs: {
        'job-1': {
          jobId: 'job-1',
          portfolioId: 'port-1',
          triggerType: 'ON_DEMAND',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: null,
          durationMs: null,
          calculationType: null,
          confidenceLevel: null,
          varValue: null,
          expectedShortfall: null,
          steps: [],
          error: null,
        },
      },
      closeJob,
    })

    render(<JobHistory portfolioId="port-1" />)

    fireEvent.click(screen.getByTestId('close-detail-job-1'))

    expect(closeJob).toHaveBeenCalledWith('job-1')
  })
})
