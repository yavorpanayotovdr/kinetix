import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { CalculationRunSummaryDto } from '../types'
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

describe('RunHistoryTable', () => {
  it('renders a table with rows for each run', () => {
    render(<RunHistoryTable runs={runs} selectedRunId={null} onSelectRun={() => {}} />)

    expect(screen.getByTestId('run-history-table')).toBeInTheDocument()
    expect(screen.getByTestId('run-row-run-1')).toBeInTheDocument()
    expect(screen.getByTestId('run-row-run-2')).toBeInTheDocument()
  })

  it('shows status badges with correct text', () => {
    render(<RunHistoryTable runs={runs} selectedRunId={null} onSelectRun={() => {}} />)

    expect(screen.getByText('COMPLETED')).toBeInTheDocument()
    expect(screen.getByText('FAILED')).toBeInTheDocument()
  })

  it('shows trigger type badges', () => {
    render(<RunHistoryTable runs={runs} selectedRunId={null} onSelectRun={() => {}} />)

    expect(screen.getByText('ON_DEMAND')).toBeInTheDocument()
    expect(screen.getByText('TRADE_EVENT')).toBeInTheDocument()
  })

  it('shows duration in milliseconds', () => {
    render(<RunHistoryTable runs={runs} selectedRunId={null} onSelectRun={() => {}} />)

    expect(screen.getByText('150ms')).toBeInTheDocument()
    expect(screen.getByText('200ms')).toBeInTheDocument()
  })

  it('calls onSelectRun when a row is clicked', () => {
    const onSelectRun = vi.fn()
    render(<RunHistoryTable runs={runs} selectedRunId={null} onSelectRun={onSelectRun} />)

    fireEvent.click(screen.getByTestId('run-row-run-1'))

    expect(onSelectRun).toHaveBeenCalledWith('run-1')
  })

  it('shows empty state when no runs', () => {
    render(<RunHistoryTable runs={[]} selectedRunId={null} onSelectRun={() => {}} />)

    expect(screen.getByTestId('run-history-empty')).toBeInTheDocument()
    expect(screen.getByText('No calculation runs yet.')).toBeInTheDocument()
  })

  it('shows dash for null VaR and ES values', () => {
    render(<RunHistoryTable runs={[runs[1]]} selectedRunId={null} onSelectRun={() => {}} />)

    const row = screen.getByTestId('run-row-run-2')
    expect(row).toHaveTextContent('-')
  })
})
