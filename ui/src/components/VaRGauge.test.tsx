import { render, screen, fireEvent } from '@testing-library/react'
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

  describe('limit-aware colouring', () => {
    it('uses green colour when VaR is below 60% of the limit', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const gauge = screen.getByTestId('var-gauge')
      const arc = gauge.querySelectorAll('path')[1]
      expect(arc.getAttribute('stroke')).toBe('#22c55e')
    })

    it('uses amber colour when VaR is between 60% and 85% of the limit', () => {
      render(
        <VaRGauge varValue={700000} expectedShortfall={900000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const gauge = screen.getByTestId('var-gauge')
      const arc = gauge.querySelectorAll('path')[1]
      expect(arc.getAttribute('stroke')).toBe('#f59e0b')
    })

    it('uses red colour when VaR is above 85% of the limit', () => {
      render(
        <VaRGauge varValue={900000} expectedShortfall={1100000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const gauge = screen.getByTestId('var-gauge')
      const arc = gauge.querySelectorAll('path')[1]
      expect(arc.getAttribute('stroke')).toBe('#ef4444')
    })

    it('displays the limit label when varLimit is provided', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const limitLabel = screen.getByTestId('var-limit')
      expect(limitLabel).toHaveTextContent('Limit $1,000,000.00')
    })

    it('does not display the limit label when varLimit is not provided', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" />,
      )

      expect(screen.queryByTestId('var-limit')).not.toBeInTheDocument()
    })

    it('falls back to ES-based colouring when varLimit is not provided', () => {
      // With ES-based colouring: ratio = varValue / (expectedShortfall * 1.5)
      // 500000 / (700000 * 1.5) = 500000 / 1050000 = 0.476 -> green
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" />,
      )

      const gauge = screen.getByTestId('var-gauge')
      const arc = gauge.querySelectorAll('path')[1]
      expect(arc.getAttribute('stroke')).toBe('#22c55e')
    })
  })

  describe('info popovers', () => {
    it('shows VaR explanation when info icon is clicked', () => {
      render(
        <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
      )

      fireEvent.click(screen.getByTestId('var-info'))

      const popover = screen.getByTestId('var-popover')
      expect(popover).toBeInTheDocument()
      expect(popover).toHaveTextContent('maximum expected loss')
    })

    it('shows ES explanation when info icon is clicked', () => {
      render(
        <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
      )

      fireEvent.click(screen.getByTestId('es-info'))

      const popover = screen.getByTestId('es-popover')
      expect(popover).toBeInTheDocument()
      expect(popover).toHaveTextContent('average loss')
    })

    it('closes popover when the same info icon is clicked again', () => {
      render(
        <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
      )

      fireEvent.click(screen.getByTestId('var-info'))
      expect(screen.getByTestId('var-popover')).toBeInTheDocument()

      fireEvent.click(screen.getByTestId('var-info'))
      expect(screen.queryByTestId('var-popover')).not.toBeInTheDocument()
    })

    it('closes popover when Escape is pressed', () => {
      render(
        <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
      )

      fireEvent.click(screen.getByTestId('es-info'))
      expect(screen.getByTestId('es-popover')).toBeInTheDocument()

      fireEvent.keyDown(document, { key: 'Escape' })
      expect(screen.queryByTestId('es-popover')).not.toBeInTheDocument()
    })

    it('closes popover when clicking outside', () => {
      render(
        <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
      )

      fireEvent.click(screen.getByTestId('var-info'))
      expect(screen.getByTestId('var-popover')).toBeInTheDocument()

      fireEvent.mouseDown(document.body)
      expect(screen.queryByTestId('var-popover')).not.toBeInTheDocument()
    })

    it('shows only one popover at a time', () => {
      render(
        <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
      )

      fireEvent.click(screen.getByTestId('var-info'))
      expect(screen.getByTestId('var-popover')).toBeInTheDocument()

      fireEvent.click(screen.getByTestId('es-info'))
      expect(screen.queryByTestId('var-popover')).not.toBeInTheDocument()
      expect(screen.getByTestId('es-popover')).toBeInTheDocument()
    })

    it('closes popover when close button is clicked', () => {
      render(
        <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
      )

      fireEvent.click(screen.getByTestId('var-info'))
      expect(screen.getByTestId('var-popover')).toBeInTheDocument()

      fireEvent.click(screen.getByTestId('var-popover-close'))
      expect(screen.queryByTestId('var-popover')).not.toBeInTheDocument()
    })
  })
})
