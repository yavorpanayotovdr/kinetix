import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PositionDto } from './types'

vi.mock('./hooks/usePositions')
vi.mock('./hooks/usePriceStream')

import App from './App'
import { usePositions } from './hooks/usePositions'
import { usePriceStream } from './hooks/usePriceStream'

const mockUsePositions = vi.mocked(usePositions)
const mockUsePriceStream = vi.mocked(usePriceStream)

const position: PositionDto = {
  portfolioId: 'port-1',
  instrumentId: 'AAPL',
  assetClass: 'EQUITY',
  quantity: '100',
  averageCost: { amount: '150.00', currency: 'USD' },
  marketPrice: { amount: '155.00', currency: 'USD' },
  marketValue: { amount: '15500.00', currency: 'USD' },
  unrealizedPnl: { amount: '500.00', currency: 'USD' },
}

describe('App', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockUsePriceStream.mockReturnValue({ positions: [], connected: false })
  })

  it('renders Kinetix heading', () => {
    mockUsePositions.mockReturnValue({
      positions: [],
      portfolioId: null,
      loading: false,
      error: null,
    })

    render(<App />)

    expect(screen.getByText('Kinetix')).toBeInTheDocument()
  })

  it('shows loading message while fetching', () => {
    mockUsePositions.mockReturnValue({
      positions: [],
      portfolioId: null,
      loading: true,
      error: null,
    })

    render(<App />)

    expect(screen.getByText('Loading positions...')).toBeInTheDocument()
  })

  it('shows error message on failure', () => {
    mockUsePositions.mockReturnValue({
      positions: [],
      portfolioId: null,
      loading: false,
      error: 'Network error',
    })

    render(<App />)

    expect(screen.getByText('Network error')).toBeInTheDocument()
  })

  it('renders position grid when loaded', () => {
    mockUsePositions.mockReturnValue({
      positions: [position],
      portfolioId: 'port-1',
      loading: false,
      error: null,
    })
    mockUsePriceStream.mockReturnValue({
      positions: [position],
      connected: true,
    })

    render(<App />)

    expect(screen.getByText('port-1')).toBeInTheDocument()
    expect(screen.getByTestId('position-row-AAPL')).toBeInTheDocument()
    expect(screen.getByTestId('connection-status')).toHaveTextContent('Live')
  })
})
