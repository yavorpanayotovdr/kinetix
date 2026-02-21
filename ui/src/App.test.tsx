import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PositionDto, VaRResultDto } from './types'

vi.mock('./hooks/usePositions')
vi.mock('./hooks/usePriceStream')
vi.mock('./hooks/useVaR')

import App from './App'
import { usePositions } from './hooks/usePositions'
import { usePriceStream } from './hooks/usePriceStream'
import { useVaR } from './hooks/useVaR'

const mockUsePositions = vi.mocked(usePositions)
const mockUsePriceStream = vi.mocked(usePriceStream)
const mockUseVaR = vi.mocked(useVaR)

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

const varResult: VaRResultDto = {
  portfolioId: 'port-1',
  calculationType: 'HISTORICAL',
  confidenceLevel: 'CL_95',
  varValue: '1234567.89',
  expectedShortfall: '1567890.12',
  componentBreakdown: [
    { assetClass: 'EQUITY', varContribution: '800000.00', percentageOfTotal: '64.85' },
  ],
  calculatedAt: '2025-01-15T10:30:00Z',
}

describe('App', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockUsePriceStream.mockReturnValue({ positions: [], connected: false })
    mockUseVaR.mockReturnValue({
      varResult: null,
      history: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })
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

  it('renders VaR dashboard alongside position grid', () => {
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
    mockUseVaR.mockReturnValue({
      varResult,
      history: [
        { varValue: 1234567.89, expectedShortfall: 1567890.12, calculatedAt: '2025-01-15T10:30:00Z' },
      ],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    render(<App />)

    expect(screen.getByTestId('var-dashboard')).toBeInTheDocument()
    expect(screen.getByTestId('position-row-AAPL')).toBeInTheDocument()
  })

  it('shows VaR empty state without affecting position grid', () => {
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
    mockUseVaR.mockReturnValue({
      varResult: null,
      history: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    render(<App />)

    expect(screen.getByTestId('var-empty')).toBeInTheDocument()
    expect(screen.getByTestId('position-row-AAPL')).toBeInTheDocument()
  })
})
