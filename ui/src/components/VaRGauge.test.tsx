import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { VaRGauge } from './VaRGauge'

describe('VaRGauge', () => {
  it('renders the compact card layout without a semi-circle arc', () => {
    render(
      <VaRGauge varValue={1234567.89} expectedShortfall={1567890.12} confidenceLevel="CL_95" />,
    )

    const gauge = screen.getByTestId('var-gauge')
    expect(gauge.querySelector('path[d*="A 80 80"]')).not.toBeInTheDocument()
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

  describe('status dot', () => {
    it('renders a status dot next to the VaR value', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" />,
      )

      const dot = screen.getByTestId('var-status-dot')
      expect(dot).toBeInTheDocument()
    })

    it('colours the status dot green when VaR is low relative to ES', () => {
      // ES-based: ratio = 500000 / (700000 * 1.5) = 0.476 -> green
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" />,
      )

      const dot = screen.getByTestId('var-status-dot')
      expect(dot).toHaveStyle({ backgroundColor: '#22c55e' })
    })

    it('colours the status dot amber when VaR is moderate relative to ES', () => {
      // ES-based: ratio = 600000 / (700000 * 1.5) = 0.571 -> amber
      render(
        <VaRGauge varValue={600000} expectedShortfall={700000} confidenceLevel="CL_95" />,
      )

      const dot = screen.getByTestId('var-status-dot')
      expect(dot).toHaveStyle({ backgroundColor: '#f59e0b' })
    })

    it('colours the status dot red when VaR is high relative to ES', () => {
      // ES-based: ratio = 900000 / (700000 * 1.5) = 0.857 -> red
      render(
        <VaRGauge varValue={900000} expectedShortfall={700000} confidenceLevel="CL_95" />,
      )

      const dot = screen.getByTestId('var-status-dot')
      expect(dot).toHaveStyle({ backgroundColor: '#ef4444' })
    })
  })

  describe('PV display', () => {
    it('displays PV value when pvValue is provided', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" pvValue="$12.4M" />,
      )

      const pvEl = screen.getByTestId('var-pv')
      expect(pvEl).toHaveTextContent('PV')
      expect(pvEl).toHaveTextContent('$12.4M')
    })

    it('does not display PV row when pvValue is not provided', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" />,
      )

      expect(screen.queryByTestId('var-pv')).not.toBeInTheDocument()
    })
  })

  describe('limit-aware colouring', () => {
    it('uses green colour when VaR is below 60% of the limit', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const dot = screen.getByTestId('var-status-dot')
      expect(dot).toHaveStyle({ backgroundColor: '#22c55e' })
    })

    it('uses amber colour when VaR is between 60% and 85% of the limit', () => {
      render(
        <VaRGauge varValue={700000} expectedShortfall={900000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const dot = screen.getByTestId('var-status-dot')
      expect(dot).toHaveStyle({ backgroundColor: '#f59e0b' })
    })

    it('uses red colour when VaR is above 85% of the limit', () => {
      render(
        <VaRGauge varValue={900000} expectedShortfall={1100000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const dot = screen.getByTestId('var-status-dot')
      expect(dot).toHaveStyle({ backgroundColor: '#ef4444' })
    })

    it('displays the limit label with utilisation percentage when varLimit is provided', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const limitLabel = screen.getByTestId('var-limit')
      expect(limitLabel).toHaveTextContent('Limit')
      expect(limitLabel).toHaveTextContent('50%')
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

      const dot = screen.getByTestId('var-status-dot')
      expect(dot).toHaveStyle({ backgroundColor: '#22c55e' })
    })
  })

  describe('progress bar', () => {
    it('renders a progress bar when varLimit is provided', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const bar = screen.getByTestId('var-limit-bar')
      expect(bar).toBeInTheDocument()
    })

    it('does not render a progress bar when varLimit is not provided', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" />,
      )

      expect(screen.queryByTestId('var-limit-bar')).not.toBeInTheDocument()
    })

    it('does not render a progress bar when varLimit is zero', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" varLimit={0} />,
      )

      expect(screen.queryByTestId('var-limit-bar')).not.toBeInTheDocument()
      expect(screen.queryByTestId('var-limit')).not.toBeInTheDocument()
    })

    it('fills the progress bar based on utilisation percentage', () => {
      render(
        <VaRGauge varValue={500000} expectedShortfall={700000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const fill = screen.getByTestId('var-limit-bar-fill')
      expect(fill).toHaveStyle({ width: '50%' })
    })

    it('colours the progress bar fill using the gauge colour', () => {
      render(
        <VaRGauge varValue={700000} expectedShortfall={900000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const fill = screen.getByTestId('var-limit-bar-fill')
      expect(fill).toHaveStyle({ backgroundColor: '#f59e0b' })
    })

    it('caps the progress bar fill at 100%', () => {
      render(
        <VaRGauge varValue={1500000} expectedShortfall={1800000} confidenceLevel="CL_95" varLimit={1000000} />,
      )

      const fill = screen.getByTestId('var-limit-bar-fill')
      expect(fill).toHaveStyle({ width: '100%' })
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

  describe('VaR change indicator', () => {
    it('shows increase with red text and up arrow when VaR rises', () => {
      render(
        <VaRGauge varValue={1050000} expectedShortfall={1300000} confidenceLevel="CL_95" previousVaR={1000000} />,
      )

      const change = screen.getByTestId('var-change')
      expect(change).toBeInTheDocument()
      expect(change).toHaveTextContent('$50,000.00')
      expect(change).toHaveTextContent('+5.0%')
      expect(change.className).toContain('text-red-600')
    })

    it('shows decrease with green text and down arrow when VaR falls', () => {
      render(
        <VaRGauge varValue={950000} expectedShortfall={1200000} confidenceLevel="CL_95" previousVaR={1000000} />,
      )

      const change = screen.getByTestId('var-change')
      expect(change).toBeInTheDocument()
      expect(change).toHaveTextContent('$50,000.00')
      expect(change).toHaveTextContent('-5.0%')
      expect(change.className).toContain('text-green-600')
    })

    it('shows neutral grey when VaR is unchanged', () => {
      render(
        <VaRGauge varValue={1000000} expectedShortfall={1300000} confidenceLevel="CL_95" previousVaR={1000000} />,
      )

      const change = screen.getByTestId('var-change')
      expect(change).toHaveTextContent('$0.00')
      expect(change).toHaveTextContent('0.0%')
      expect(change.className).toContain('text-slate-400')
    })

    it('hides change indicator when previousVaR is not provided', () => {
      render(
        <VaRGauge varValue={1000000} expectedShortfall={1300000} confidenceLevel="CL_95" />,
      )

      expect(screen.queryByTestId('var-change')).not.toBeInTheDocument()
    })

    it('shows only absolute change when previousVaR is zero', () => {
      render(
        <VaRGauge varValue={50000} expectedShortfall={70000} confidenceLevel="CL_95" previousVaR={0} />,
      )

      const change = screen.getByTestId('var-change')
      expect(change).toHaveTextContent('$50,000.00')
      expect(change).not.toHaveTextContent('%')
    })
  })
})
