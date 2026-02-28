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
})
