import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AssetClassImpactView } from './AssetClassImpactView'
import { makeAssetClassImpact } from '../test-utils/stressMocks'

const impacts = [
  makeAssetClassImpact({ assetClass: 'EQUITY', baseExposure: '1000000.00', stressedExposure: '600000.00', pnlImpact: '-400000.00' }),
  makeAssetClassImpact({ assetClass: 'COMMODITY', baseExposure: '500000.00', stressedExposure: '350000.00', pnlImpact: '-150000.00' }),
]

describe('AssetClassImpactView', () => {
  it('should render base and stressed bars for each asset class', () => {
    render(<AssetClassImpactView impacts={impacts} onAssetClassClick={vi.fn()} />)

    expect(screen.getByTestId('asset-class-impact-view')).toBeInTheDocument()
    expect(screen.getByTitle(/Base:.*\$1,000,000/)).toBeInTheDocument()
    expect(screen.getByTitle(/Stressed:.*\$600,000/)).toBeInTheDocument()
    expect(screen.getByTitle(/Base:.*\$500,000/)).toBeInTheDocument()
    expect(screen.getByTitle(/Stressed:.*\$350,000/)).toBeInTheDocument()
  })

  it('should call onAssetClassClick when asset class label is clicked', () => {
    const onClick = vi.fn()
    render(<AssetClassImpactView impacts={impacts} onAssetClassClick={onClick} />)

    fireEvent.click(screen.getByTestId('asset-class-click-EQUITY'))
    expect(onClick).toHaveBeenCalledWith('EQUITY')
  })

  it('should display P&L impact per asset class', () => {
    render(<AssetClassImpactView impacts={impacts} onAssetClassClick={vi.fn()} />)

    const pnls = screen.getAllByTestId('asset-class-pnl')
    expect(pnls[0]).toHaveTextContent('-$400,000')
    expect(pnls[1]).toHaveTextContent('-$150,000')
  })

  it('should render empty state when no impacts provided', () => {
    render(<AssetClassImpactView impacts={[]} onAssetClassClick={vi.fn()} />)

    expect(screen.getByText('No asset class data')).toBeInTheDocument()
  })
})
