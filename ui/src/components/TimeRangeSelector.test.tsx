import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { TimeRangeSelector } from './TimeRangeSelector'
import type { TimeRange } from '../types'

const defaultRange: TimeRange = {
  from: '2025-01-15T00:00:00.000Z',
  to: '2025-01-15T23:59:59.000Z',
  label: 'Last 24h',
}

describe('TimeRangeSelector', () => {
  it('renders all preset buttons', () => {
    render(<TimeRangeSelector value={defaultRange} onChange={() => {}} />)

    expect(screen.getByTestId('time-preset-Last 1h')).toBeInTheDocument()
    expect(screen.getByTestId('time-preset-Last 24h')).toBeInTheDocument()
    expect(screen.getByTestId('time-preset-Last 7d')).toBeInTheDocument()
    expect(screen.getByTestId('time-preset-Today')).toBeInTheDocument()
    expect(screen.getByTestId('time-preset-Custom')).toBeInTheDocument()
  })

  it('displays the current range label', () => {
    render(<TimeRangeSelector value={defaultRange} onChange={() => {}} />)

    expect(screen.getByTestId('time-range-label')).toHaveTextContent('Last 24h')
  })

  it('displays from/to times when label is Custom', () => {
    const customRange: TimeRange = {
      from: '2025-01-15T09:30:00.000Z',
      to: '2025-01-15T14:00:00.000Z',
      label: 'Custom',
    }
    render(<TimeRangeSelector value={customRange} onChange={() => {}} />)

    const label = screen.getByTestId('time-range-label').textContent!
    expect(label).toContain('Custom:')
    expect(label).toMatch(/\d{2}:\d{2}:\d{2}/)
  })

  it('calls onChange with computed range when a preset is clicked', () => {
    const onChange = vi.fn()
    render(<TimeRangeSelector value={defaultRange} onChange={onChange} />)

    fireEvent.click(screen.getByTestId('time-preset-Last 1h'))

    expect(onChange).toHaveBeenCalledTimes(1)
    const arg = onChange.mock.calls[0][0] as TimeRange
    expect(arg.label).toBe('Last 1h')
    expect(arg.from).toBeTruthy()
    expect(arg.to).toBeTruthy()
  })

  it('shows custom inputs when Custom button is clicked', () => {
    render(<TimeRangeSelector value={defaultRange} onChange={() => {}} />)

    expect(screen.queryByTestId('custom-range-inputs')).not.toBeInTheDocument()

    fireEvent.click(screen.getByTestId('time-preset-Custom'))

    expect(screen.getByTestId('custom-range-inputs')).toBeInTheDocument()
    expect(screen.getByTestId('custom-from')).toBeInTheDocument()
    expect(screen.getByTestId('custom-to')).toBeInTheDocument()
  })

  it('hides custom inputs when a preset is selected after Custom', () => {
    render(<TimeRangeSelector value={defaultRange} onChange={() => {}} />)

    fireEvent.click(screen.getByTestId('time-preset-Custom'))
    expect(screen.getByTestId('custom-range-inputs')).toBeInTheDocument()

    fireEvent.click(screen.getByTestId('time-preset-Last 7d'))
    expect(screen.queryByTestId('custom-range-inputs')).not.toBeInTheDocument()
  })

  it('does not call onChange until Apply is clicked', () => {
    const onChange = vi.fn()
    render(<TimeRangeSelector value={defaultRange} onChange={onChange} />)

    fireEvent.click(screen.getByTestId('time-preset-Custom'))
    fireEvent.change(screen.getByTestId('custom-from'), { target: { value: '2025-01-14T08:00' } })

    expect(onChange).not.toHaveBeenCalled()
  })

  it('calls onChange with Custom label and correct ISO from/to when Apply is clicked', () => {
    const onChange = vi.fn()
    render(<TimeRangeSelector value={defaultRange} onChange={onChange} />)

    fireEvent.click(screen.getByTestId('time-preset-Custom'))
    fireEvent.change(screen.getByTestId('custom-from'), { target: { value: '2025-01-14T08:00' } })
    fireEvent.change(screen.getByTestId('custom-to'), { target: { value: '2025-01-15T18:00' } })
    fireEvent.click(screen.getByTestId('custom-apply'))

    expect(onChange).toHaveBeenCalledTimes(1)
    const arg = onChange.mock.calls[0][0] as TimeRange
    expect(arg.label).toBe('Custom')

    // Verify from/to are valid ISO-8601 strings matching the entered datetime-local values
    const expectedFrom = new Date('2025-01-14T08:00').toISOString()
    const expectedTo = new Date('2025-01-15T18:00').toISOString()
    expect(arg.from).toBe(expectedFrom)
    expect(arg.to).toBe(expectedTo)
  })

  it('produces from/to that end with Z suffix (UTC) when Apply is clicked', () => {
    const onChange = vi.fn()
    render(<TimeRangeSelector value={defaultRange} onChange={onChange} />)

    fireEvent.click(screen.getByTestId('time-preset-Custom'))
    fireEvent.change(screen.getByTestId('custom-from'), { target: { value: '2025-01-15T10:00' } })
    fireEvent.change(screen.getByTestId('custom-to'), { target: { value: '2025-01-15T10:00' } })
    fireEvent.click(screen.getByTestId('custom-apply'))

    expect(onChange).toHaveBeenCalledTimes(1)
    const arg = onChange.mock.calls[0][0] as TimeRange
    expect(arg.from).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}Z$/)
    expect(arg.to).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}Z$/)
  })
})
