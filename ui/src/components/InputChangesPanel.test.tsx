import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { InputChangesPanel } from './InputChangesPanel'
import type { InputChangesSummaryDto, ParameterDiffDto } from '../types'

function emptyInputChanges(
  overrides: Partial<InputChangesSummaryDto> = {},
): InputChangesSummaryDto {
  return {
    positionsChanged: false,
    marketDataChanged: false,
    modelVersionChanged: false,
    baseModelVersion: '1.0.0',
    targetModelVersion: '1.0.0',
    positionChanges: [],
    marketDataChanges: [],
    ...overrides,
  }
}

describe('InputChangesPanel', () => {
  it('renders unavailable message when inputChanges is null', () => {
    render(<InputChangesPanel inputChanges={null} parameterDiffs={[]} />)

    const panel = screen.getByTestId('input-changes-unavailable')
    expect(panel).toBeInTheDocument()
    expect(
      screen.getByText('Input change data not available for this comparison'),
    ).toBeInTheDocument()
  })

  it('renders panel with count badge when changes exist', () => {
    const inputChanges = emptyInputChanges({
      positionsChanged: true,
      positionChanges: [
        {
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          changeType: 'ADDED',
          baseQuantity: null,
          targetQuantity: '100',
          quantityDelta: '100',
          baseMarketPrice: null,
          targetMarketPrice: '150.00',
          priceDelta: null,
          currency: 'USD',
        },
      ],
      marketDataChanges: [
        {
          dataType: 'PRICE',
          instrumentId: 'MSFT',
          assetClass: 'EQUITY',
          changeType: 'CHANGED',
          magnitude: 'SMALL',
        },
      ],
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    expect(screen.getByTestId('input-changes-panel')).toBeInTheDocument()
    const badge = screen.getByTestId('input-changes-count')
    expect(badge).toBeInTheDocument()
    expect(badge).toHaveTextContent('2')
  })

  it('renders inputs identical message when no changes detected', () => {
    const inputChanges = emptyInputChanges()

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    expect(screen.getByTestId('inputs-identical')).toBeInTheDocument()
    expect(screen.getByText('Inputs identical')).toBeInTheDocument()
  })

  it('is collapsed by default', () => {
    const inputChanges = emptyInputChanges({
      positionsChanged: true,
      positionChanges: [
        {
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          changeType: 'ADDED',
          baseQuantity: null,
          targetQuantity: '100',
          quantityDelta: '100',
          baseMarketPrice: null,
          targetMarketPrice: '150.00',
          priceDelta: null,
          currency: 'USD',
        },
      ],
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    const toggle = screen.getByRole('button', { expanded: false })
    expect(toggle).toBeInTheDocument()
    expect(screen.queryByTestId('diagnostic-disclaimer')).not.toBeInTheDocument()
  })

  it('expands on click to show body', () => {
    const inputChanges = emptyInputChanges({
      positionsChanged: true,
      positionChanges: [
        {
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          changeType: 'ADDED',
          baseQuantity: null,
          targetQuantity: '100',
          quantityDelta: '100',
          baseMarketPrice: null,
          targetMarketPrice: '150.00',
          priceDelta: null,
          currency: 'USD',
        },
      ],
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    const toggle = screen.getByRole('button', { expanded: false })
    fireEvent.click(toggle)

    expect(screen.getByRole('button', { expanded: true })).toBeInTheDocument()
    expect(document.getElementById('input-changes-body')).toBeInTheDocument()
  })

  it('renders diagnostic disclaimer when expanded', () => {
    const inputChanges = emptyInputChanges()

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    fireEvent.click(screen.getByRole('button'))

    const disclaimer = screen.getByTestId('diagnostic-disclaimer')
    expect(disclaimer).toBeInTheDocument()
    expect(disclaimer).toHaveTextContent(
      'Input change indicators are diagnostic estimates, not exact attribution.',
    )
  })

  it('renders model version change', () => {
    const inputChanges = emptyInputChanges({
      modelVersionChanged: true,
      baseModelVersion: '1.0.0',
      targetModelVersion: '2.0.0',
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    fireEvent.click(screen.getByRole('button'))

    expect(screen.getByTestId('model-params-section')).toBeInTheDocument()
    const versionChange = screen.getByTestId('model-version-change')
    expect(versionChange).toHaveTextContent('Model Version:')
    expect(versionChange).toHaveTextContent('1.0.0')
    expect(versionChange).toHaveTextContent('2.0.0')
  })

  it('renders position changes with ADDED badge', () => {
    const inputChanges = emptyInputChanges({
      positionsChanged: true,
      positionChanges: [
        {
          instrumentId: 'TSLA',
          assetClass: 'EQUITY',
          changeType: 'ADDED',
          baseQuantity: null,
          targetQuantity: '50',
          quantityDelta: '50',
          baseMarketPrice: null,
          targetMarketPrice: '200.00',
          priceDelta: null,
          currency: 'USD',
        },
      ],
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    fireEvent.click(screen.getByRole('button'))

    expect(screen.getByTestId('position-changes-section')).toBeInTheDocument()
    expect(screen.getByText('TSLA')).toBeInTheDocument()
    expect(screen.getByText('ADDED')).toBeInTheDocument()
  })

  it('renders position changes with REMOVED badge', () => {
    const inputChanges = emptyInputChanges({
      positionsChanged: true,
      positionChanges: [
        {
          instrumentId: 'GOOG',
          assetClass: 'EQUITY',
          changeType: 'REMOVED',
          baseQuantity: '200',
          targetQuantity: null,
          quantityDelta: '-200',
          baseMarketPrice: '120.00',
          targetMarketPrice: null,
          priceDelta: null,
          currency: 'USD',
        },
      ],
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    fireEvent.click(screen.getByRole('button'))

    expect(screen.getByTestId('position-changes-section')).toBeInTheDocument()
    expect(screen.getByText('GOOG')).toBeInTheDocument()
    expect(screen.getByText('REMOVED')).toBeInTheDocument()
  })

  it('renders market data changes with change type', () => {
    const inputChanges = emptyInputChanges({
      marketDataChanged: true,
      marketDataChanges: [
        {
          dataType: 'VOL_SURFACE',
          instrumentId: 'SPX',
          assetClass: 'EQUITY',
          changeType: 'CHANGED',
          magnitude: null,
        },
      ],
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    fireEvent.click(screen.getByRole('button'))

    expect(screen.getByTestId('market-data-changes-section')).toBeInTheDocument()
    expect(screen.getByText('VOL_SURFACE')).toBeInTheDocument()
    expect(screen.getByText('SPX')).toBeInTheDocument()
    expect(screen.getByText('CHANGED')).toBeInTheDocument()
  })

  it('renders magnitude indicator for market data changes', () => {
    const inputChanges = emptyInputChanges({
      marketDataChanged: true,
      marketDataChanges: [
        {
          dataType: 'PRICE',
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          changeType: 'CHANGED',
          magnitude: 'LARGE',
        },
      ],
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    fireEvent.click(screen.getByRole('button'))

    expect(screen.getByTestId('magnitude-large')).toBeInTheDocument()
    expect(screen.getByText('LARGE')).toBeInTheDocument()
  })

  it('does not render position section when no position changes', () => {
    const inputChanges = emptyInputChanges({
      marketDataChanged: true,
      marketDataChanges: [
        {
          dataType: 'PRICE',
          instrumentId: 'MSFT',
          assetClass: 'EQUITY',
          changeType: 'CHANGED',
          magnitude: 'SMALL',
        },
      ],
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    fireEvent.click(screen.getByRole('button'))

    expect(screen.queryByTestId('position-changes-section')).not.toBeInTheDocument()
    expect(screen.getByTestId('market-data-changes-section')).toBeInTheDocument()
  })

  it('does not render market data section when no market data changes', () => {
    const inputChanges = emptyInputChanges({
      positionsChanged: true,
      positionChanges: [
        {
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          changeType: 'QUANTITY_CHANGED',
          baseQuantity: '100',
          targetQuantity: '150',
          quantityDelta: '50',
          baseMarketPrice: '150.00',
          targetMarketPrice: '150.00',
          priceDelta: '0',
          currency: 'USD',
        },
      ],
    })

    render(<InputChangesPanel inputChanges={inputChanges} parameterDiffs={[]} />)

    fireEvent.click(screen.getByRole('button'))

    expect(screen.getByTestId('position-changes-section')).toBeInTheDocument()
    expect(screen.queryByTestId('market-data-changes-section')).not.toBeInTheDocument()
  })

  it('renders parameter diffs in model section', () => {
    const inputChanges = emptyInputChanges()
    const parameterDiffs: ParameterDiffDto[] = [
      { paramName: 'numSimulations', baseValue: '10000', targetValue: '50000' },
      { paramName: 'confidenceLevel', baseValue: '0.95', targetValue: '0.99' },
    ]

    render(
      <InputChangesPanel
        inputChanges={inputChanges}
        parameterDiffs={parameterDiffs}
      />,
    )

    fireEvent.click(screen.getByRole('button'))

    expect(screen.getByTestId('model-params-section')).toBeInTheDocument()
    expect(screen.getByText('numSimulations')).toBeInTheDocument()
    expect(screen.getByText('10000')).toBeInTheDocument()
    expect(screen.getByText('50000')).toBeInTheDocument()
    expect(screen.getByText('confidenceLevel')).toBeInTheDocument()
    expect(screen.getByText('0.95')).toBeInTheDocument()
    expect(screen.getByText('0.99')).toBeInTheDocument()
  })
})
