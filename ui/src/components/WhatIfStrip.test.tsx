import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { GreeksResultDto } from '../types'
import { WhatIfStrip } from './WhatIfStrip'

const greeksResult: GreeksResultDto = {
  portfolioId: 'port-1',
  assetClassGreeks: [
    { assetClass: 'EQUITY', delta: '1234.560000', gamma: '78.900000', vega: '5678.120000' },
    { assetClass: 'COMMODITY', delta: '567.890000', gamma: '12.340000', vega: '2345.670000' },
  ],
  theta: '-123.450000',
  rho: '456.780000',
  calculatedAt: '2025-01-15T10:00:00Z',
}

describe('WhatIfStrip', () => {
  it('renders the slider and what-if section', () => {
    render(<WhatIfStrip greeksResult={greeksResult} volBump={0} onVolBumpChange={() => {}} />)

    expect(screen.getByTestId('greeks-whatif')).toBeInTheDocument()
    expect(screen.getByTestId('vol-bump-slider')).toBeInTheDocument()
  })

  it('calls onVolBumpChange when the slider changes', () => {
    const onChange = vi.fn()
    render(<WhatIfStrip greeksResult={greeksResult} volBump={0} onVolBumpChange={onChange} />)

    fireEvent.change(screen.getByTestId('vol-bump-slider'), { target: { value: '2.5' } })

    expect(onChange).toHaveBeenCalledWith(2.5)
  })

  it('displays projected VaR change based on vega and volBump', () => {
    const volBump = 1
    const expectedChange = (5678.12 + 2345.67) * volBump

    render(<WhatIfStrip greeksResult={greeksResult} volBump={volBump} onVolBumpChange={() => {}} />)

    const whatif = screen.getByTestId('greeks-whatif')
    expect(whatif).toHaveTextContent('Projected VaR change')
    expect(whatif).toHaveTextContent(expectedChange.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
  })

  it('shows positive change in red and negative in green', () => {
    const { rerender } = render(
      <WhatIfStrip greeksResult={greeksResult} volBump={1} onVolBumpChange={() => {}} />,
    )

    expect(screen.getByTestId('greeks-whatif').querySelector('.text-red-600')).toBeInTheDocument()

    rerender(<WhatIfStrip greeksResult={greeksResult} volBump={-1} onVolBumpChange={() => {}} />)

    expect(screen.getByTestId('greeks-whatif').querySelector('.text-green-600')).toBeInTheDocument()
  })
})
