import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ValuationDatePicker } from './ValuationDatePicker'

describe('ValuationDatePicker', () => {
  it('renders with Today selected by default when value is null', () => {
    render(<ValuationDatePicker value={null} onChange={vi.fn()} />)

    const todayBtn = screen.getByTestId('vdate-today')
    expect(todayBtn).toBeInTheDocument()
    expect(todayBtn.className).toContain('bg-primary-100')
  })

  it('emits null when Today is clicked', () => {
    const onChange = vi.fn()
    render(<ValuationDatePicker value="2025-03-10" onChange={onChange} />)

    fireEvent.click(screen.getByTestId('vdate-today'))

    expect(onChange).toHaveBeenCalledWith(null)
  })

  it('emits yesterday date when Yesterday is clicked', () => {
    const onChange = vi.fn()
    render(<ValuationDatePicker value={null} onChange={onChange} />)

    fireEvent.click(screen.getByTestId('vdate-yesterday'))

    const emittedDate = onChange.mock.calls[0][0]
    expect(emittedDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)

    // Verify it is yesterday's date
    const expected = new Date()
    expected.setUTCDate(expected.getUTCDate() - 1)
    expect(emittedDate).toBe(expected.toISOString().slice(0, 10))
  })

  it('emits T-2 date when T-2 is clicked', () => {
    const onChange = vi.fn()
    render(<ValuationDatePicker value={null} onChange={onChange} />)

    fireEvent.click(screen.getByTestId('vdate-t2'))

    const emittedDate = onChange.mock.calls[0][0]
    const expected = new Date()
    expected.setUTCDate(expected.getUTCDate() - 2)
    expect(emittedDate).toBe(expected.toISOString().slice(0, 10))
  })

  it('reveals date input when Pick date is clicked', () => {
    render(<ValuationDatePicker value={null} onChange={vi.fn()} />)

    expect(screen.queryByTestId('vdate-input')).not.toBeInTheDocument()

    fireEvent.click(screen.getByTestId('vdate-custom'))

    expect(screen.getByTestId('vdate-input')).toBeInTheDocument()
  })

  it('has max attribute on date input to prevent future dates', () => {
    render(<ValuationDatePicker value={null} onChange={vi.fn()} />)

    fireEvent.click(screen.getByTestId('vdate-custom'))

    const input = screen.getByTestId('vdate-input') as HTMLInputElement
    expect(input.max).toBe(new Date().toISOString().slice(0, 10))
  })

  it('has role="group" with aria-label for accessibility', () => {
    render(<ValuationDatePicker value={null} onChange={vi.fn()} />)

    const group = screen.getByRole('group', { name: 'Valuation date' })
    expect(group).toBeInTheDocument()
  })

  it('highlights yesterday button when value matches yesterday', () => {
    const yesterday = new Date()
    yesterday.setUTCDate(yesterday.getUTCDate() - 1)
    const yesterdayStr = yesterday.toISOString().slice(0, 10)

    render(<ValuationDatePicker value={yesterdayStr} onChange={vi.fn()} />)

    expect(screen.getByTestId('vdate-yesterday').className).toContain('bg-primary-100')
    expect(screen.getByTestId('vdate-today').className).not.toContain('bg-primary-100')
  })
})
