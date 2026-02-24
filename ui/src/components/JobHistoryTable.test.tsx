import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CalculationJobSummaryDto, CalculationJobDetailDto } from '../types'
import { JobHistoryTable } from './JobHistoryTable'

const runs: CalculationJobSummaryDto[] = [
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
  {
    jobId: 'job-2',
    portfolioId: 'port-1',
    triggerType: 'TRADE_EVENT',
    status: 'FAILED',
    startedAt: '2025-01-15T09:00:00Z',
    completedAt: '2025-01-15T09:00:00.200Z',
    durationMs: 200,
    calculationType: 'PARAMETRIC',
    varValue: null,
    expectedShortfall: null,
  },
]

const jobDetail: CalculationJobDetailDto = {
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

const jobDetail2: CalculationJobDetailDto = {
  jobId: 'job-2',
  portfolioId: 'port-1',
  triggerType: 'TRADE_EVENT',
  status: 'FAILED',
  startedAt: '2025-01-15T09:00:00Z',
  completedAt: '2025-01-15T09:00:00.200Z',
  durationMs: 200,
  calculationType: 'PARAMETRIC',
  confidenceLevel: 'CL_95',
  varValue: null,
  expectedShortfall: null,
  steps: [
    {
      name: 'FETCH_POSITIONS',
      status: 'FAILED',
      startedAt: '2025-01-15T09:00:00Z',
      completedAt: '2025-01-15T09:00:00.050Z',
      durationMs: 50,
      details: {},
      error: 'timeout',
    },
  ],
  error: 'timeout',
}

const defaultProps = {
  expandedJobs: {} as Record<string, CalculationJobDetailDto>,
  loadingJobIds: new Set<string>(),
  onSelectJob: () => {},
  onCloseJob: () => {},
}

describe('JobHistoryTable', () => {
  it('renders a table with rows for each job', () => {
    render(<JobHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.getByTestId('job-history-table')).toBeInTheDocument()
    expect(screen.getByTestId('job-row-job-1')).toBeInTheDocument()
    expect(screen.getByTestId('job-row-job-2')).toBeInTheDocument()
  })

  it('shows status badges with correct text', () => {
    render(<JobHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.getByText('COMPLETED')).toBeInTheDocument()
    expect(screen.getByText('FAILED')).toBeInTheDocument()
  })

  it('shows trigger type badges', () => {
    render(<JobHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.getByText('ON_DEMAND')).toBeInTheDocument()
    expect(screen.getByText('TRADE_EVENT')).toBeInTheDocument()
  })

  it('shows duration in milliseconds', () => {
    render(<JobHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.getByText('150ms')).toBeInTheDocument()
    expect(screen.getByText('200ms')).toBeInTheDocument()
  })

  it('calls onSelectJob when a row is clicked', () => {
    const onSelectJob = vi.fn()
    render(<JobHistoryTable runs={runs} {...defaultProps} onSelectJob={onSelectJob} />)

    fireEvent.click(screen.getByTestId('job-row-job-1'))

    expect(onSelectJob).toHaveBeenCalledWith('job-1')
  })

  it('shows empty state when no jobs', () => {
    render(<JobHistoryTable runs={[]} {...defaultProps} />)

    expect(screen.getByTestId('job-history-empty')).toBeInTheDocument()
    expect(screen.getByText('No calculation jobs yet.')).toBeInTheDocument()
  })

  it('shows dash for null VaR and ES values', () => {
    render(<JobHistoryTable runs={[runs[1]]} {...defaultProps} />)

    const row = screen.getByTestId('job-row-job-2')
    expect(row).toHaveTextContent('-')
  })

  it('renders inline detail panel for an expanded job', () => {
    render(
      <JobHistoryTable
        runs={runs}
        expandedJobs={{ 'job-1': jobDetail }}
        loadingJobIds={new Set()}
        onSelectJob={() => {}}
        onCloseJob={() => {}}
      />,
    )

    expect(screen.getByTestId('job-detail-row-job-1')).toBeInTheDocument()
    expect(screen.getByTestId('job-detail-panel')).toBeInTheDocument()
    expect(screen.getByTestId('job-timeline')).toBeInTheDocument()
    expect(screen.getByText('Job Detail')).toBeInTheDocument()
  })

  it('shows loading indicator while detail is fetching', () => {
    render(
      <JobHistoryTable
        runs={runs}
        expandedJobs={{}}
        loadingJobIds={new Set(['job-1'])}
        onSelectJob={() => {}}
        onCloseJob={() => {}}
      />,
    )

    expect(screen.getByTestId('job-detail-row-job-1')).toBeInTheDocument()
    expect(screen.getByTestId('detail-loading')).toBeInTheDocument()
    expect(screen.getByText('Loading job details...')).toBeInTheDocument()
    expect(screen.queryByTestId('job-timeline')).not.toBeInTheDocument()
  })

  it('calls onCloseJob when close button is clicked', () => {
    const onCloseJob = vi.fn()
    render(
      <JobHistoryTable
        runs={runs}
        expandedJobs={{ 'job-1': jobDetail }}
        loadingJobIds={new Set()}
        onSelectJob={() => {}}
        onCloseJob={onCloseJob}
      />,
    )

    fireEvent.click(screen.getByTestId('close-detail-job-1'))

    expect(onCloseJob).toHaveBeenCalledWith('job-1')
  })

  it('does not render detail row when no jobs are expanded', () => {
    render(<JobHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.queryByTestId('job-detail-row-job-1')).not.toBeInTheDocument()
    expect(screen.queryByTestId('job-detail-row-job-2')).not.toBeInTheDocument()
  })

  it('renders multiple detail panels when multiple jobs are expanded', () => {
    render(
      <JobHistoryTable
        runs={runs}
        expandedJobs={{ 'job-1': jobDetail, 'job-2': jobDetail2 }}
        loadingJobIds={new Set()}
        onSelectJob={() => {}}
        onCloseJob={() => {}}
      />,
    )

    expect(screen.getByTestId('job-detail-row-job-1')).toBeInTheDocument()
    expect(screen.getByTestId('job-detail-row-job-2')).toBeInTheDocument()
    expect(screen.getAllByTestId('job-detail-panel')).toHaveLength(2)
    expect(screen.getAllByText('Job Detail')).toHaveLength(2)
  })
})
