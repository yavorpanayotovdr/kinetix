import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { PipelineStepDto } from '../types'
import { PipelineTimeline } from './PipelineTimeline'

const steps: PipelineStepDto[] = [
  {
    name: 'FETCH_POSITIONS',
    status: 'COMPLETED',
    startedAt: '2025-01-15T10:00:00Z',
    completedAt: '2025-01-15T10:00:00.020Z',
    durationMs: 20,
    details: { positionCount: '5' },
    error: null,
  },
  {
    name: 'DISCOVER_DEPENDENCIES',
    status: 'COMPLETED',
    startedAt: '2025-01-15T10:00:00.020Z',
    completedAt: '2025-01-15T10:00:00.050Z',
    durationMs: 30,
    details: { dependencyCount: '3', dataTypes: 'SPOT_PRICE,YIELD_CURVE' },
    error: null,
  },
  {
    name: 'FETCH_MARKET_DATA',
    status: 'COMPLETED',
    startedAt: '2025-01-15T10:00:00.050Z',
    completedAt: '2025-01-15T10:00:00.080Z',
    durationMs: 30,
    details: { requested: '3', fetched: '2' },
    error: null,
  },
  {
    name: 'CALCULATE_VAR',
    status: 'COMPLETED',
    startedAt: '2025-01-15T10:00:00.080Z',
    completedAt: '2025-01-15T10:00:00.130Z',
    durationMs: 50,
    details: { varValue: '5000.0', expectedShortfall: '6250.0' },
    error: null,
  },
  {
    name: 'PUBLISH_RESULT',
    status: 'COMPLETED',
    startedAt: '2025-01-15T10:00:00.130Z',
    completedAt: '2025-01-15T10:00:00.150Z',
    durationMs: 20,
    details: { topic: 'risk.results' },
    error: null,
  },
]

describe('PipelineTimeline', () => {
  it('renders all 5 pipeline steps', () => {
    render(<PipelineTimeline steps={steps} />)

    expect(screen.getByTestId('pipeline-timeline')).toBeInTheDocument()
    expect(screen.getByTestId('pipeline-step-FETCH_POSITIONS')).toBeInTheDocument()
    expect(screen.getByTestId('pipeline-step-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
    expect(screen.getByTestId('pipeline-step-FETCH_MARKET_DATA')).toBeInTheDocument()
    expect(screen.getByTestId('pipeline-step-CALCULATE_VAR')).toBeInTheDocument()
    expect(screen.getByTestId('pipeline-step-PUBLISH_RESULT')).toBeInTheDocument()
  })

  it('displays human-readable step labels', () => {
    render(<PipelineTimeline steps={steps} />)

    expect(screen.getByText('Fetch Positions')).toBeInTheDocument()
    expect(screen.getByText('Discover Dependencies')).toBeInTheDocument()
    expect(screen.getByText('Fetch Market Data')).toBeInTheDocument()
    expect(screen.getByText('Calculate VaR')).toBeInTheDocument()
    expect(screen.getByText('Publish Result')).toBeInTheDocument()
  })

  it('shows duration for each step', () => {
    render(<PipelineTimeline steps={steps} />)

    expect(screen.getAllByText('20ms')).toHaveLength(2)
    expect(screen.getAllByText('30ms')).toHaveLength(2)
    expect(screen.getByText('50ms')).toBeInTheDocument()
  })

  it('shows green status dot for completed steps', () => {
    render(<PipelineTimeline steps={[steps[0]]} />)

    expect(screen.getByTestId('step-dot-COMPLETED')).toBeInTheDocument()
  })

  it('shows red status dot for failed steps', () => {
    const failedStep: PipelineStepDto = {
      ...steps[0],
      status: 'FAILED',
      error: 'Connection timeout',
    }
    render(<PipelineTimeline steps={[failedStep]} />)

    expect(screen.getByTestId('step-dot-FAILED')).toBeInTheDocument()
    expect(screen.getByText('Connection timeout')).toBeInTheDocument()
  })

  it('expands step details on toggle click', () => {
    render(<PipelineTimeline steps={steps} />)

    expect(screen.queryByTestId('details-FETCH_POSITIONS')).not.toBeInTheDocument()

    fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

    expect(screen.getByTestId('details-FETCH_POSITIONS')).toBeInTheDocument()
    expect(screen.getByText('positionCount:')).toBeInTheDocument()
  })

  it('renders empty list without errors', () => {
    render(<PipelineTimeline steps={[]} />)

    expect(screen.getByTestId('pipeline-timeline')).toBeInTheDocument()
  })
})
