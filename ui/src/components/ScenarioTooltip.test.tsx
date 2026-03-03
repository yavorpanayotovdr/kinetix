import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ScenarioTooltip } from './ScenarioTooltip'

const defaultProps = {
  scenarioName: 'GFC_2008',
  description: 'Global Financial Crisis 2008 - Lehman Brothers collapse',
  shocks: JSON.stringify({
    volShocks: { EQUITY: 3.0, FIXED_INCOME: 2.0 },
    priceShocks: { EQUITY: 0.6, FIXED_INCOME: 0.85 },
  }),
  lastRunAt: '2026-03-03T08:00:00Z',
  status: 'APPROVED',
  approvedBy: 'head@kinetix.com',
}

describe('ScenarioTooltip', () => {
  it('should display scenario description on hover', () => {
    render(<ScenarioTooltip {...defaultProps} />)

    const trigger = screen.getByText('GFC 2008')
    fireEvent.mouseEnter(trigger)

    expect(screen.getByText(/Lehman Brothers collapse/)).toBeInTheDocument()
  })

  it('should display vol and price shocks per asset class', () => {
    render(<ScenarioTooltip {...defaultProps} />)

    fireEvent.mouseEnter(screen.getByText('GFC 2008'))

    expect(screen.getByText('EQUITY')).toBeInTheDocument()
    expect(screen.getByText('3x')).toBeInTheDocument()
    expect(screen.getByText('0.6x')).toBeInTheDocument()
  })

  it('should display last run timestamp', () => {
    render(<ScenarioTooltip {...defaultProps} />)

    fireEvent.mouseEnter(screen.getByText('GFC 2008'))

    expect(screen.getByTestId('tooltip-last-run')).toBeInTheDocument()
  })

  it('should display approval status and approver', () => {
    render(<ScenarioTooltip {...defaultProps} />)

    fireEvent.mouseEnter(screen.getByText('GFC 2008'))

    expect(screen.getByText('APPROVED')).toBeInTheDocument()
    expect(screen.getByText('head@kinetix.com')).toBeInTheDocument()
  })

  it('should hide on mouse leave', () => {
    render(<ScenarioTooltip {...defaultProps} />)

    const trigger = screen.getByText('GFC 2008')
    fireEvent.mouseEnter(trigger)
    expect(screen.getByTestId('scenario-tooltip')).toBeInTheDocument()

    fireEvent.mouseLeave(trigger)
    expect(screen.queryByTestId('scenario-tooltip')).not.toBeInTheDocument()
  })

  it('should be accessible via keyboard focus', () => {
    render(<ScenarioTooltip {...defaultProps} />)

    const trigger = screen.getByText('GFC 2008')
    fireEvent.focus(trigger)

    expect(screen.getByTestId('scenario-tooltip')).toBeInTheDocument()

    fireEvent.blur(trigger)
    expect(screen.queryByTestId('scenario-tooltip')).not.toBeInTheDocument()
  })
})
