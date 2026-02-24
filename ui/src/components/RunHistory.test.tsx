import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('../hooks/useRunHistory')

import { useRunHistory } from '../hooks/useRunHistory'
import { RunHistory } from './RunHistory'

const mockUseRunHistory = vi.mocked(useRunHistory)

const defaultHookResult = {
  runs: [],
  selectedRun: null,
  loading: false,
  error: null,
  selectRun: vi.fn(),
  clearSelection: vi.fn(),
  refresh: vi.fn(),
}

describe('RunHistory', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockUseRunHistory.mockReturnValue(defaultHookResult)
  })

  it('renders collapsed by default', () => {
    render(<RunHistory portfolioId="port-1" />)

    expect(screen.getByTestId('run-history')).toBeInTheDocument()
    expect(screen.getByText('Calculation Runs')).toBeInTheDocument()
    expect(screen.queryByTestId('run-history-table')).not.toBeInTheDocument()
  })

  it('passes null portfolioId to hook when collapsed', () => {
    render(<RunHistory portfolioId="port-1" />)

    expect(mockUseRunHistory).toHaveBeenCalledWith(null)
  })

  it('passes portfolioId to hook when expanded', () => {
    render(<RunHistory portfolioId="port-1" />)

    fireEvent.click(screen.getByTestId('run-history-toggle'))

    expect(mockUseRunHistory).toHaveBeenCalledWith('port-1')
  })

  it('shows loading state when expanded and loading', () => {
    mockUseRunHistory.mockReturnValue({
      ...defaultHookResult,
      loading: true,
    })

    render(<RunHistory portfolioId="port-1" />)

    fireEvent.click(screen.getByTestId('run-history-toggle'))

    expect(screen.getByTestId('run-history-loading')).toBeInTheDocument()
  })

  it('shows error message when expanded and errored', () => {
    mockUseRunHistory.mockReturnValue({
      ...defaultHookResult,
      error: 'Failed to load',
    })

    render(<RunHistory portfolioId="port-1" />)

    fireEvent.click(screen.getByTestId('run-history-toggle'))

    expect(screen.getByTestId('run-history-error')).toBeInTheDocument()
    expect(screen.getByText('Failed to load')).toBeInTheDocument()
  })

  it('shows run count badge when expanded with runs', () => {
    mockUseRunHistory.mockReturnValue({
      ...defaultHookResult,
      runs: [
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
      ],
    })

    render(<RunHistory portfolioId="port-1" />)

    fireEvent.click(screen.getByTestId('run-history-toggle'))

    expect(screen.getByText('1')).toBeInTheDocument()
  })

  it('shows pipeline detail panel when a run is selected', () => {
    mockUseRunHistory.mockReturnValue({
      ...defaultHookResult,
      runs: [
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
      ],
      selectedRun: {
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
      },
    })

    render(<RunHistory portfolioId="port-1" />)

    fireEvent.click(screen.getByTestId('run-history-toggle'))

    expect(screen.getByTestId('run-detail-panel')).toBeInTheDocument()
    expect(screen.getByTestId('pipeline-timeline')).toBeInTheDocument()
  })

  it('calls clearSelection when close detail button is clicked', () => {
    const clearSelection = vi.fn()
    mockUseRunHistory.mockReturnValue({
      ...defaultHookResult,
      runs: [
        {
          runId: 'run-1',
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
      selectedRun: {
        runId: 'run-1',
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
      clearSelection,
    })

    render(<RunHistory portfolioId="port-1" />)

    fireEvent.click(screen.getByTestId('run-history-toggle'))
    fireEvent.click(screen.getByTestId('close-detail'))

    expect(clearSelection).toHaveBeenCalledTimes(1)
  })
})
