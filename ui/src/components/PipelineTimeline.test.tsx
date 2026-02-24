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
    details: {
      positionCount: '5',
      positions: JSON.stringify([
        { instrumentId: 'AAPL', assetClass: 'EQUITY', quantity: '100', averageCost: '150.00 USD', marketPrice: '170.00 USD', marketValue: '17000.00 USD', unrealizedPnl: '2000.00 USD' },
        { instrumentId: 'TSLA', assetClass: 'EQUITY', quantity: '50', averageCost: '200.00 USD', marketPrice: '250.00 USD', marketValue: '12500.00 USD', unrealizedPnl: '2500.00 USD' },
      ]),
    },
    error: null,
  },
  {
    name: 'DISCOVER_DEPENDENCIES',
    status: 'COMPLETED',
    startedAt: '2025-01-15T10:00:00.020Z',
    completedAt: '2025-01-15T10:00:00.050Z',
    durationMs: 30,
    details: {
      dependencyCount: '3',
      dataTypes: 'SPOT_PRICE,YIELD_CURVE',
      dependencies: JSON.stringify([
        { instrumentId: 'AAPL', dataType: 'SPOT_PRICE', assetClass: 'EQUITY' },
        { instrumentId: 'USD_SOFR', dataType: 'YIELD_CURVE', assetClass: 'RATES', parameters: 'tenors=1M,3M,6M' },
      ]),
    },
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

  it('auto-expands FETCH_POSITIONS step by default', () => {
    render(<PipelineTimeline steps={steps} />)

    expect(screen.getByTestId('details-FETCH_POSITIONS')).toBeInTheDocument()
    expect(screen.getByText('positionCount:')).toBeInTheDocument()
    expect(screen.getByTestId('position-AAPL')).toBeInTheDocument()
    expect(screen.getByTestId('position-TSLA')).toBeInTheDocument()
  })

  it('expands step details on toggle click', () => {
    render(<PipelineTimeline steps={steps} />)

    expect(screen.queryByTestId('details-FETCH_MARKET_DATA')).not.toBeInTheDocument()

    fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))

    expect(screen.getByTestId('details-FETCH_MARKET_DATA')).toBeInTheDocument()
    expect(screen.getByText('requested:')).toBeInTheDocument()
  })

  it('renders expandable positions in FETCH_POSITIONS details', () => {
    render(<PipelineTimeline steps={steps} />)

    expect(screen.getByTestId('details-FETCH_POSITIONS')).toBeInTheDocument()
    expect(screen.getByText('positionCount:')).toBeInTheDocument()
    expect(screen.getByTestId('position-AAPL')).toBeInTheDocument()
    expect(screen.getByTestId('position-TSLA')).toBeInTheDocument()
    expect(screen.queryByText(/"instrumentId"/)).not.toBeInTheDocument()
  })

  it('expands position to show JSON', () => {
    render(<PipelineTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('position-AAPL'))

    const jsonBlock = screen.getByTestId('position-json-AAPL')
    expect(jsonBlock).toBeInTheDocument()
    expect(jsonBlock.textContent).toContain('"instrumentId": "AAPL"')
    expect(jsonBlock.textContent).toContain('"quantity": "100"')
    expect(jsonBlock.textContent).toContain('"marketValue": "17000.00 USD"')
  })

  it('does not render positions key as a regular detail', () => {
    render(<PipelineTimeline steps={steps} />)

    expect(screen.queryByText('positions:')).not.toBeInTheDocument()
  })

  it('renders expandable dependencies in DISCOVER_DEPENDENCIES details', () => {
    render(<PipelineTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('toggle-DISCOVER_DEPENDENCIES'))

    expect(screen.getByTestId('details-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
    expect(screen.getByTestId('dependency-AAPL-SPOT_PRICE')).toBeInTheDocument()
    expect(screen.getByText('AAPL — SPOT_PRICE')).toBeInTheDocument()
    expect(screen.getByTestId('dependency-USD_SOFR-YIELD_CURVE')).toBeInTheDocument()
    expect(screen.getByText('USD_SOFR — YIELD_CURVE')).toBeInTheDocument()
  })

  it('expands dependency to show JSON', () => {
    render(<PipelineTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('toggle-DISCOVER_DEPENDENCIES'))
    fireEvent.click(screen.getByTestId('dependency-AAPL-SPOT_PRICE'))

    const jsonBlock = screen.getByTestId('dependency-json-AAPL-SPOT_PRICE')
    expect(jsonBlock).toBeInTheDocument()
    expect(jsonBlock.textContent).toContain('"instrumentId": "AAPL"')
    expect(jsonBlock.textContent).toContain('"dataType": "SPOT_PRICE"')
    expect(jsonBlock.textContent).toContain('"assetClass": "EQUITY"')
  })

  it('does not render dependencies key as a regular detail', () => {
    render(<PipelineTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('toggle-DISCOVER_DEPENDENCIES'))

    expect(screen.queryByText('dependencies:')).not.toBeInTheDocument()
  })

  it('renders empty list without errors', () => {
    render(<PipelineTimeline steps={[]} />)

    expect(screen.getByTestId('pipeline-timeline')).toBeInTheDocument()
  })
})
