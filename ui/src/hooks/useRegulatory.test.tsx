import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FrtbResultDto } from '../types'

vi.mock('../api/regulatory')

import { fetchFrtb, generateReport } from '../api/regulatory'
import { useRegulatory } from './useRegulatory'

const mockFetchFrtb = vi.mocked(fetchFrtb)
const mockGenerateReport = vi.mocked(generateReport)

const frtbResult: FrtbResultDto = {
  portfolioId: 'port-1',
  sbmCharges: [
    {
      riskClass: 'GIRR',
      deltaCharge: '100000',
      vegaCharge: '50000',
      curvatureCharge: '25000',
      totalCharge: '175000',
    },
  ],
  totalSbmCharge: '175000',
  grossJtd: '80000',
  hedgeBenefit: '20000',
  netDrc: '60000',
  exoticNotional: '10000',
  otherNotional: '5000',
  totalRrao: '15000',
  totalCapitalCharge: '250000',
  calculatedAt: '2025-01-15T10:30:00Z',
}

describe('useRegulatory', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('starts with no result', () => {
    const { result } = renderHook(() => useRegulatory('port-1'))

    expect(result.current.result).toBeNull()
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('calculates FRTB and returns result', async () => {
    mockFetchFrtb.mockResolvedValue(frtbResult)

    const { result } = renderHook(() => useRegulatory('port-1'))

    await act(async () => {
      result.current.calculate()
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.result).toEqual(frtbResult)
    expect(mockFetchFrtb).toHaveBeenCalledWith('port-1')
  })

  it('sets error when calculate fails', async () => {
    mockFetchFrtb.mockRejectedValue(new Error('FRTB failed'))

    const { result } = renderHook(() => useRegulatory('port-1'))

    await act(async () => {
      result.current.calculate()
    })

    await waitFor(() => {
      expect(result.current.loading).toBe(false)
    })

    expect(result.current.error).toBe('FRTB failed')
  })

  it('downloads CSV report', async () => {
    mockGenerateReport.mockResolvedValue({
      portfolioId: 'port-1',
      format: 'CSV',
      content: 'col1,col2\nval1,val2',
      generatedAt: '2025-01-15T10:30:00Z',
    })

    const { result } = renderHook(() => useRegulatory('port-1'))

    const mockClick = vi.fn()
    const originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      if (tag === 'a') {
        return { href: '', download: '', click: mockClick } as unknown as HTMLAnchorElement
      }
      return originalCreateElement(tag)
    })
    globalThis.URL.createObjectURL = vi.fn().mockReturnValue('blob:test')
    globalThis.URL.revokeObjectURL = vi.fn()

    await act(async () => {
      result.current.downloadCsv()
    })

    expect(mockGenerateReport).toHaveBeenCalledWith('port-1', 'CSV')
    expect(mockClick).toHaveBeenCalled()

    vi.mocked(document.createElement).mockRestore()
  })

  it('downloads XBRL report', async () => {
    mockGenerateReport.mockResolvedValue({
      portfolioId: 'port-1',
      format: 'XBRL',
      content: '<xbrl>test</xbrl>',
      generatedAt: '2025-01-15T10:30:00Z',
    })

    const { result } = renderHook(() => useRegulatory('port-1'))

    const mockClick = vi.fn()
    const originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      if (tag === 'a') {
        return { href: '', download: '', click: mockClick } as unknown as HTMLAnchorElement
      }
      return originalCreateElement(tag)
    })
    globalThis.URL.createObjectURL = vi.fn().mockReturnValue('blob:test')
    globalThis.URL.revokeObjectURL = vi.fn()

    await act(async () => {
      result.current.downloadXbrl()
    })

    expect(mockGenerateReport).toHaveBeenCalledWith('port-1', 'XBRL')
    expect(mockClick).toHaveBeenCalled()

    vi.mocked(document.createElement).mockRestore()
  })

  it('clears result when portfolioId changes', async () => {
    mockFetchFrtb.mockResolvedValue(frtbResult)

    const { result, rerender } = renderHook(
      ({ portfolioId }) => useRegulatory(portfolioId),
      { initialProps: { portfolioId: 'port-1' as string | null } },
    )

    await act(async () => {
      result.current.calculate()
    })

    await waitFor(() => {
      expect(result.current.result).toEqual(frtbResult)
    })

    rerender({ portfolioId: 'port-2' })

    expect(result.current.result).toBeNull()
  })
})
