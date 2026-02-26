import { render, screen, fireEvent, act } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { JobStepDto } from '../types'
import { JobTimeline } from './JobTimeline'

const steps: JobStepDto[] = [
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
    details: {
      requested: '3',
      fetched: '2',
      marketDataItems: JSON.stringify([
        { instrumentId: 'AAPL', dataType: 'SPOT_PRICE', assetClass: 'EQUITY', status: 'FETCHED', value: '170.5' },
        { instrumentId: 'USD_SOFR', dataType: 'YIELD_CURVE', assetClass: 'RATES', status: 'FETCHED', points: '6' },
        { instrumentId: 'AAPL', dataType: 'HISTORICAL_PRICES', assetClass: 'EQUITY', status: 'MISSING' },
      ]),
    },
    error: null,
  },
  {
    name: 'CALCULATE_VAR',
    status: 'COMPLETED',
    startedAt: '2025-01-15T10:00:00.080Z',
    completedAt: '2025-01-15T10:00:00.130Z',
    durationMs: 50,
    details: {
      varValue: '5000.0',
      expectedShortfall: '6250.0',
      positionBreakdown: JSON.stringify([
        { instrumentId: 'AAPL', assetClass: 'EQUITY', marketValue: '17000.00', varContribution: '3000.00', esContribution: '3750.00', percentageOfTotal: '60.00' },
        { instrumentId: 'TSLA', assetClass: 'EQUITY', marketValue: '12500.00', varContribution: '2000.00', esContribution: '2500.00', percentageOfTotal: '40.00' },
      ]),
    },
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

describe('JobTimeline', () => {
  it('renders all 5 job steps', () => {
    render(<JobTimeline steps={steps} />)

    expect(screen.getByTestId('job-timeline')).toBeInTheDocument()
    expect(screen.getByTestId('job-step-FETCH_POSITIONS')).toBeInTheDocument()
    expect(screen.getByTestId('job-step-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
    expect(screen.getByTestId('job-step-FETCH_MARKET_DATA')).toBeInTheDocument()
    expect(screen.getByTestId('job-step-CALCULATE_VAR')).toBeInTheDocument()
    expect(screen.getByTestId('job-step-PUBLISH_RESULT')).toBeInTheDocument()
  })

  it('displays human-readable step labels', () => {
    render(<JobTimeline steps={steps} />)

    expect(screen.getByText('Fetch Positions')).toBeInTheDocument()
    expect(screen.getByText('Discover Dependencies')).toBeInTheDocument()
    expect(screen.getByText('Fetch Market Data')).toBeInTheDocument()
    expect(screen.getByText('Calculate VaR')).toBeInTheDocument()
    expect(screen.getByText('Publish Result')).toBeInTheDocument()
  })

  it('shows duration formatted as seconds for each step', () => {
    render(<JobTimeline steps={steps} />)

    expect(screen.getAllByText('0.0s')).toHaveLength(4)
    expect(screen.getByText('0.1s')).toBeInTheDocument()
  })

  it('shows green status dot for completed steps', () => {
    render(<JobTimeline steps={[steps[0]]} />)

    expect(screen.getByTestId('step-dot-COMPLETED')).toBeInTheDocument()
  })

  it('shows amber status dot when a completed step has missing market data items', () => {
    render(<JobTimeline steps={[steps[2]]} />)

    expect(screen.getByTestId('step-dot-PARTIAL')).toBeInTheDocument()
  })

  it('shows green status dot when all market data items are fetched', () => {
    const allFetchedStep: JobStepDto = {
      ...steps[2],
      details: {
        requested: '2',
        fetched: '2',
        marketDataItems: JSON.stringify([
          { instrumentId: 'AAPL', dataType: 'SPOT_PRICE', assetClass: 'EQUITY', status: 'FETCHED', value: '170.5' },
          { instrumentId: 'USD_SOFR', dataType: 'YIELD_CURVE', assetClass: 'RATES', status: 'FETCHED', points: '6' },
        ]),
      },
    }
    render(<JobTimeline steps={[allFetchedStep]} />)

    expect(screen.getByTestId('step-dot-COMPLETED')).toBeInTheDocument()
  })

  it('shows red status dot for failed steps', () => {
    const failedStep: JobStepDto = {
      ...steps[0],
      status: 'FAILED',
      error: 'Connection timeout',
    }
    render(<JobTimeline steps={[failedStep]} />)

    expect(screen.getByTestId('step-dot-FAILED')).toBeInTheDocument()
    expect(screen.getByText('Connection timeout')).toBeInTheDocument()
  })

  it('expands step details on toggle click', () => {
    render(<JobTimeline steps={steps} />)

    expect(screen.queryByTestId('details-FETCH_POSITIONS')).not.toBeInTheDocument()

    fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

    expect(screen.getByTestId('details-FETCH_POSITIONS')).toBeInTheDocument()
    expect(screen.getByText('positionCount:')).toBeInTheDocument()
  })

  it('renders expandable positions in FETCH_POSITIONS details', () => {
    render(<JobTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

    expect(screen.getByTestId('details-FETCH_POSITIONS')).toBeInTheDocument()
    expect(screen.getByText('positionCount:')).toBeInTheDocument()
    expect(screen.getByTestId('position-AAPL')).toBeInTheDocument()
    expect(screen.getByTestId('position-TSLA')).toBeInTheDocument()
    expect(screen.queryByText(/"instrumentId"/)).not.toBeInTheDocument()
  })

  it('expands position to show JSON', () => {
    render(<JobTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))
    fireEvent.click(screen.getByTestId('position-AAPL'))

    const jsonBlock = screen.getByTestId('position-json-AAPL')
    expect(jsonBlock).toBeInTheDocument()
    expect(jsonBlock.textContent).toContain('"instrumentId": "AAPL"')
    expect(jsonBlock.textContent).toContain('"quantity": "100"')
    expect(jsonBlock.textContent).toContain('"marketValue": "17000.00 USD"')
  })

  it('does not render positions key as a regular detail', () => {
    render(<JobTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

    expect(screen.queryByText('positions:')).not.toBeInTheDocument()
  })

  it('renders expandable dependencies in DISCOVER_DEPENDENCIES details', () => {
    render(<JobTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('toggle-DISCOVER_DEPENDENCIES'))

    expect(screen.getByTestId('details-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
    expect(screen.getByTestId('dependency-AAPL-SPOT_PRICE')).toBeInTheDocument()
    expect(screen.getByText('SPOT_PRICE — AAPL')).toBeInTheDocument()
    expect(screen.getByTestId('dependency-USD_SOFR-YIELD_CURVE')).toBeInTheDocument()
    expect(screen.getByText('YIELD_CURVE — USD_SOFR')).toBeInTheDocument()
  })

  it('expands dependency to show JSON', () => {
    render(<JobTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('toggle-DISCOVER_DEPENDENCIES'))
    fireEvent.click(screen.getByTestId('dependency-AAPL-SPOT_PRICE'))

    const jsonBlock = screen.getByTestId('dependency-json-AAPL-SPOT_PRICE')
    expect(jsonBlock).toBeInTheDocument()
    expect(jsonBlock.textContent).toContain('"instrumentId": "AAPL"')
    expect(jsonBlock.textContent).toContain('"dataType": "SPOT_PRICE"')
    expect(jsonBlock.textContent).toContain('"assetClass": "EQUITY"')
  })

  it('does not render dependencies key as a regular detail', () => {
    render(<JobTimeline steps={steps} />)

    fireEvent.click(screen.getByTestId('toggle-DISCOVER_DEPENDENCIES'))

    expect(screen.queryByText('dependencies:')).not.toBeInTheDocument()
  })

  it('renders empty list without errors', () => {
    render(<JobTimeline steps={[]} />)

    expect(screen.getByTestId('job-timeline')).toBeInTheDocument()
  })

  describe('search prop filters steps by content', () => {
    it('shows all steps when search is empty', () => {
      render(<JobTimeline steps={steps} search="" />)

      expect(screen.getByTestId('job-step-FETCH_POSITIONS')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-FETCH_MARKET_DATA')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-CALCULATE_VAR')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-PUBLISH_RESULT')).toBeInTheDocument()
    })

    it('filters steps to those whose details contain the search term', () => {
      render(<JobTimeline steps={steps} search="AAPL" />)

      expect(screen.getByTestId('job-step-FETCH_POSITIONS')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-FETCH_MARKET_DATA')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-CALCULATE_VAR')).toBeInTheDocument()
      expect(screen.queryByTestId('job-step-PUBLISH_RESULT')).not.toBeInTheDocument()
    })

    it('matches step detail key-value pairs', () => {
      render(<JobTimeline steps={steps} search="risk.results" />)

      expect(screen.getByTestId('job-step-PUBLISH_RESULT')).toBeInTheDocument()
      expect(screen.queryByTestId('job-step-FETCH_POSITIONS')).not.toBeInTheDocument()
    })

    it('matches step label name', () => {
      render(<JobTimeline steps={steps} search="Calculate" />)

      expect(screen.getByTestId('job-step-CALCULATE_VAR')).toBeInTheDocument()
      expect(screen.queryByTestId('job-step-FETCH_POSITIONS')).not.toBeInTheDocument()
    })

    it('is case-insensitive', () => {
      render(<JobTimeline steps={steps} search="aapl" />)

      expect(screen.getByTestId('job-step-FETCH_POSITIONS')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
    })

    it('auto-expands matching steps when search is active', () => {
      render(<JobTimeline steps={steps} search="AAPL" />)

      expect(screen.getByTestId('details-FETCH_POSITIONS')).toBeInTheDocument()
      expect(screen.getByTestId('details-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
      expect(screen.getByTestId('details-FETCH_MARKET_DATA')).toBeInTheDocument()
      expect(screen.getByTestId('details-CALCULATE_VAR')).toBeInTheDocument()
    })

    it('filters items within matching steps to only those that match', () => {
      render(<JobTimeline steps={steps} search="AAPL" />)

      expect(screen.getByTestId('position-AAPL')).toBeInTheDocument()
      expect(screen.queryByTestId('position-TSLA')).not.toBeInTheDocument()

      expect(screen.getByTestId('dependency-AAPL-SPOT_PRICE')).toBeInTheDocument()
      expect(screen.queryByTestId('dependency-USD_SOFR-YIELD_CURVE')).not.toBeInTheDocument()

      expect(screen.getByTestId('market-data-AAPL-SPOT_PRICE')).toBeInTheDocument()
      expect(screen.getByTestId('market-data-AAPL-HISTORICAL_PRICES')).toBeInTheDocument()
      expect(screen.queryByTestId('market-data-USD_SOFR-YIELD_CURVE')).not.toBeInTheDocument()
    })

    it('treats spaces as AND for step filtering', () => {
      render(<JobTimeline steps={steps} search="AAPL EQUITY" />)

      expect(screen.getByTestId('job-step-FETCH_POSITIONS')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-FETCH_MARKET_DATA')).toBeInTheDocument()
      expect(screen.getByTestId('job-step-CALCULATE_VAR')).toBeInTheDocument()
    })

    it('treats spaces as AND for item filtering within steps', () => {
      render(<JobTimeline steps={steps} search="SPOT AAPL" />)

      expect(screen.getByTestId('dependency-AAPL-SPOT_PRICE')).toBeInTheDocument()
      expect(screen.queryByTestId('dependency-USD_SOFR-YIELD_CURVE')).not.toBeInTheDocument()
    })

    it('shows no-results message when nothing matches', () => {
      render(<JobTimeline steps={steps} search="NONEXISTENT" />)

      expect(screen.getByText('No steps match your search.')).toBeInTheDocument()
    })
  })

  describe('in-step item filtering', () => {
    it('shows a filter input when positions step is expanded', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

      expect(screen.getByTestId('filter-FETCH_POSITIONS')).toBeInTheDocument()
    })

    it('filters positions by instrument ID', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

      fireEvent.change(screen.getByTestId('filter-FETCH_POSITIONS'), { target: { value: 'TSLA' } })

      expect(screen.getByTestId('position-TSLA')).toBeInTheDocument()
      expect(screen.queryByTestId('position-AAPL')).not.toBeInTheDocument()
    })

    it('filters positions by any field value', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

      fireEvent.change(screen.getByTestId('filter-FETCH_POSITIONS'), { target: { value: '17000' } })

      expect(screen.getByTestId('position-AAPL')).toBeInTheDocument()
      expect(screen.queryByTestId('position-TSLA')).not.toBeInTheDocument()
    })

    it('shows a filter input when dependencies step is expanded', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-DISCOVER_DEPENDENCIES'))

      expect(screen.getByTestId('filter-DISCOVER_DEPENDENCIES')).toBeInTheDocument()
    })

    it('filters dependencies by instrument ID', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-DISCOVER_DEPENDENCIES'))

      fireEvent.change(screen.getByTestId('filter-DISCOVER_DEPENDENCIES'), { target: { value: 'USD_SOFR' } })

      expect(screen.getByTestId('dependency-USD_SOFR-YIELD_CURVE')).toBeInTheDocument()
      expect(screen.queryByTestId('dependency-AAPL-SPOT_PRICE')).not.toBeInTheDocument()
    })

    it('filters dependencies by data type', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-DISCOVER_DEPENDENCIES'))

      fireEvent.change(screen.getByTestId('filter-DISCOVER_DEPENDENCIES'), { target: { value: 'YIELD' } })

      expect(screen.getByTestId('dependency-USD_SOFR-YIELD_CURVE')).toBeInTheDocument()
      expect(screen.queryByTestId('dependency-AAPL-SPOT_PRICE')).not.toBeInTheDocument()
    })

    it('is case-insensitive', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

      fireEvent.change(screen.getByTestId('filter-FETCH_POSITIONS'), { target: { value: 'tsla' } })

      expect(screen.getByTestId('position-TSLA')).toBeInTheDocument()
      expect(screen.queryByTestId('position-AAPL')).not.toBeInTheDocument()
    })

    it('treats spaces as AND in the item filter', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

      fireEvent.change(screen.getByTestId('filter-FETCH_POSITIONS'), { target: { value: 'EQUITY 170' } })

      expect(screen.getByTestId('position-AAPL')).toBeInTheDocument()
      expect(screen.queryByTestId('position-TSLA')).not.toBeInTheDocument()
    })
  })

  describe('market data items in FETCH_MARKET_DATA', () => {
    it('renders expandable market data items', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))

      expect(screen.getByTestId('details-FETCH_MARKET_DATA')).toBeInTheDocument()
      expect(screen.getByTestId('market-data-AAPL-SPOT_PRICE')).toBeInTheDocument()
      expect(screen.getByTestId('market-data-USD_SOFR-YIELD_CURVE')).toBeInTheDocument()
      expect(screen.getByTestId('market-data-AAPL-HISTORICAL_PRICES')).toBeInTheDocument()
    })

    it('displays label as dataType — instrumentId', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))

      expect(screen.getByText('SPOT_PRICE — AAPL')).toBeInTheDocument()
      expect(screen.getByText('YIELD_CURVE — USD_SOFR')).toBeInTheDocument()
      expect(screen.getByText('HISTORICAL_PRICES — AAPL')).toBeInTheDocument()
    })

    it('shows red dot only for MISSING items, no dot for FETCHED items', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))

      expect(screen.queryByTestId('market-data-dot-FETCHED')).not.toBeInTheDocument()
      expect(screen.getAllByTestId('market-data-dot-MISSING')).toHaveLength(1)
    })

    it('expands market data item to show JSON', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))
      fireEvent.click(screen.getByTestId('market-data-AAPL-SPOT_PRICE'))

      const jsonBlock = screen.getByTestId('market-data-json-AAPL-SPOT_PRICE')
      expect(jsonBlock).toBeInTheDocument()
      expect(jsonBlock.textContent).toContain('"instrumentId": "AAPL"')
      expect(jsonBlock.textContent).toContain('"dataType": "SPOT_PRICE"')
      expect(jsonBlock.textContent).toContain('"value": "170.5"')
    })

    it('uses neutral background for FETCHED items and red-tinted background for MISSING items', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))
      fireEvent.click(screen.getByTestId('market-data-AAPL-SPOT_PRICE'))
      fireEvent.click(screen.getByTestId('market-data-AAPL-HISTORICAL_PRICES'))

      const fetchedJson = screen.getByTestId('market-data-json-AAPL-SPOT_PRICE')
      expect(fetchedJson.className).toContain('bg-slate-50')

      const missingJson = screen.getByTestId('market-data-json-AAPL-HISTORICAL_PRICES')
      expect(missingJson.className).toContain('bg-red-50')
    })

    it('does not render marketDataItems key as a regular detail', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))

      expect(screen.queryByText('marketDataItems:')).not.toBeInTheDocument()
    })

    it('shows a filter input when market data step is expanded', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))

      expect(screen.getByTestId('filter-FETCH_MARKET_DATA')).toBeInTheDocument()
    })

    it('filters market data items by instrument ID', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))

      fireEvent.change(screen.getByTestId('filter-FETCH_MARKET_DATA'), { target: { value: 'USD_SOFR' } })

      expect(screen.getByTestId('market-data-USD_SOFR-YIELD_CURVE')).toBeInTheDocument()
      expect(screen.queryByTestId('market-data-AAPL-SPOT_PRICE')).not.toBeInTheDocument()
      expect(screen.queryByTestId('market-data-AAPL-HISTORICAL_PRICES')).not.toBeInTheDocument()
    })

    it('filters market data items by data type', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))

      fireEvent.change(screen.getByTestId('filter-FETCH_MARKET_DATA'), { target: { value: 'SPOT' } })

      expect(screen.getByTestId('market-data-AAPL-SPOT_PRICE')).toBeInTheDocument()
      expect(screen.queryByTestId('market-data-USD_SOFR-YIELD_CURVE')).not.toBeInTheDocument()
    })

    it('filters market data items by status', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_MARKET_DATA'))

      fireEvent.change(screen.getByTestId('filter-FETCH_MARKET_DATA'), { target: { value: 'MISSING' } })

      expect(screen.getByTestId('market-data-AAPL-HISTORICAL_PRICES')).toBeInTheDocument()
      expect(screen.queryByTestId('market-data-AAPL-SPOT_PRICE')).not.toBeInTheDocument()
      expect(screen.queryByTestId('market-data-USD_SOFR-YIELD_CURVE')).not.toBeInTheDocument()
    })
  })

  describe('per-position dependencies in FETCH_POSITIONS', () => {
    const dependenciesByPosition = {
      AAPL: [
        { instrumentId: 'AAPL', dataType: 'SPOT_PRICE', assetClass: 'EQUITY' },
        { instrumentId: '', dataType: 'CORRELATION_MATRIX', assetClass: '' },
      ],
      TSLA: [
        { instrumentId: 'TSLA', dataType: 'SPOT_PRICE', assetClass: 'EQUITY' },
        { instrumentId: '', dataType: 'CORRELATION_MATRIX', assetClass: '' },
      ],
    }

    const stepsWithPosDeps: JobStepDto[] = [
      {
        ...steps[0],
        details: {
          ...steps[0].details,
          dependenciesByPosition: JSON.stringify(dependenciesByPosition),
        },
      },
      steps[1],
      steps[2],
      steps[3],
      steps[4],
    ]

    it('shows dependencies toggle within expanded position when dependenciesByPosition is present', () => {
      render(<JobTimeline steps={stepsWithPosDeps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))
      fireEvent.click(screen.getByTestId('position-AAPL'))

      expect(screen.getByTestId('pos-deps-toggle-AAPL')).toBeInTheDocument()
      expect(screen.getByText('Dependencies (2)')).toBeInTheDocument()
    })

    it('expanding dependencies section shows individual dependency items', () => {
      render(<JobTimeline steps={stepsWithPosDeps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))
      fireEvent.click(screen.getByTestId('position-AAPL'))
      fireEvent.click(screen.getByTestId('pos-deps-toggle-AAPL'))

      expect(screen.getByTestId('pos-dep-AAPL-SPOT_PRICE')).toBeInTheDocument()
      expect(screen.getByTestId('pos-dep-AAPL-CORRELATION_MATRIX')).toBeInTheDocument()
    })

    it('expanding a dependency item shows its JSON', () => {
      render(<JobTimeline steps={stepsWithPosDeps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))
      fireEvent.click(screen.getByTestId('position-AAPL'))
      fireEvent.click(screen.getByTestId('pos-deps-toggle-AAPL'))
      fireEvent.click(screen.getByTestId('pos-dep-AAPL-SPOT_PRICE'))

      const jsonBlock = screen.getByTestId('pos-dep-json-AAPL-SPOT_PRICE')
      expect(jsonBlock).toBeInTheDocument()
      expect(jsonBlock.textContent).toContain('"dataType": "SPOT_PRICE"')
    })

    it('position copy button copies only position data', async () => {
      const writeText = vi.fn().mockResolvedValue(undefined)
      Object.assign(navigator, { clipboard: { writeText } })

      render(<JobTimeline steps={stepsWithPosDeps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))
      fireEvent.click(screen.getByTestId('position-AAPL'))

      await act(async () => {
        fireEvent.click(screen.getByTestId('copy-position-AAPL'))
      })

      const copied = JSON.parse(writeText.mock.calls[0][0])
      expect(copied.instrumentId).toBe('AAPL')
      expect(copied).not.toHaveProperty('dependencies')
    })

    it('dependencies copy button copies only dependency array JSON', async () => {
      const writeText = vi.fn().mockResolvedValue(undefined)
      Object.assign(navigator, { clipboard: { writeText } })

      render(<JobTimeline steps={stepsWithPosDeps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))
      fireEvent.click(screen.getByTestId('position-AAPL'))
      fireEvent.click(screen.getByTestId('pos-deps-toggle-AAPL'))

      await act(async () => {
        fireEvent.click(screen.getByTestId('copy-pos-deps-AAPL'))
      })

      const copied = JSON.parse(writeText.mock.calls[0][0])
      expect(copied).toHaveLength(2)
      expect(copied[0].dataType).toBe('SPOT_PRICE')
      expect(copied[1].dataType).toBe('CORRELATION_MATRIX')
    })

    it('shared dependencies appear under all positions', () => {
      render(<JobTimeline steps={stepsWithPosDeps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

      fireEvent.click(screen.getByTestId('position-AAPL'))
      fireEvent.click(screen.getByTestId('pos-deps-toggle-AAPL'))
      expect(screen.getByTestId('pos-dep-AAPL-CORRELATION_MATRIX')).toBeInTheDocument()

      fireEvent.click(screen.getByTestId('position-TSLA'))
      fireEvent.click(screen.getByTestId('pos-deps-toggle-TSLA'))
      expect(screen.getByTestId('pos-dep-TSLA-CORRELATION_MATRIX')).toBeInTheDocument()
    })

    it('does not show dependencies section when dependenciesByPosition is absent', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))
      fireEvent.click(screen.getByTestId('position-AAPL'))

      expect(screen.queryByTestId('pos-deps-toggle-AAPL')).not.toBeInTheDocument()
    })

    it('does not render dependenciesByPosition as a plain detail key-value', () => {
      render(<JobTimeline steps={stepsWithPosDeps} />)
      fireEvent.click(screen.getByTestId('toggle-FETCH_POSITIONS'))

      expect(screen.queryByText('dependenciesByPosition:')).not.toBeInTheDocument()
    })
  })

  describe('per-position VaR breakdown in CALCULATE_VAR', () => {
    it('renders expandable position breakdown items in CALCULATE_VAR details', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-CALCULATE_VAR'))

      expect(screen.getByTestId('details-CALCULATE_VAR')).toBeInTheDocument()
      expect(screen.getByTestId('var-breakdown-AAPL')).toBeInTheDocument()
      expect(screen.getByTestId('var-breakdown-TSLA')).toBeInTheDocument()
    })

    it('expands position breakdown item to show VaR and ES as JSON with copy button', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-CALCULATE_VAR'))
      fireEvent.click(screen.getByTestId('var-breakdown-AAPL'))

      const jsonBlock = screen.getByTestId('var-breakdown-json-AAPL')
      expect(jsonBlock).toBeInTheDocument()
      expect(jsonBlock.textContent).toContain('"varContribution": "3000.00"')
      expect(jsonBlock.textContent).toContain('"esContribution": "3750.00"')
      expect(jsonBlock.textContent).toContain('"percentageOfTotal": "60.00"')
      expect(jsonBlock.textContent).toContain('"marketValue": "17000.00"')

      expect(screen.getByTestId('copy-var-breakdown-AAPL')).toBeInTheDocument()
    })

    it('does not render positionBreakdown key as a regular detail', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-CALCULATE_VAR'))

      expect(screen.queryByText('positionBreakdown:')).not.toBeInTheDocument()
    })

    it('shows filter input when CALCULATE_VAR step has position breakdown', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-CALCULATE_VAR'))

      expect(screen.getByTestId('filter-CALCULATE_VAR')).toBeInTheDocument()
    })

    it('filters position breakdown items by instrument ID', () => {
      render(<JobTimeline steps={steps} />)
      fireEvent.click(screen.getByTestId('toggle-CALCULATE_VAR'))

      fireEvent.change(screen.getByTestId('filter-CALCULATE_VAR'), { target: { value: 'TSLA' } })

      expect(screen.getByTestId('var-breakdown-TSLA')).toBeInTheDocument()
      expect(screen.queryByTestId('var-breakdown-AAPL')).not.toBeInTheDocument()
    })
  })
})
