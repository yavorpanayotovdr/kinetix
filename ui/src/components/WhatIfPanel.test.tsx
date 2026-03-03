import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { WhatIfResponseDto, WhatIfImpactDto } from '../types'
import type { TradeFormEntry } from '../hooks/useWhatIf'
import { WhatIfPanel } from './WhatIfPanel'

const emptyTrade: TradeFormEntry = {
  instrumentId: '',
  assetClass: 'EQUITY',
  side: 'BUY',
  quantity: '',
  priceAmount: '',
  priceCurrency: 'USD',
}

const whatIfResponse: WhatIfResponseDto = {
  baseVaR: '100000.00',
  baseExpectedShortfall: '130000.00',
  baseGreeks: {
    portfolioId: 'port-1',
    assetClassGreeks: [
      { assetClass: 'EQUITY', delta: '50000.00', gamma: '1200.00', vega: '8000.00' },
    ],
    theta: '-500.00',
    rho: '200.00',
    calculatedAt: '2025-01-15T10:00:00Z',
  },
  basePositionRisk: [],
  hypotheticalVaR: '85000.00',
  hypotheticalExpectedShortfall: '110000.00',
  hypotheticalGreeks: {
    portfolioId: 'port-1',
    assetClassGreeks: [
      { assetClass: 'EQUITY', delta: '45000.00', gamma: '1100.00', vega: '7500.00' },
    ],
    theta: '-450.00',
    rho: '180.00',
    calculatedAt: '2025-01-15T10:00:00Z',
  },
  hypotheticalPositionRisk: [],
  varChange: '-15000.00',
  esChange: '-20000.00',
  calculatedAt: '2025-01-15T10:00:00Z',
}

const impact: WhatIfImpactDto = {
  varChange: '-15000.00',
  esChange: '-20000.00',
  deltaChange: -5000,
  gammaChange: -100,
  vegaChange: -500,
  thetaChange: 50,
  rhoChange: -20,
}

const defaultProps = {
  open: true,
  onClose: vi.fn(),
  trades: [emptyTrade],
  onAddTrade: vi.fn(),
  onRemoveTrade: vi.fn(),
  onUpdateTrade: vi.fn(),
  onSubmit: vi.fn(),
  onReset: vi.fn(),
  result: null as WhatIfResponseDto | null,
  impact: null as WhatIfImpactDto | null,
  loading: false,
  error: null as string | null,
}

