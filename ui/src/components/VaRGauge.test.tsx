import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { VaRGauge } from './VaRGauge'

describe('VaRGauge', () => {
  it('renders the SVG gauge', () => {
    render(
      <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
    )

    const gauge = screen.getByTestId('var-gauge')
    expect(gauge.querySelector('svg')).toBeInTheDocument()
  })

  it('displays the formatted VaR value', () => {
    render(
      <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
    )

    const valueEl = screen.getByTestId('var-value')
    expect(valueEl).toHaveTextContent('$1,234,567.89')
  })

  it('displays 95% confidence label', () => {
    render(
      <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
    )

    const label = screen.getByTestId('var-confidence')
    expect(label).toHaveTextContent('VaR (95%)')
  })

  it('displays 99% confidence label', () => {
    render(
      <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_99" />,
    )

    const label = screen.getByTestId('var-confidence')
    expect(label).toHaveTextContent('VaR (99%)')
  })

  it('displays the expected shortfall value', () => {
    render(
      <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
    )

    const esEl = screen.getByTestId('es-value')
    expect(esEl).toHaveTextContent('$1,567,890.12')
  })
})
