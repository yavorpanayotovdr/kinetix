import { render, screen, fireEvent, act } from '@testing-library/react'
import { describe, expect, it, vi, afterEach } from 'vitest'
import type { ValuationJobSummaryDto, ValuationJobDetailDto } from '../types'
import { JobHistoryTable } from './JobHistoryTable'

const runs: ValuationJobSummaryDto[] = [
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
    pvValue: 1800000.0,
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
    pvValue: null,
  },
]

const jobDetail: ValuationJobDetailDto = {
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
  pvValue: 1800000.0,
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

const jobDetail2: ValuationJobDetailDto = {
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
  pvValue: null,
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
  expandedJobs: {} as Record<string, ValuationJobDetailDto>,
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

  it('shows duration formatted as seconds', () => {
    render(<JobHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.getByText('0.2s')).toBeInTheDocument()
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
    expect(screen.getByText('No valuation jobs yet.')).toBeInTheDocument()
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
    expect(screen.getByText('Job Details')).toBeInTheDocument()
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

  it('shows a search input in the detail panel', () => {
    render(
      <JobHistoryTable
        runs={runs}
        expandedJobs={{ 'job-1': jobDetail }}
        loadingJobIds={new Set()}
        onSelectJob={() => {}}
        onCloseJob={() => {}}
      />,
    )

    expect(screen.getByTestId('detail-search-job-1')).toBeInTheDocument()
  })

  it('passes search term to JobTimeline for filtering', () => {
    const detailWithPositions: ValuationJobDetailDto = {
      ...jobDetail,
      steps: [
        {
          name: 'FETCH_POSITIONS',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00Z',
          completedAt: '2025-01-15T10:00:00.020Z',
          durationMs: 20,
          details: {
            positionCount: '2',
            positions: JSON.stringify([
              { instrumentId: 'AAPL', assetClass: 'EQUITY' },
              { instrumentId: 'TSLA', assetClass: 'EQUITY' },
            ]),
          },
          error: null,
        },
        {
          name: 'VALUATION',
          status: 'COMPLETED',
          startedAt: '2025-01-15T10:00:00.080Z',
          completedAt: '2025-01-15T10:00:00.130Z',
          durationMs: 50,
          details: { varValue: '5000.0' },
          error: null,
        },
      ],
    }

    render(
      <JobHistoryTable
        runs={runs}
        expandedJobs={{ 'job-1': detailWithPositions }}
        loadingJobIds={new Set()}
        onSelectJob={() => {}}
        onCloseJob={() => {}}
      />,
    )

    fireEvent.change(screen.getByTestId('detail-search-job-1'), { target: { value: 'AAPL' } })

    expect(screen.getByTestId('job-step-FETCH_POSITIONS')).toBeInTheDocument()
    expect(screen.queryByTestId('job-step-VALUATION')).not.toBeInTheDocument()
  })

  it('displays truncated job ID in each row', () => {
    const uuidRuns = [
      {
        ...runs[0],
        jobId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
      },
      {
        ...runs[1],
        jobId: 'deadbeef-cafe-babe-face-123456789abc',
      },
    ]
    render(<JobHistoryTable runs={uuidRuns} {...defaultProps} />)

    const cell1 = screen.getByTestId('job-id-a1b2c3d4-e5f6-7890-abcd-ef1234567890')
    expect(cell1).toHaveTextContent('a1b2c3d4')
    expect(cell1).toHaveAttribute('title', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890')

    const cell2 = screen.getByTestId('job-id-deadbeef-cafe-babe-face-123456789abc')
    expect(cell2).toHaveTextContent('deadbeef')
    expect(cell2).toHaveAttribute('title', 'deadbeef-cafe-babe-face-123456789abc')
  })

  it('shows full job ID in the detail panel', () => {
    const uuidRun = {
      ...runs[0],
      jobId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    }
    const detail: ValuationJobDetailDto = {
      ...jobDetail,
      jobId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    }
    render(
      <JobHistoryTable
        runs={[uuidRun]}
        expandedJobs={{ 'a1b2c3d4-e5f6-7890-abcd-ef1234567890': detail }}
        loadingJobIds={new Set()}
        onSelectJob={() => {}}
        onCloseJob={() => {}}
      />,
    )

    const jobIdEl = screen.getByTestId('detail-job-id')
    expect(jobIdEl).toHaveTextContent('a1b2c3d4-e5f6-7890-abcd-ef1234567890')
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
    expect(screen.getAllByText('Job Details')).toHaveLength(2)
  })

  describe('live elapsed duration for RUNNING jobs', () => {
    afterEach(() => {
      vi.useRealTimers()
    })

    it('shows a ticking elapsed duration for a RUNNING job', () => {
      vi.useFakeTimers()
      const now = new Date('2025-01-15T10:00:03Z')
      vi.setSystemTime(now)

      const runningJob: ValuationJobSummaryDto = {
        jobId: 'job-running',
        portfolioId: 'port-1',
        triggerType: 'ON_DEMAND',
        status: 'RUNNING',
        startedAt: '2025-01-15T10:00:00Z',
        completedAt: null,
        durationMs: null,
        calculationType: 'PARAMETRIC',
        varValue: null,
        expectedShortfall: null,
        pvValue: null,
      }

      render(<JobHistoryTable runs={[runningJob]} {...defaultProps} />)

      const durationCell = screen.getByTestId('duration-job-running')
      expect(durationCell).toHaveTextContent('3s')

      act(() => { vi.advanceTimersByTime(1000) })
      expect(durationCell).toHaveTextContent('4s')

      act(() => { vi.advanceTimersByTime(1000) })
      expect(durationCell).toHaveTextContent('5s')
    })

    it('shows static duration for a COMPLETED job', () => {
      render(<JobHistoryTable runs={[runs[1]]} {...defaultProps} />)

      const durationCell = screen.getByTestId('duration-job-2')
      expect(durationCell).toHaveTextContent('0.2s')
    })

    it('cleans up the interval when the component unmounts', () => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date('2025-01-15T10:00:05Z'))

      const runningJob: ValuationJobSummaryDto = {
        jobId: 'job-running',
        portfolioId: 'port-1',
        triggerType: 'ON_DEMAND',
        status: 'RUNNING',
        startedAt: '2025-01-15T10:00:00Z',
        completedAt: null,
        durationMs: null,
        calculationType: 'PARAMETRIC',
        varValue: null,
        expectedShortfall: null,
        pvValue: null,
      }

      const { unmount } = render(<JobHistoryTable runs={[runningJob]} {...defaultProps} />)

      unmount()

      // Should not throw after unmount
      act(() => { vi.advanceTimersByTime(2000) })
    })
  })
})
