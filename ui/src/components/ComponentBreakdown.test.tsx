import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ComponentBreakdown } from './ComponentBreakdown'

describe('ComponentBreakdown', () => {
  const breakdown = [
    { assetClass: 'EQUITY', varContribution: '800000.00', percentageOfTotal: '64.85' },
    { assetClass: 'FIXED_INCOME', varContribution: '300000.00', percentageOfTotal: '24.30' },
    { assetClass: 'COMMODITY', varContribution: '134567.89', percentageOfTotal: '10.85' },
  ]

  it('renders a donut segment for each asset class', () => {
    render(<ComponentBreakdown breakdown={breakdown} />)

    expect(screen.getByTestId('breakdown-segment-EQUITY')).toBeInTheDocument()
    expect(screen.getByTestId('breakdown-segment-FIXED_INCOME')).toBeInTheDocument()
    expect(screen.getByTestId('breakdown-segment-COMMODITY')).toBeInTheDocument()
  })

  it('renders legend rows with formatted dollar values and percentages', () => {
    render(<ComponentBreakdown breakdown={breakdown} />)

    expect(screen.getByTestId('breakdown-EQUITY')).toHaveTextContent('Equity')
    expect(screen.getByTestId('breakdown-EQUITY')).toHaveTextContent('$800,000.00')
    expect(screen.getByTestId('breakdown-EQUITY')).toHaveTextContent('64.85%')

    expect(screen.getByTestId('breakdown-FIXED_INCOME')).toHaveTextContent('Fixed Income')
    expect(screen.getByTestId('breakdown-FIXED_INCOME')).toHaveTextContent('$300,000.00')
    expect(screen.getByTestId('breakdown-FIXED_INCOME')).toHaveTextContent('24.30%')

    expect(screen.getByTestId('breakdown-COMMODITY')).toHaveTextContent('Commodity')
    expect(screen.getByTestId('breakdown-COMMODITY')).toHaveTextContent('$134,567.89')
    expect(screen.getByTestId('breakdown-COMMODITY')).toHaveTextContent('10.85%')
  })

  it('handles empty breakdown array gracefully', () => {
    render(<ComponentBreakdown breakdown={[]} />)

    expect(screen.getByText('Component Breakdown')).toBeInTheDocument()
    expect(screen.queryByTestId(/breakdown-segment-/)).not.toBeInTheDocument()
  })

  it('handles single-item breakdown as a full ring', () => {
    const single = [{ assetClass: 'FX', varContribution: '500000.00', percentageOfTotal: '100.00' }]

    render(<ComponentBreakdown breakdown={single} />)

    expect(screen.getByTestId('breakdown-segment-FX')).toBeInTheDocument()
    expect(screen.getByTestId('breakdown-FX')).toHaveTextContent('FX')
    expect(screen.getByTestId('breakdown-FX')).toHaveTextContent('$500,000.00')
    expect(screen.getByTestId('breakdown-FX')).toHaveTextContent('100.00%')
  })

  it('formats asset class labels from SCREAMING_SNAKE to Title Case', () => {
    render(<ComponentBreakdown breakdown={breakdown} />)

    expect(screen.getByTestId('breakdown-FIXED_INCOME')).toHaveTextContent('Fixed Income')
    expect(screen.getByTestId('breakdown-EQUITY')).toHaveTextContent('Equity')
    expect(screen.getByTestId('breakdown-COMMODITY')).toHaveTextContent('Commodity')
  })

  it('sorts donut segments by percentage descending', () => {
    render(<ComponentBreakdown breakdown={breakdown} />)

    const segments = screen.getAllByTestId(/breakdown-segment-/)
    expect(segments[0]).toHaveAttribute('data-testid', 'breakdown-segment-EQUITY')
    expect(segments[1]).toHaveAttribute('data-testid', 'breakdown-segment-FIXED_INCOME')
    expect(segments[2]).toHaveAttribute('data-testid', 'breakdown-segment-COMMODITY')
  })

  it('lays out legend and donut side by side with gap', () => {
    const { container } = render(<ComponentBreakdown breakdown={breakdown} />)

    const flexContainer = container.querySelector('.flex.items-center.gap-8')!
    expect(flexContainer).toBeTruthy()
  })

  it('renders donut at 130x130', () => {
    render(<ComponentBreakdown breakdown={breakdown} />)

    const svg = document.querySelector('svg')!
    expect(svg).toHaveAttribute('width', '130')
    expect(svg).toHaveAttribute('height', '130')
  })

  describe('diversification benefit', () => {
    it('displays diversification benefit when portfolioVaR is provided', () => {
      // Sum of component VaRs: 800000 + 300000 + 134567.89 = 1234567.89
      // Portfolio VaR: 1000000
      // Benefit: 1234567.89 - 1000000 = 234567.89
      // Percentage: 234567.89 / 1234567.89 * 100 ≈ 19.00%
      render(<ComponentBreakdown breakdown={breakdown} portfolioVaR="1000000.00" />)

      const benefit = screen.getByTestId('diversification-benefit')
      expect(benefit).toBeInTheDocument()
      expect(benefit).toHaveTextContent('Diversification')
      expect(benefit).toHaveTextContent('-$234,567.89')
      expect(benefit).toHaveTextContent('19.00%')
    })

    it('does not display diversification benefit when portfolioVaR is not provided', () => {
      render(<ComponentBreakdown breakdown={breakdown} />)

      expect(screen.queryByTestId('diversification-benefit')).not.toBeInTheDocument()
    })

    it('does not display diversification benefit when breakdown is empty', () => {
      render(<ComponentBreakdown breakdown={[]} portfolioVaR="1000000.00" />)

      expect(screen.queryByTestId('diversification-benefit')).not.toBeInTheDocument()
    })

    it('shows zero benefit when sum equals portfolio VaR', () => {
      const simple = [
        { assetClass: 'EQUITY', varContribution: '1000000.00', percentageOfTotal: '100.00' },
      ]
      render(<ComponentBreakdown breakdown={simple} portfolioVaR="1000000.00" />)

      const benefit = screen.getByTestId('diversification-benefit')
      expect(benefit).toHaveTextContent('$0.00')
      expect(benefit).toHaveTextContent('0.00%')
    })

    it('applies green color to the benefit amount', () => {
      render(<ComponentBreakdown breakdown={breakdown} portfolioVaR="1000000.00" />)

      const benefit = screen.getByTestId('diversification-benefit')
      expect(benefit.querySelector('[data-testid="diversification-amount"]')).toHaveClass('text-green-600')
    })
  })
})
