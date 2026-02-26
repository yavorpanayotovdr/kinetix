import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../hooks/useRegulatory')

import { RegulatoryTab } from './RegulatoryTab'
import { useRegulatory } from '../hooks/useRegulatory'

const mockUseRegulatory = vi.mocked(useRegulatory)

describe('RegulatoryTab', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockUseRegulatory.mockReturnValue({
      result: null,
      loading: false,
      error: null,
      calculate: vi.fn(),
      downloadCsv: vi.fn(),
      downloadXbrl: vi.fn(),
    })
  })

  it('calls useRegulatory with the given portfolioId', () => {
    render(<RegulatoryTab portfolioId="port-1" />)

    expect(mockUseRegulatory).toHaveBeenCalledWith('port-1')
  })

  it('renders the regulatory dashboard', () => {
    render(<RegulatoryTab portfolioId="port-1" />)

    expect(screen.getByTestId('regulatory-dashboard')).toBeInTheDocument()
  })
})
