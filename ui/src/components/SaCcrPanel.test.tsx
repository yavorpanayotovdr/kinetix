import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SaCcrPanel } from './SaCcrPanel'

const TEST_RESULT = {
  nettingSetId: 'CP-GS-SA-CCR',
  counterpartyId: 'CP-GS',
  replacementCost: 2100000,
  pfeAddon: 1013000,
  multiplier: 0.995,
  ead: 4218000,
  alpha: 1.4,
}

describe('SaCcrPanel', () => {
  it('renders loading state', () => {
    render(<SaCcrPanel result={null} loading={true} error={null} />)
    expect(screen.getByTestId('sa-ccr-loading')).toBeVisible()
  })

  it('renders error state', () => {
    render(<SaCcrPanel result={null} loading={false} error="Counterparty not found" />)
    expect(screen.getByTestId('sa-ccr-error')).toHaveTextContent('Counterparty not found')
  })

  it('renders empty state when no result', () => {
    render(<SaCcrPanel result={null} loading={false} error={null} />)
    expect(screen.getByTestId('sa-ccr-empty')).toBeVisible()
  })

  it('renders EAD, RC, PFE add-on, and multiplier', () => {
    render(<SaCcrPanel result={TEST_RESULT} loading={false} error={null} />)
    expect(screen.getByTestId('sa-ccr-panel')).toBeVisible()
    expect(screen.getByTestId('sa-ccr-ead')).toBeVisible()
    expect(screen.getByTestId('sa-ccr-rc')).toBeVisible()
    expect(screen.getByTestId('sa-ccr-pfe')).toBeVisible()
    expect(screen.getByTestId('sa-ccr-multiplier')).toBeVisible()
  })

  it('displays regulatory label clearly', () => {
    render(<SaCcrPanel result={TEST_RESULT} loading={false} error={null} />)
    expect(screen.getByText(/Regulatory Capital.*SA-CCR.*BCBS 279/)).toBeVisible()
    expect(screen.getByText(/Distinct from internal Monte Carlo PFE/)).toBeVisible()
  })

  it('labels PFE as SA-CCR PFE Add-on to avoid conflation', () => {
    render(<SaCcrPanel result={TEST_RESULT} loading={false} error={null} />)
    expect(screen.getByText('SA-CCR PFE Add-on')).toBeVisible()
  })
})
