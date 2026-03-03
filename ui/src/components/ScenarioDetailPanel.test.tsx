import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ScenarioDetailPanel } from './ScenarioDetailPanel'
import { makeStressResult } from '../test-utils/stressMocks'

const result = makeStressResult()

describe('ScenarioDetailPanel', () => {
  it('should render Asset Class view by default when scenario selected', () => {
    render(<ScenarioDetailPanel result={result} />)

    expect(screen.getByTestId('detail-panel')).toBeInTheDocument()
    expect(screen.getByTestId('asset-class-impact-view')).toBeInTheDocument()
  })

  it('should switch to Position view when Positions toggle is clicked', () => {
    render(<ScenarioDetailPanel result={result} />)

    fireEvent.click(screen.getByTestId('view-toggle-positions'))
    expect(screen.getByTestId('stress-position-table')).toBeInTheDocument()
    expect(screen.queryByTestId('asset-class-impact-view')).not.toBeInTheDocument()
  })

  it('should not render when result is null', () => {
    const { container } = render(<ScenarioDetailPanel result={null} />)

    expect(container.firstChild).toBeNull()
  })

  it('should have aria-live polite region for dynamic content', () => {
    render(<ScenarioDetailPanel result={result} />)

    expect(screen.getByTestId('detail-panel')).toHaveAttribute('aria-live', 'polite')
  })

  it('should show active style on selected view toggle', () => {
    render(<ScenarioDetailPanel result={result} />)

    const assetClassBtn = screen.getByTestId('view-toggle-asset-class')
    const positionsBtn = screen.getByTestId('view-toggle-positions')

    expect(assetClassBtn.className).toContain('bg-indigo-600')
    expect(positionsBtn.className).not.toContain('bg-indigo-600')
  })

  it('should pass asset class filter when switching from AssetClassImpactView to positions', () => {
    render(<ScenarioDetailPanel result={result} />)

    // Click on asset class in the impact view triggers position view with filter
    fireEvent.click(screen.getByTestId('asset-class-click-EQUITY'))
    expect(screen.getByTestId('stress-position-table')).toBeInTheDocument()
    expect(screen.getByTestId('filter-pill')).toHaveTextContent('EQUITY')
  })
})