describe('WhatIfPanel', () => {
  it('renders the trade form with all inputs', () => {
    render(<WhatIfPanel {...defaultProps} />)

    expect(screen.getByTestId('whatif-panel')).toBeInTheDocument()
    expect(screen.getByTestId('whatif-instrument-0')).toBeInTheDocument()
    expect(screen.getByTestId('whatif-side-0')).toBeInTheDocument()
    expect(screen.getByTestId('whatif-quantity-0')).toBeInTheDocument()
    expect(screen.getByTestId('whatif-price-0')).toBeInTheDocument()
  })

  it('does not render when closed', () => {
    render(<WhatIfPanel {...defaultProps} open={false} />)

    expect(screen.queryByTestId('whatif-panel')).not.toBeInTheDocument()
  })

  it('renders before/after comparison when results are available', () => {
    render(
      <WhatIfPanel
        {...defaultProps}
        result={whatIfResponse}
        impact={impact}
      />,
    )

    expect(screen.getByTestId('whatif-comparison')).toBeInTheDocument()
    expect(screen.getByTestId('whatif-var-base')).toHaveTextContent('100,000.00')
    expect(screen.getByTestId('whatif-var-after')).toHaveTextContent('85,000.00')
    expect(screen.getByTestId('whatif-var-change')).toBeInTheDocument()
  })

  it('shows VaR reduction in green', () => {
    render(
      <WhatIfPanel
        {...defaultProps}
        result={whatIfResponse}
        impact={impact}
      />,
    )

    const varChange = screen.getByTestId('whatif-var-change')
    expect(varChange.className).toContain('text-green-600')
  })

  it('shows VaR increase in red', () => {
    const increaseResponse: WhatIfResponseDto = {
      ...whatIfResponse,
      hypotheticalVaR: '120000.00',
      varChange: '20000.00',
    }
    const increaseImpact: WhatIfImpactDto = {
      ...impact,
      varChange: '20000.00',
    }

    render(
      <WhatIfPanel
        {...defaultProps}
        result={increaseResponse}
        impact={increaseImpact}
      />,
    )

    const varChange = screen.getByTestId('whatif-var-change')
    expect(varChange.className).toContain('text-red-600')
  })

  it('calls onAddTrade when add button is clicked', () => {
    const onAddTrade = vi.fn()
    render(<WhatIfPanel {...defaultProps} onAddTrade={onAddTrade} />)

    fireEvent.click(screen.getByTestId('whatif-add-trade'))

    expect(onAddTrade).toHaveBeenCalled()
  })

  it('calls onRemoveTrade when remove button is clicked for a trade', () => {
    const onRemoveTrade = vi.fn()
    const trades = [emptyTrade, { ...emptyTrade, instrumentId: 'MSFT' }]

    render(
      <WhatIfPanel
        {...defaultProps}
        trades={trades}
        onRemoveTrade={onRemoveTrade}
      />,
    )

    fireEvent.click(screen.getByTestId('whatif-remove-trade-1'))

    expect(onRemoveTrade).toHaveBeenCalledWith(1)
  })

  it('renders multiple trade forms when multiple trades exist', () => {
    const trades = [emptyTrade, { ...emptyTrade, instrumentId: 'MSFT' }]

    render(<WhatIfPanel {...defaultProps} trades={trades} />)

    expect(screen.getByTestId('whatif-instrument-0')).toBeInTheDocument()
    expect(screen.getByTestId('whatif-instrument-1')).toBeInTheDocument()
  })

  it('calls onUpdateTrade when input changes', () => {
    const onUpdateTrade = vi.fn()
    render(<WhatIfPanel {...defaultProps} onUpdateTrade={onUpdateTrade} />)

    fireEvent.change(screen.getByTestId('whatif-instrument-0'), {
      target: { value: 'SPY' },
    })

    expect(onUpdateTrade).toHaveBeenCalledWith(0, 'instrumentId', 'SPY')
  })

  it('calls onSubmit when run button is clicked', () => {
    const onSubmit = vi.fn()
    render(<WhatIfPanel {...defaultProps} onSubmit={onSubmit} />)

    fireEvent.click(screen.getByTestId('whatif-run'))

    expect(onSubmit).toHaveBeenCalled()
  })

  it('shows loading state during submission', () => {
    render(<WhatIfPanel {...defaultProps} loading={true} />)

    expect(screen.getByTestId('whatif-run')).toBeDisabled()
  })

  it('shows error message when error occurs', () => {
    render(<WhatIfPanel {...defaultProps} error="Something went wrong" />)

    expect(screen.getByTestId('whatif-error')).toHaveTextContent('Something went wrong')
  })

  it('calls onClose when close button is clicked', () => {
    const onClose = vi.fn()
    render(<WhatIfPanel {...defaultProps} onClose={onClose} />)

    fireEvent.click(screen.getByTestId('whatif-close'))

    expect(onClose).toHaveBeenCalled()
  })

  it('calls onClose when Escape key is pressed', () => {
    const onClose = vi.fn()
    render(<WhatIfPanel {...defaultProps} onClose={onClose} />)

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onClose).toHaveBeenCalled()
  })

  it('shows ES in the comparison table', () => {
    render(
      <WhatIfPanel
        {...defaultProps}
        result={whatIfResponse}
        impact={impact}
      />,
    )

    expect(screen.getByTestId('whatif-es-base')).toHaveTextContent('130,000.00')
    expect(screen.getByTestId('whatif-es-after')).toHaveTextContent('110,000.00')
  })

  it('renders Theta row in comparison table', () => {
    render(
      <WhatIfPanel
        {...defaultProps}
        result={whatIfResponse}
        impact={impact}
      />,
    )

    expect(screen.getByTestId('whatif-theta-base')).toHaveTextContent('-500.00')
    expect(screen.getByTestId('whatif-theta-after')).toHaveTextContent('-450.00')
    expect(screen.getByTestId('whatif-theta-change')).toBeInTheDocument()
  })

  it('renders Rho row in comparison table', () => {
    render(
      <WhatIfPanel
        {...defaultProps}
        result={whatIfResponse}
        impact={impact}
      />,
    )

    expect(screen.getByTestId('whatif-rho-base')).toHaveTextContent('200.00')
    expect(screen.getByTestId('whatif-rho-after')).toHaveTextContent('180.00')
    expect(screen.getByTestId('whatif-rho-change')).toBeInTheDocument()
  })

  it('shows notional value per trade leg', () => {
    const trades = [{
      ...emptyTrade,
      instrumentId: 'SPY',
      quantity: '100',
      priceAmount: '450.00',
    }]

    render(<WhatIfPanel {...defaultProps} trades={trades} />)

    expect(screen.getByTestId('whatif-notional-0')).toHaveTextContent('45,000.00')
  })

  it('renders position risk breakdown when results have position risk data', () => {
    const responseWithPositionRisk: WhatIfResponseDto = {
      ...whatIfResponse,
      basePositionRisk: [
        {
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          marketValue: '17000.00',
          delta: '0.850000',
          gamma: '0.020000',
          vega: '1500.000000',
          varContribution: '5000.00',
          esContribution: '6250.00',
          percentageOfTotal: '100.00',
        },
      ],
      hypotheticalPositionRisk: [
        {
          instrumentId: 'AAPL',
          assetClass: 'EQUITY',
          marketValue: '20000.00',
          delta: '1.000000',
          gamma: '0.025000',
          vega: '1800.000000',
          varContribution: '7000.00',
          esContribution: '8750.00',
          percentageOfTotal: '100.00',
        },
      ],
    }

    render(
      <WhatIfPanel
        {...defaultProps}
        result={responseWithPositionRisk}
        impact={impact}
      />,
    )

    expect(screen.getByTestId('whatif-position-breakdown')).toBeInTheDocument()
    expect(screen.getByText('AAPL')).toBeInTheDocument()
  })

  it('calls onReset when reset button is clicked', () => {
    const onReset = vi.fn()
    render(
      <WhatIfPanel
        {...defaultProps}
        result={whatIfResponse}
        impact={impact}
        onReset={onReset}
      />,
    )

    fireEvent.click(screen.getByTestId('whatif-reset'))

    expect(onReset).toHaveBeenCalled()
  })

  it('has role="dialog" and aria-modal on root panel', () => {
    render(<WhatIfPanel {...defaultProps} />)

    const panel = screen.getByTestId('whatif-panel')
    expect(panel).toHaveAttribute('role', 'dialog')
    expect(panel).toHaveAttribute('aria-modal', 'true')
  })

  it('has aria-labelledby pointing to the title', () => {
    render(<WhatIfPanel {...defaultProps} />)

    const panel = screen.getByTestId('whatif-panel')
    expect(panel).toHaveAttribute('aria-labelledby', 'whatif-title')
    expect(screen.getByText('What-If Analysis')).toHaveAttribute('id', 'whatif-title')
  })

  it('has aria-label on close button', () => {
    render(<WhatIfPanel {...defaultProps} />)

    expect(screen.getByTestId('whatif-close')).toHaveAttribute('aria-label', 'Close what-if panel')
  })

  it('has aria-pressed on Buy/Sell buttons', () => {
    render(<WhatIfPanel {...defaultProps} />)

    expect(screen.getByTestId('whatif-side-buy-0')).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('whatif-side-sell-0')).toHaveAttribute('aria-pressed', 'false')
  })

  it('wraps results in aria-live polite region', () => {
    render(
      <WhatIfPanel
        {...defaultProps}
        result={whatIfResponse}
        impact={impact}
      />,
    )

    const liveRegion = screen.getByTestId('whatif-results-live')
    expect(liveRegion).toHaveAttribute('aria-live', 'polite')
  })

  it('renders a backdrop overlay that calls onClose', () => {
    const onClose = vi.fn()
    render(<WhatIfPanel {...defaultProps} onClose={onClose} />)

    const backdrop = screen.getByTestId('whatif-backdrop')
    fireEvent.click(backdrop)
    expect(onClose).toHaveBeenCalled()
  })

  it('applies dark mode classes to panel', () => {
    render(<WhatIfPanel {...defaultProps} />)

    const panel = screen.getByTestId('whatif-panel')
    expect(panel.className).toContain('dark:bg-surface-800')
  })

  it('has aria-label on remove trade button', () => {
    const trades = [emptyTrade, { ...emptyTrade, instrumentId: 'MSFT' }]
    render(<WhatIfPanel {...defaultProps} trades={trades} />)

    expect(screen.getByTestId('whatif-remove-trade-0')).toHaveAttribute('aria-label', 'Remove trade 1')
  })

  it('has aria-label on add trade button', () => {
    render(<WhatIfPanel {...defaultProps} />)

    expect(screen.getByTestId('whatif-add-trade')).toHaveAttribute('aria-label', 'Add another hypothetical trade')
  })
})
