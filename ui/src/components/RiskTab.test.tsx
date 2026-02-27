import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../hooks/useVaR')
vi.mock('../hooks/useJobHistory')

import { RiskTab } from './RiskTab'
import { useVaR } from '../hooks/useVaR'
import { useJobHistory } from '../hooks/useJobHistory'

const mockUseVaR = vi.mocked(useVaR)
const mockUseJobHistory = vi.mocked(useJobHistory)

describe('RiskTab', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockUseVaR.mockReturnValue({
      varResult: null,
      greeksResult: null,
      history: [],
      filteredHistory: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
      refreshing: false,
      timeRange: { from: '2025-01-14T10:30:00Z', to: '2025-01-15T10:30:00Z', label: 'Last 24h' },
      setTimeRange: vi.fn(),
      zoomIn: vi.fn(),
      resetZoom: vi.fn(),
      zoomDepth: 0,
    })
    mockUseJobHistory.mockReturnValue({
      runs: [],
      expandedJobs: {},
      loadingJobIds: new Set(),
      loading: false,
      error: null,
      timeRange: { from: '2025-01-14T10:00:00Z', to: '2025-01-15T10:00:00Z', label: 'Last 24h' },
      setTimeRange: vi.fn(),
      toggleJob: vi.fn(),
      closeJob: vi.fn(),
      clearSelection: vi.fn(),
      refresh: vi.fn(),
      zoomIn: vi.fn(),
      resetZoom: vi.fn(),
      zoomDepth: 0,
      page: 1,
      totalPages: 1,
      hasNextPage: false,
      nextPage: vi.fn(),
      prevPage: vi.fn(),
      firstPage: vi.fn(),
      lastPage: vi.fn(),
      goToPage: vi.fn(),
      pageSize: 10,
      setPageSize: vi.fn(),
    })
  })

  it('calls useVaR with the given portfolioId', () => {
    render(<RiskTab portfolioId="port-1" />)

    expect(mockUseVaR).toHaveBeenCalledWith('port-1')
  })

  it('renders VaR dashboard and job history', () => {
    render(<RiskTab portfolioId="port-1" />)

    expect(screen.getByTestId('var-empty')).toBeInTheDocument()
    expect(screen.getByTestId('job-history')).toBeInTheDocument()
  })
})
