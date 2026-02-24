import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CalculationRunSummaryDto, CalculationRunDetailDto } from '../types'
import { RunHistoryTable } from './RunHistoryTable'

const runs: CalculationRunSummaryDto[] = [
  {
    runId: 'run-1',
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
    runId: 'run-2',
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

const selectedRunDetail: CalculationRunDetailDto = {
  runId: 'run-1',
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

const defaultProps = {
  selectedRunId: null as string | null,
  selectedRun: null as CalculationRunDetailDto | null,
  onSelectRun: () => {},
  onClearSelection: () => {},
}

describe('RunHistoryTable', () => {
  it('renders a table with rows for each run', () => {
    render(<RunHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.getByTestId('run-history-table')).toBeInTheDocument()
    expect(screen.getByTestId('run-row-run-1')).toBeInTheDocument()
    expect(screen.getByTestId('run-row-run-2')).toBeInTheDocument()
  })

  it('shows status badges with correct text', () => {
    render(<RunHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.getByText('COMPLETED')).toBeInTheDocument()
    expect(screen.getByText('FAILED')).toBeInTheDocument()
  })

  it('shows trigger type badges', () => {
    render(<RunHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.getByText('ON_DEMAND')).toBeInTheDocument()
    expect(screen.getByText('TRADE_EVENT')).toBeInTheDocument()
  })

  it('shows duration in milliseconds', () => {
    render(<RunHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.getByText('150ms')).toBeInTheDocument()
    expect(screen.getByText('200ms')).toBeInTheDocument()
  })

  it('calls onSelectRun when a row is clicked', () => {
    const onSelectRun = vi.fn()
    render(<RunHistoryTable runs={runs} {...defaultProps} onSelectRun={onSelectRun} />)

    fireEvent.click(screen.getByTestId('run-row-run-1'))

    expect(onSelectRun).toHaveBeenCalledWith('run-1')
  })

  it('shows empty state when no runs', () => {
    render(<RunHistoryTable runs={[]} {...defaultProps} />)

    expect(screen.getByTestId('run-history-empty')).toBeInTheDocument()
    expect(screen.getByText('No calculation runs yet.')).toBeInTheDocument()
  })

  it('shows dash for null VaR and ES values', () => {
    render(<RunHistoryTable runs={[runs[1]]} {...defaultProps} />)

    const row = screen.getByTestId('run-row-run-2')
    expect(row).toHaveTextContent('-')
  })

  it('renders inline detail panel for selected run', () => {
    render(
      <RunHistoryTable
        runs={runs}
        selectedRunId="run-1"
        selectedRun={selectedRunDetail}
        onSelectRun={() => {}}
        onClearSelection={() => {}}
      />,
    )

    expect(screen.getByTestId('run-detail-row')).toBeInTheDocument()
    expect(screen.getByTestId('run-detail-panel')).toBeInTheDocument()
    expect(screen.getByTestId('pipeline-timeline')).toBeInTheDocument()
    expect(screen.getByText('Pipeline Detail')).toBeInTheDocument()
  })

  it('calls onClearSelection when close button is clicked', () => {
    const onClearSelection = vi.fn()
    render(
      <RunHistoryTable
        runs={runs}
        selectedRunId="run-1"
        selectedRun={selectedRunDetail}
        onSelectRun={() => {}}
        onClearSelection={onClearSelection}
      />,
    )

    fireEvent.click(screen.getByTestId('close-detail'))

    expect(onClearSelection).toHaveBeenCalledTimes(1)
  })

  it('does not render detail row when no run is selected', () => {
    render(<RunHistoryTable runs={runs} {...defaultProps} />)

    expect(screen.queryByTestId('run-detail-row')).not.toBeInTheDocument()
  })
})
