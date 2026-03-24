import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { HierarchyNodeRiskDto } from '../types'
import { RiskBudgetPanel } from './RiskBudgetPanel'

const baseNode: HierarchyNodeRiskDto = {
  level: 'FIRM',
  entityId: 'FIRM',
  entityName: 'FIRM',
  parentId: null,
  varValue: '2500000.00',
  expectedShortfall: '3125000.00',
  pnlToday: null,
  limitUtilisation: null,
  marginalVar: null,
  incrementalVar: null,
  topContributors: [],
  childCount: 2,
  isPartial: false,
  missingBooks: [],
}

describe('RiskBudgetPanel', () => {
  it('returns null when limitUtilisation is null', () => {
    const { container } = render(<RiskBudgetPanel node={baseNode} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders utilisation bar when limitUtilisation is provided', () => {
    const node = { ...baseNode, limitUtilisation: '60.00' }
    render(<RiskBudgetPanel node={node} />)

    expect(screen.getByTestId('risk-budget-panel')).toBeInTheDocument()
    expect(screen.getByTestId('budget-utilisation-bar')).toBeInTheDocument()
  })

  it('shows the utilisation percentage label', () => {
    const node = { ...baseNode, limitUtilisation: '60.00' }
    render(<RiskBudgetPanel node={node} />)

    expect(screen.getByText('60.0%')).toBeInTheDocument()
  })

  it('renders a green bar when utilisation is below warning threshold (80%)', () => {
    const node = { ...baseNode, limitUtilisation: '60.00' }
    render(<RiskBudgetPanel node={node} />)

    const fill = screen.getByTestId('budget-bar-fill')
    expect(fill.className).toMatch(/bg-emerald|green/)
  })

  it('renders an amber bar when utilisation is at warning level (80%-99%)', () => {
    const node = { ...baseNode, limitUtilisation: '85.00' }
    render(<RiskBudgetPanel node={node} />)

    const fill = screen.getByTestId('budget-bar-fill')
    expect(fill.className).toMatch(/bg-amber|yellow/)
  })

  it('renders a red bar when utilisation is at or above 100%', () => {
    const node = { ...baseNode, limitUtilisation: '110.00' }
    render(<RiskBudgetPanel node={node} />)

    const fill = screen.getByTestId('budget-bar-fill')
    expect(fill.className).toMatch(/bg-red/)
  })

  it('caps bar width at 100% for over-budget utilisation', () => {
    const node = { ...baseNode, limitUtilisation: '120.00' }
    render(<RiskBudgetPanel node={node} />)

    const fill = screen.getByTestId('budget-bar-fill')
    // Width style should be 100%, not 120%
    expect(fill).toHaveStyle({ width: '100%' })
  })

  it('shows "VaR Budget" label', () => {
    const node = { ...baseNode, limitUtilisation: '50.00' }
    render(<RiskBudgetPanel node={node} />)

    expect(screen.getByText(/VaR Budget/)).toBeInTheDocument()
  })
})
