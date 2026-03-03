import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ScenarioGovernancePanel } from './ScenarioGovernancePanel'
import { makeScenario } from '../test-utils/stressMocks'

describe('ScenarioGovernancePanel', () => {
  const defaultProps = {
    scenarios: [
      makeScenario({ id: '1', name: 'Draft Scenario', status: 'DRAFT' }),
      makeScenario({ id: '2', name: 'Pending Scenario', status: 'PENDING_APPROVAL' }),
      makeScenario({ id: '3', name: 'Approved Scenario', status: 'APPROVED', approvedBy: 'head@kinetix.com' }),
      makeScenario({ id: '4', name: 'Retired Scenario', status: 'RETIRED' }),
    ],
    onSubmit: vi.fn(),
    onApprove: vi.fn(),
    onRetire: vi.fn(),
    loading: false,
  }

  it('should render all scenarios with status badges', () => {
    render(<ScenarioGovernancePanel {...defaultProps} />)

    expect(screen.getByText('Draft Scenario')).toBeInTheDocument()
    expect(screen.getByText('Pending Scenario')).toBeInTheDocument()
    expect(screen.getByText('Approved Scenario')).toBeInTheDocument()
    expect(screen.getByText('Retired Scenario')).toBeInTheDocument()
    expect(screen.getByText('DRAFT')).toBeInTheDocument()
    expect(screen.getByText('PENDING_APPROVAL')).toBeInTheDocument()
    expect(screen.getByText('APPROVED')).toBeInTheDocument()
    expect(screen.getByText('RETIRED')).toBeInTheDocument()
  })

  it('should show Submit for Approval button on DRAFT scenarios', () => {
    render(<ScenarioGovernancePanel {...defaultProps} />)

    const submitButtons = screen.getAllByText('Submit for Approval')
    expect(submitButtons).toHaveLength(1)
  })

  it('should show Approve button on PENDING_APPROVAL scenarios', () => {
    render(<ScenarioGovernancePanel {...defaultProps} />)

    const approveButtons = screen.getAllByText('Approve')
    expect(approveButtons).toHaveLength(1)
  })

  it('should show Retire button on APPROVED scenarios', () => {
    render(<ScenarioGovernancePanel {...defaultProps} />)

    const retireButtons = screen.getAllByText('Retire')
    expect(retireButtons).toHaveLength(1)
  })

  it('should call onSubmit when Submit for Approval is clicked', () => {
    render(<ScenarioGovernancePanel {...defaultProps} />)

    fireEvent.click(screen.getByText('Submit for Approval'))
    expect(defaultProps.onSubmit).toHaveBeenCalledWith('1')
  })

  it('should not show action buttons on RETIRED scenarios', () => {
    const retiredOnly = {
      ...defaultProps,
      scenarios: [makeScenario({ id: '4', name: 'Retired Scenario', status: 'RETIRED' })],
    }
    render(<ScenarioGovernancePanel {...retiredOnly} />)

    expect(screen.queryByText('Submit for Approval')).not.toBeInTheDocument()
    expect(screen.queryByText('Approve')).not.toBeInTheDocument()
    expect(screen.queryByText('Retire')).not.toBeInTheDocument()
  })
})
